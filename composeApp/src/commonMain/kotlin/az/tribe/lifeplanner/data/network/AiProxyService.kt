package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.domain.enum.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface AiProxyService {
    suspend fun generateText(
        prompt: String,
        systemPrompt: String? = null,
        provider: AiProvider? = null
    ): String

    suspend fun generateStructuredJson(
        prompt: String,
        responseSchema: JsonObject,
        systemPrompt: String? = null,
        provider: AiProvider? = null
    ): String

    suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        responseSchema: JsonObject? = null,
        provider: AiProvider? = null
    ): String

    fun chatStream(
        messages: List<ChatMessage>,
        systemPrompt: String? = null,
        provider: AiProvider? = null
    ): Flow<StreamEvent>

    data class ChatMessage(
        val role: String,
        val content: String
    )

    sealed class StreamEvent {
        data class TextChunk(val text: String) : StreamEvent()
        data class Done(val fullText: String) : StreamEvent()
        data class Error(val message: String) : StreamEvent()
    }
}
