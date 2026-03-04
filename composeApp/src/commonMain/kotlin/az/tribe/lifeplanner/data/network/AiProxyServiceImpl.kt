package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.domain.enum.AiProvider
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AiProxyServiceImpl(
    private val supabase: SupabaseClient,
    private val settings: Settings,
    private val httpClient: HttpClient
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

    /**
     * Returns the user's access token if authenticated,
     * or falls back to the anon key for guest users.
     */
    private suspend fun getAuthToken(): String {
        return supabase.auth.currentSessionOrNull()?.accessToken
            ?: BuildKonfig.SUPABASE_ANON_KEY
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
}
