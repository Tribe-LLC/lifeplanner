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
                emit(AiProxyService.StreamEvent.Error("AI proxy error ${response.status.value}: $errorBody"))
                return@execute
            }

            // Verify response is SSE
            val contentType = response.headers["Content-Type"] ?: ""
            if (!contentType.contains("text/event-stream")) {
                // Server returned non-SSE (e.g. JSON) — streaming not supported
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
            var currentEvent = ""
            val accumulated = StringBuilder()
            var receivedAnyEvent = false
            var doneEmitted = false

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break

                if (line.startsWith("event: ")) {
                    currentEvent = line.removePrefix("event: ").trim()
                } else if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ")
                    receivedAnyEvent = true

                    when (currentEvent) {
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
                // Empty lines (SSE separators) are just skipped
            }

            // If we got text chunks but no done event, emit done with accumulated text
            if (!receivedAnyEvent && accumulated.isEmpty()) {
                emit(AiProxyService.StreamEvent.Error("No streaming data received"))
            } else if (accumulated.isNotEmpty() && !doneEmitted) {
                // Stream ended without explicit done event
                emit(AiProxyService.StreamEvent.Done(accumulated.toString()))
            }
        }
    }
}
