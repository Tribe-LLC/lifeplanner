package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.domain.enum.AiProvider
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiProxyServiceStreamingTest {

    private fun createService(
        mockEngine: MockEngine,
        token: String = "test-jwt-token"
    ): AiProxyServiceImpl {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val settings = MapSettings().apply {
            putString("ai_provider", AiProvider.GEMINI.name)
        }
        val tokenProvider = AuthTokenProvider { token }
        return AiProxyServiceImpl(settings, httpClient, tokenProvider)
    }

    private val testMessages = listOf(
        AiProxyService.ChatMessage(role = "user", content = "Hello")
    )

    @Test
    fun streamingChatEmitsTextChunksAndDone() = runTest {
        val sseContent = buildString {
            appendLine("event: text")
            appendLine("data: Hello")
            appendLine()
            appendLine("event: text")
            appendLine("data:  there")
            appendLine()
            appendLine("event: text")
            appendLine("data: !")
            appendLine()
            appendLine("event: done")
            appendLine("data: {}")
            appendLine()
        }

        val mockEngine = MockEngine { request ->
            // Verify auth header is set correctly
            assertEquals("Bearer test-jwt-token", request.headers["Authorization"])
            respond(
                content = sseContent,
                headers = headersOf("Content-Type", "text/event-stream")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages, "test system prompt").collect { events.add(it) }

        assertEquals(4, events.size, "Expected 3 TextChunks + 1 Done, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.TextChunk)
        assertEquals("Hello", (events[0] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[1] is AiProxyService.StreamEvent.TextChunk)
        assertEquals(" there", (events[1] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[2] is AiProxyService.StreamEvent.TextChunk)
        assertEquals("!", (events[2] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[3] is AiProxyService.StreamEvent.Done)
        assertEquals("Hello there!", (events[3] as AiProxyService.StreamEvent.Done).fullText)
    }

    @Test
    fun streamingChatHandlesErrorEvent() = runTest {
        val sseContent = buildString {
            appendLine("event: error")
            appendLine("""data: {"error":"Rate limit exceeded"}""")
            appendLine()
        }

        val mockEngine = MockEngine {
            respond(
                content = sseContent,
                headers = headersOf("Content-Type", "text/event-stream")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages).collect { events.add(it) }

        assertEquals(1, events.size, "Expected 1 Error event, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.Error)
        assertEquals("Rate limit exceeded", (events[0] as AiProxyService.StreamEvent.Error).message)
    }

    @Test
    fun nonSseResponseFallsBackToJson() = runTest {
        val jsonBody = """{"text":"Hello from JSON fallback"}"""

        val mockEngine = MockEngine {
            respond(
                content = jsonBody,
                headers = headersOf("Content-Type", "application/json")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages).collect { events.add(it) }

        assertEquals(2, events.size, "Expected TextChunk + Done, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.TextChunk)
        assertEquals("Hello from JSON fallback", (events[0] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[1] is AiProxyService.StreamEvent.Done)
        assertEquals("Hello from JSON fallback", (events[1] as AiProxyService.StreamEvent.Done).fullText)
    }

    @Test
    fun http401EmitsError() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Unauthorized: invalid JWT",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", "text/plain")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages).collect { events.add(it) }

        assertEquals(1, events.size, "Expected 1 Error event, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.Error)
        val errorMsg = (events[0] as AiProxyService.StreamEvent.Error).message
        assertTrue(errorMsg.contains("401"), "Error should mention 401: $errorMsg")
        assertTrue(errorMsg.contains("Unauthorized"), "Error should contain body: $errorMsg")
    }

    @Test
    fun emptyStreamEmitsError() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                headers = headersOf("Content-Type", "text/event-stream")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages).collect { events.add(it) }

        assertEquals(1, events.size, "Expected 1 Error event, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.Error)
        assertEquals(
            "No streaming data received",
            (events[0] as AiProxyService.StreamEvent.Error).message
        )
    }

    @Test
    fun streamWithoutDoneEventStillCompletes() = runTest {
        val sseContent = buildString {
            appendLine("event: text")
            appendLine("data: Hello")
            appendLine()
            appendLine("event: text")
            appendLine("data: World")
            appendLine()
        }

        val mockEngine = MockEngine {
            respond(
                content = sseContent,
                headers = headersOf("Content-Type", "text/event-stream")
            )
        }

        val service = createService(mockEngine)
        val events = mutableListOf<AiProxyService.StreamEvent>()

        service.chatStream(testMessages).collect { events.add(it) }

        assertEquals(3, events.size, "Expected 2 TextChunks + 1 fallback Done, got: $events")
        assertTrue(events[0] is AiProxyService.StreamEvent.TextChunk)
        assertEquals("Hello", (events[0] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[1] is AiProxyService.StreamEvent.TextChunk)
        assertEquals("World", (events[1] as AiProxyService.StreamEvent.TextChunk).text)
        assertTrue(events[2] is AiProxyService.StreamEvent.Done)
        assertEquals("HelloWorld", (events[2] as AiProxyService.StreamEvent.Done).fullText)
    }
}
