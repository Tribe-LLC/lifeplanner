package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.domain.enum.AiProvider
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AiProxyServiceImpl(
    private val settings: Settings,
    private val httpClient: HttpClient,
    private val tokenProvider: AuthTokenProvider
) : AiProxyService {

    companion object {
        private const val SETTINGS_KEY_PROVIDER = "ai_provider"
    }

    private val log = Logger.withTag("AiProxyService")

    private fun getSelectedProvider(): AiProvider {
        val name = settings.getStringOrNull(SETTINGS_KEY_PROVIDER)
        return name?.let {
            try { AiProvider.valueOf(it) } catch (_: Exception) { AiProvider.GEMINI }
        } ?: AiProvider.GEMINI
    }

    private fun getEdgeFunctionUrl(): String {
        return "${BuildKonfig.SUPABASE_URL}/functions/v1/ai-proxy"
    }

    private suspend fun getAuthToken(): String {
        return tokenProvider.getToken()
    }

    private suspend fun callEdgeFunction(requestBody: JsonObject): String {
        val token = getAuthToken()
        log.d { "Calling edge function with provider: ${requestBody["provider"]}" }

        val response = httpClient.post(getEdgeFunctionUrl()) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            header("apikey", BuildKonfig.SUPABASE_ANON_KEY)
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.e { "Edge function error ${response.status}: $errorBody" }
            // 401 means no valid user session, not a network problem. Typed so the UI can say
            // "sign in" instead of sending the user off to check their connection.
            if (response.status.value == 401) {
                throw AiAuthRequiredException("AI proxy auth error 401: $errorBody")
            }
            // The edge function returns {"error": "<already-sanitized message>"} on 5xx. Surface
            // it verbatim so the user sees "rate-limited" / "authentication failed" / "timed out"
            // instead of a generic connection error, without leaking quota or key details.
            val serverMessage = runCatching {
                Json.parseToJsonElement(errorBody).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (serverMessage != null) {
                throw AiProviderException(serverMessage)
            }
            throw IllegalStateException("AI proxy error ${response.status.value}: $errorBody")
        }

        val responseBody: JsonObject = response.body()
        return responseBody["text"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No text in AI response")
    }

    override suspend fun generateText(
        prompt: String,
        systemPrompt: String?,
        provider: AiProvider?
    ): String {
        val effectiveProvider = provider ?: getSelectedProvider()
        val requestBody = buildJsonObject {
            put("prompt", prompt)
            systemPrompt?.let { put("systemPrompt", it) }
            put("provider", effectiveProvider.name)
        }
        return callEdgeFunction(requestBody)
    }

    override suspend fun generateStructuredJson(
        prompt: String,
        responseSchema: JsonObject,
        systemPrompt: String?,
        provider: AiProvider?
    ): String {
        val effectiveProvider = provider ?: getSelectedProvider()
        val requestBody = buildJsonObject {
            put("prompt", prompt)
            systemPrompt?.let { put("systemPrompt", it) }
            put("responseSchema", responseSchema)
            put("provider", effectiveProvider.name)
        }
        return callEdgeFunction(requestBody)
    }

    override suspend fun chat(
        messages: List<AiProxyService.ChatMessage>,
        systemPrompt: String?,
        responseSchema: JsonObject?,
        provider: AiProvider?
    ): String {
        val effectiveProvider = provider ?: getSelectedProvider()
        val messagesArray = buildJsonArray {
            for (msg in messages) {
                add(buildJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }
        val requestBody = buildJsonObject {
            put("messages", messagesArray)
            systemPrompt?.let { put("systemPrompt", it) }
            responseSchema?.let { put("responseSchema", it) }
            put("provider", effectiveProvider.name)
            put("enrichContext", true)
        }
        return callEdgeFunction(requestBody)
    }

    override fun chatStream(
        messages: List<AiProxyService.ChatMessage>,
        systemPrompt: String?,
        provider: AiProvider?
    ): Flow<AiProxyService.StreamEvent> = flow {
        val effectiveProvider = provider ?: getSelectedProvider()
        val token = getAuthToken()

        val messagesArray = buildJsonArray {
            for (msg in messages) {
                add(buildJsonObject {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }
        val requestBody = buildJsonObject {
            put("messages", messagesArray)
            systemPrompt?.let { put("systemPrompt", it) }
            put("provider", effectiveProvider.name)
            put("enrichContext", true)
            put("stream", true)
        }

        log.d { "Starting streaming chat with provider: $effectiveProvider" }

        httpClient.preparePost(getEdgeFunctionUrl()) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            header("apikey", BuildKonfig.SUPABASE_ANON_KEY)
            setBody(requestBody)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                log.e { "Stream error ${response.status}: $errorBody" }
                // The stream path reports errors as text rather than throwing, so surface the
                // user-facing auth wording here directly.
                val message =
                    if (response.status.value == 401) {
                        AiAuthRequiredException(errorBody).toUserFacingAiMessage("stream a reply")
                    } else {
                        "AI proxy error ${response.status.value}: $errorBody"
                    }
                emit(AiProxyService.StreamEvent.Error(message))
                return@execute
            }

            // Verify response is SSE
            val contentType = response.headers["Content-Type"] ?: ""
            if (!contentType.contains("text/event-stream")) {
                // Server returned non-SSE (e.g. JSON), streaming not supported
                log.w { "Expected text/event-stream but got: $contentType" }
                val body = response.bodyAsText()
                // Try to extract text from JSON response as fallback
                try {
                    val json = Json.parseToJsonElement(body).jsonObject
                    val text = json["text"]?.jsonPrimitive?.content
                    if (text != null) {
                        emit(AiProxyService.StreamEvent.TextChunk(text))
                        emit(AiProxyService.StreamEvent.Done(text))
                        return@execute
                    }
                } catch (_: Exception) { /* not JSON */ }
                emit(AiProxyService.StreamEvent.Error("Streaming not available"))
                return@execute
            }

            val channel = response.bodyAsChannel()
            val reader = ProxySseReader()
            val accumulated = StringBuilder()
            var receivedAnyEvent = false
            var doneEmitted = false

            suspend fun handle(piece: Pair<String, String>?) {
                val (event, data) = piece ?: return
                receivedAnyEvent = true
                when (event) {
                    "text" -> {
                        accumulated.append(data)
                        emit(AiProxyService.StreamEvent.TextChunk(data))
                    }
                    "done" -> {
                        doneEmitted = true
                        emit(AiProxyService.StreamEvent.Done(accumulated.toString()))
                    }
                    "error" -> {
                        try {
                            val errorJson = Json.parseToJsonElement(data).jsonObject
                            val errorMsg = errorJson["error"]?.jsonPrimitive?.content ?: "Unknown stream error"
                            emit(AiProxyService.StreamEvent.Error(errorMsg))
                        } catch (_: Exception) {
                            emit(AiProxyService.StreamEvent.Error(data))
                        }
                    }
                }
            }

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                handle(reader.push(line))
            }
            handle(reader.finish())

            // If we got text chunks but no done event, emit done with accumulated text
            if (!receivedAnyEvent && accumulated.isEmpty()) {
                log.w { "Stream ended with no events received" }
                emit(AiProxyService.StreamEvent.Error("No streaming data received"))
            } else if (accumulated.isNotEmpty() && !doneEmitted) {
                log.w { "Stream ended without explicit done event (${accumulated.length} chars accumulated)" }
                emit(AiProxyService.StreamEvent.Done(accumulated.toString()))
            }
        }
    }
}
