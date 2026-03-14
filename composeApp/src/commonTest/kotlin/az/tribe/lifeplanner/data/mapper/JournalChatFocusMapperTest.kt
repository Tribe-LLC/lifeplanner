package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.ChatMessageEntity
import az.tribe.lifeplanner.database.ChatSessionEntity
import az.tribe.lifeplanner.database.FocusSessionEntity
import az.tribe.lifeplanner.database.JournalEntryEntity
import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.ChatMessageMetadata
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.testutil.testChatMessage
import az.tribe.lifeplanner.testutil.testChatSession
import az.tribe.lifeplanner.testutil.testFocusSession
import az.tribe.lifeplanner.testutil.testJournalEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class JournalChatFocusMapperTest {

    // ════════════════════════════════════════════════════════════════
    // JournalMapper
    // ════════════════════════════════════════════════════════════════

    // ── JournalEntryEntity.toDomain ─────────────────────────────────

    @Test
    fun `JournalEntryEntity toDomain maps all fields`() {
        val entity = JournalEntryEntity(
            id = "j-1",
            title = "Great Day",
            content = "Today was awesome",
            mood = "HAPPY",
            linkedGoalId = "goal-1",
            linkedHabitId = "habit-1",
            promptUsed = "What was the highlight?",
            tags = "work,health",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = "2026-03-06T12:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val entry = entity.toDomain()

        assertEquals("j-1", entry.id)
        assertEquals("Great Day", entry.title)
        assertEquals("Today was awesome", entry.content)
        assertEquals(Mood.HAPPY, entry.mood)
        assertEquals("goal-1", entry.linkedGoalId)
        assertEquals("habit-1", entry.linkedHabitId)
        assertEquals("What was the highlight?", entry.promptUsed)
        assertEquals(listOf("work", "health"), entry.tags)
        assertEquals(LocalDate(2026, 3, 6), entry.date)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), entry.createdAt)
        assertEquals(LocalDateTime(2026, 3, 6, 12, 0, 0), entry.updatedAt)
    }

    @Test
    fun `JournalEntryEntity toDomain maps null updatedAt`() {
        val entity = JournalEntryEntity(
            id = "j-2",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertNull(entity.toDomain().updatedAt)
    }

    @Test
    fun `JournalEntryEntity toDomain maps null optional fields`() {
        val entity = JournalEntryEntity(
            id = "j-3",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val entry = entity.toDomain()
        assertNull(entry.linkedGoalId)
        assertNull(entry.linkedHabitId)
        assertNull(entry.promptUsed)
    }

    @Test
    fun `JournalEntryEntity toDomain falls back to NEUTRAL for unknown mood`() {
        val entity = JournalEntryEntity(
            id = "j-unknown-mood",
            title = "T",
            content = "C",
            mood = "UNKNOWN_MOOD",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(Mood.NEUTRAL, entity.toDomain().mood)
    }

    @Test
    fun `JournalEntryEntity toDomain maps all Mood values`() {
        for (mood in Mood.entries) {
            val entity = JournalEntryEntity(
                id = "j-${mood.name}",
                title = "T",
                content = "C",
                mood = mood.name,
                linkedGoalId = null,
                linkedHabitId = null,
                promptUsed = null,
                tags = "",
                date = "2026-03-06",
                createdAt = "2026-03-06T10:00:00",
                updatedAt = null,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(mood, entity.toDomain().mood, "Failed for mood $mood")
        }
    }

    // ── parseTags tests ─────────────────────────────────────────────

    @Test
    fun `parseTags handles comma-separated tags`() {
        val entity = JournalEntryEntity(
            id = "j-csv-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "work,health,focus",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(listOf("work", "health", "focus"), entity.toDomain().tags)
    }

    @Test
    fun `parseTags handles JSON array tags`() {
        val entity = JournalEntryEntity(
            id = "j-json-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = """["work","health","focus"]""",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(listOf("work", "health", "focus"), entity.toDomain().tags)
    }

    @Test
    fun `parseTags handles empty string`() {
        val entity = JournalEntryEntity(
            id = "j-empty-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertTrue(entity.toDomain().tags.isEmpty())
    }

    @Test
    fun `parseTags handles blank string`() {
        val entity = JournalEntryEntity(
            id = "j-blank-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "   ",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertTrue(entity.toDomain().tags.isEmpty())
    }

    @Test
    fun `parseTags trims whitespace in comma-separated tags`() {
        val entity = JournalEntryEntity(
            id = "j-spaced-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = " work , health , focus ",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(listOf("work", "health", "focus"), entity.toDomain().tags)
    }

    @Test
    fun `parseTags filters blank entries from comma-separated`() {
        val entity = JournalEntryEntity(
            id = "j-filter-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "work,,health, ,focus",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(listOf("work", "health", "focus"), entity.toDomain().tags)
    }

    @Test
    fun `parseTags filters blank entries from JSON array`() {
        val entity = JournalEntryEntity(
            id = "j-filter-json-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = """["work","","health"]""",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(listOf("work", "health"), entity.toDomain().tags)
    }

    @Test
    fun `parseTags falls back to comma split for malformed JSON starting with bracket`() {
        val entity = JournalEntryEntity(
            id = "j-bad-json-tags",
            title = "T",
            content = "C",
            mood = "NEUTRAL",
            linkedGoalId = null,
            linkedHabitId = null,
            promptUsed = null,
            tags = "[broken json",
            date = "2026-03-06",
            createdAt = "2026-03-06T10:00:00",
            updatedAt = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        // Falls through to comma split, resulting in ["[broken json"]
        val tags = entity.toDomain().tags
        assertTrue(tags.isNotEmpty())
    }

    // ── JournalEntry.toEntity ───────────────────────────────────────

    @Test
    fun `JournalEntry toEntity maps all fields`() {
        val entry = testJournalEntry(
            id = "j-1",
            title = "My Day",
            content = "Content here",
            mood = Mood.VERY_HAPPY,
            linkedGoalId = "g-1",
            linkedHabitId = "h-1",
            promptUsed = "Prompt",
            tags = listOf("work", "health"),
            date = LocalDate(2026, 3, 6),
            createdAt = LocalDateTime(2026, 3, 6, 10, 0, 0),
            updatedAt = LocalDateTime(2026, 3, 6, 12, 0, 0)
        )

        val entity = entry.toEntity()

        assertEquals("j-1", entity.id)
        assertEquals("My Day", entity.title)
        assertEquals("Content here", entity.content)
        assertEquals("VERY_HAPPY", entity.mood)
        assertEquals("g-1", entity.linkedGoalId)
        assertEquals("h-1", entity.linkedHabitId)
        assertEquals("Prompt", entity.promptUsed)
        assertEquals("work,health", entity.tags)
        assertEquals("2026-03-06", entity.date)
    }

    @Test
    fun `JournalEntry toEntity maps empty tags to empty string`() {
        val entry = testJournalEntry(tags = emptyList())
        assertEquals("", entry.toEntity().tags)
    }

    @Test
    fun `JournalEntry toEntity maps null updatedAt`() {
        val entry = testJournalEntry(updatedAt = null)
        assertNull(entry.toEntity().updatedAt)
    }

    // ── JournalEntry round trip ─────────────────────────────────────

    @Test
    fun `JournalEntry round trip preserves id and title`() {
        val original = testJournalEntry(id = "rt-j", title = "Round trip test")
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
    }

    @Test
    fun `JournalEntry round trip preserves mood`() {
        for (mood in Mood.entries) {
            val original = testJournalEntry(id = "rt-mood-${mood.name}", mood = mood)
            val restored = original.toEntity().toDomain()
            assertEquals(original.mood, restored.mood, "Failed for mood $mood")
        }
    }

    @Test
    fun `JournalEntry round trip preserves date`() {
        val original = testJournalEntry(date = LocalDate(2026, 12, 25))
        val restored = original.toEntity().toDomain()
        assertEquals(original.date, restored.date)
    }

    @Test
    fun `JournalEntry round trip preserves tags via comma format`() {
        val original = testJournalEntry(tags = listOf("alpha", "beta", "gamma"))
        val restored = original.toEntity().toDomain()
        assertEquals(original.tags, restored.tags)
    }

    // ── createNewJournalEntry ───────────────────────────────────────

    @Test
    fun `createNewJournalEntry sets defaults`() {
        val entry = createNewJournalEntry(
            title = "New Entry",
            content = "Content",
            mood = Mood.HAPPY
        )

        assertEquals("New Entry", entry.title)
        assertEquals("Content", entry.content)
        assertEquals(Mood.HAPPY, entry.mood)
        assertNull(entry.linkedGoalId)
        assertNull(entry.linkedHabitId)
        assertNull(entry.promptUsed)
        assertTrue(entry.tags.isEmpty())
        assertNull(entry.updatedAt)
    }

    @Test
    fun `createNewJournalEntry generates unique ids`() {
        val e1 = createNewJournalEntry(title = "A", content = "C", mood = Mood.HAPPY)
        val e2 = createNewJournalEntry(title = "B", content = "C", mood = Mood.HAPPY)
        assertNotEquals(e1.id, e2.id)
    }

    @Test
    fun `createNewJournalEntry uses provided optional parameters`() {
        val entry = createNewJournalEntry(
            title = "T",
            content = "C",
            mood = Mood.SAD,
            linkedGoalId = "g-1",
            linkedHabitId = "h-1",
            promptUsed = "Prompt",
            tags = listOf("tag1")
        )

        assertEquals("g-1", entry.linkedGoalId)
        assertEquals("h-1", entry.linkedHabitId)
        assertEquals("Prompt", entry.promptUsed)
        assertEquals(listOf("tag1"), entry.tags)
    }

    // ── List mapper ─────────────────────────────────────────────────

    @Test
    fun `toDomainJournalEntries maps empty list`() {
        assertTrue(emptyList<JournalEntryEntity>().toDomainJournalEntries().isEmpty())
    }

    @Test
    fun `toDomainJournalEntries maps multiple entities`() {
        val entities = listOf(
            JournalEntryEntity(
                id = "j1", title = "A", content = "C", mood = "HAPPY",
                linkedGoalId = null, linkedHabitId = null, promptUsed = null,
                tags = "", date = "2026-03-06", createdAt = "2026-03-06T10:00:00",
                updatedAt = null, sync_updated_at = null, is_deleted = 0L,
                sync_version = 0L, last_synced_at = null
            ),
            JournalEntryEntity(
                id = "j2", title = "B", content = "C", mood = "SAD",
                linkedGoalId = null, linkedHabitId = null, promptUsed = null,
                tags = "tag1", date = "2026-03-07", createdAt = "2026-03-07T10:00:00",
                updatedAt = null, sync_updated_at = null, is_deleted = 0L,
                sync_version = 0L, last_synced_at = null
            )
        )

        val result = entities.toDomainJournalEntries()
        assertEquals(2, result.size)
        assertEquals("j1", result[0].id)
        assertEquals("j2", result[1].id)
    }

    // ════════════════════════════════════════════════════════════════
    // ChatMapper
    // ════════════════════════════════════════════════════════════════

    // ── ChatMessageEntity.toDomain ──────────────────────────────────

    @Test
    fun `ChatMessageEntity toDomain maps all fields`() {
        val entity = ChatMessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            content = "Hello!",
            role = "USER",
            timestamp = "2026-03-06T10:00:00",
            relatedGoalId = "goal-1",
            metadata = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val msg = entity.toDomain()

        assertEquals("msg-1", msg.id)
        assertEquals("Hello!", msg.content)
        assertEquals(MessageRole.USER, msg.role)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), msg.timestamp)
        assertEquals("goal-1", msg.relatedGoalId)
        assertNull(msg.metadata)
    }

    @Test
    fun `ChatMessageEntity toDomain maps ASSISTANT role`() {
        val entity = ChatMessageEntity(
            id = "msg-2", sessionId = "s", content = "Hi",
            role = "ASSISTANT", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertEquals(MessageRole.ASSISTANT, entity.toDomain().role)
    }

    @Test
    fun `ChatMessageEntity toDomain maps SYSTEM role`() {
        val entity = ChatMessageEntity(
            id = "msg-3", sessionId = "s", content = "System msg",
            role = "SYSTEM", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertEquals(MessageRole.SYSTEM, entity.toDomain().role)
    }

    @Test
    fun `ChatMessageEntity toDomain falls back to USER for unknown role`() {
        val entity = ChatMessageEntity(
            id = "msg-unknown", sessionId = "s", content = "C",
            role = "UNKNOWN_ROLE", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertEquals(MessageRole.USER, entity.toDomain().role)
    }

    @Test
    fun `ChatMessageEntity toDomain handles lowercase role`() {
        val entity = ChatMessageEntity(
            id = "msg-lower", sessionId = "s", content = "C",
            role = "assistant", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertEquals(MessageRole.ASSISTANT, entity.toDomain().role)
    }

    @Test
    fun `ChatMessageEntity toDomain parses metadata JSON`() {
        val metadataJson = """{"suggestedActions":[],"coachSuggestions":[],"referencedGoals":["goal-1"],"mood":"happy","isMotivational":true,"executedSuggestionIds":[]}"""
        val entity = ChatMessageEntity(
            id = "msg-meta", sessionId = "s", content = "C",
            role = "ASSISTANT", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = metadataJson,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )

        val msg = entity.toDomain()
        assertNotNull(msg.metadata)
        assertEquals(listOf("goal-1"), msg.metadata!!.referencedGoals)
        assertEquals("happy", msg.metadata!!.mood)
        assertTrue(msg.metadata!!.isMotivational)
    }

    @Test
    fun `ChatMessageEntity toDomain returns null metadata for invalid JSON`() {
        val entity = ChatMessageEntity(
            id = "msg-bad-meta", sessionId = "s", content = "C",
            role = "USER", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = "{broken",
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().metadata)
    }

    @Test
    fun `ChatMessageEntity toDomain returns null metadata for null metadata field`() {
        val entity = ChatMessageEntity(
            id = "msg-null-meta", sessionId = "s", content = "C",
            role = "USER", timestamp = "2026-03-06T10:00:00",
            relatedGoalId = null, metadata = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().metadata)
    }

    // ── ChatMessage.toEntity ────────────────────────────────────────

    @Test
    fun `ChatMessage toEntity maps all fields`() {
        val msg = testChatMessage(
            id = "msg-1",
            content = "Hello",
            role = MessageRole.USER,
            timestamp = LocalDateTime(2026, 3, 6, 10, 0, 0),
            relatedGoalId = "goal-1"
        )

        val entity = msg.toEntity("session-1")

        assertEquals("msg-1", entity.id)
        assertEquals("session-1", entity.sessionId)
        assertEquals("Hello", entity.content)
        assertEquals("USER", entity.role)
        assertEquals("goal-1", entity.relatedGoalId)
    }

    @Test
    fun `ChatMessage toEntity serializes metadata`() {
        val metadata = ChatMessageMetadata(
            referencedGoals = listOf("g-1"),
            mood = "happy",
            isMotivational = true
        )
        val msg = testChatMessage(metadata = metadata)
        val entity = msg.toEntity("s")

        assertNotNull(entity.metadata)
        assertTrue(entity.metadata!!.contains("happy"))
        assertTrue(entity.metadata!!.contains("g-1"))
    }

    @Test
    fun `ChatMessage toEntity maps null metadata to null`() {
        val msg = testChatMessage(metadata = null)
        assertNull(msg.toEntity("s").metadata)
    }

    @Test
    fun `ChatMessage toEntity sets sync fields`() {
        val entity = testChatMessage().toEntity("s")
        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
        assertEquals(0L, entity.sync_version)
        assertNull(entity.last_synced_at)
    }

    // ── ChatMessage round trip ──────────────────────────────────────

    @Test
    fun `ChatMessage round trip preserves id and content`() {
        val original = testChatMessage(id = "rt-msg", content = "Test content")
        val restored = original.toEntity("s").toDomain()
        assertEquals(original.id, restored.id)
        assertEquals(original.content, restored.content)
    }

    @Test
    fun `ChatMessage round trip preserves role for all MessageRole values`() {
        for (role in MessageRole.entries) {
            val original = testChatMessage(id = "rt-role-${role.name}", role = role)
            val restored = original.toEntity("s").toDomain()
            assertEquals(original.role, restored.role, "Failed for role $role")
        }
    }

    @Test
    fun `ChatMessage round trip preserves relatedGoalId`() {
        val original = testChatMessage(relatedGoalId = "g-42")
        val restored = original.toEntity("s").toDomain()
        assertEquals(original.relatedGoalId, restored.relatedGoalId)
    }

    @Test
    fun `ChatMessage round trip preserves null relatedGoalId`() {
        val original = testChatMessage(relatedGoalId = null)
        val restored = original.toEntity("s").toDomain()
        assertNull(restored.relatedGoalId)
    }

    @Test
    fun `ChatMessage round trip preserves metadata`() {
        val metadata = ChatMessageMetadata(
            referencedGoals = listOf("g-1", "g-2"),
            mood = "calm",
            isMotivational = false
        )
        val original = testChatMessage(metadata = metadata)
        val restored = original.toEntity("s").toDomain()

        assertNotNull(restored.metadata)
        assertEquals(original.metadata!!.referencedGoals, restored.metadata!!.referencedGoals)
        assertEquals(original.metadata!!.mood, restored.metadata!!.mood)
        assertEquals(original.metadata!!.isMotivational, restored.metadata!!.isMotivational)
    }

    // ── ChatSessionEntity.toDomain ──────────────────────────────────

    @Test
    fun `ChatSessionEntity toDomain maps all fields`() {
        val entity = ChatSessionEntity(
            id = "session-1",
            title = "My Chat",
            createdAt = "2026-03-06T10:00:00",
            lastMessageAt = "2026-03-06T12:00:00",
            summary = "A summary",
            coachId = "luna_general",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val session = entity.toDomain()

        assertEquals("session-1", session.id)
        assertEquals("My Chat", session.title)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), session.createdAt)
        assertEquals(LocalDateTime(2026, 3, 6, 12, 0, 0), session.lastMessageAt)
        assertEquals("A summary", session.summary)
        assertEquals("luna_general", session.coachId)
        assertTrue(session.messages.isEmpty())
    }

    @Test
    fun `ChatSessionEntity toDomain includes provided messages`() {
        val entity = ChatSessionEntity(
            id = "session-2",
            title = "Chat",
            createdAt = "2026-03-06T10:00:00",
            lastMessageAt = "2026-03-06T10:00:00",
            summary = null,
            coachId = "luna_general",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val messages = listOf(testChatMessage(id = "m1"), testChatMessage(id = "m2"))
        val session = entity.toDomain(messages)

        assertEquals(2, session.messages.size)
    }

    @Test
    fun `ChatSessionEntity toDomain maps null summary`() {
        val entity = ChatSessionEntity(
            id = "session-3",
            title = "Chat",
            createdAt = "2026-03-06T10:00:00",
            lastMessageAt = "2026-03-06T10:00:00",
            summary = null,
            coachId = "luna_general",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )
        assertNull(entity.toDomain().summary)
    }

    // ── Chat list mappers ───────────────────────────────────────────

    @Test
    fun `toDomainMessages maps empty list`() {
        assertTrue(emptyList<ChatMessageEntity>().toDomainMessages().isEmpty())
    }

    @Test
    fun `toDomainMessages maps multiple entities`() {
        val entities = listOf(
            ChatMessageEntity("m1", "s", "Hello", "USER", "2026-03-06T10:00:00", null, null, null, 0L, 0L, null),
            ChatMessageEntity("m2", "s", "Hi", "ASSISTANT", "2026-03-06T10:01:00", null, null, null, 0L, 0L, null)
        )
        val result = entities.toDomainMessages()
        assertEquals(2, result.size)
    }

    @Test
    fun `toDomainSessions maps empty list`() {
        assertTrue(emptyList<ChatSessionEntity>().toDomainSessions().isEmpty())
    }

    // ── createChatMessage ───────────────────────────────────────────

    @Test
    fun `createChatMessage sets fields correctly`() {
        val msg = createChatMessage(
            content = "Hello",
            role = MessageRole.USER,
            relatedGoalId = "g-1"
        )
        assertEquals("Hello", msg.content)
        assertEquals(MessageRole.USER, msg.role)
        assertEquals("g-1", msg.relatedGoalId)
        assertNull(msg.metadata)
    }

    @Test
    fun `createChatMessage generates unique ids`() {
        val m1 = createChatMessage(content = "A", role = MessageRole.USER)
        val m2 = createChatMessage(content = "B", role = MessageRole.USER)
        assertNotEquals(m1.id, m2.id)
    }

    // ── createChatSession ───────────────────────────────────────────

    @Test
    fun `createChatSession sets defaults`() {
        val session = createChatSession()
        assertEquals("New Chat", session.title)
        assertEquals("luna_general", session.coachId)
        assertTrue(session.messages.isEmpty())
        assertNull(session.summary)
    }

    @Test
    fun `createChatSession uses provided parameters`() {
        val session = createChatSession(title = "Custom Chat", coachId = "coach-42")
        assertEquals("Custom Chat", session.title)
        assertEquals("coach-42", session.coachId)
    }

    @Test
    fun `createChatSession generates unique ids`() {
        val s1 = createChatSession()
        val s2 = createChatSession()
        assertNotEquals(s1.id, s2.id)
    }

    // ════════════════════════════════════════════════════════════════
    // FocusMapper
    // ════════════════════════════════════════════════════════════════

    // ── FocusSessionEntity.toDomain ─────────────────────────────────

    @Test
    fun `FocusSessionEntity toDomain maps all fields`() {
        val entity = FocusSessionEntity(
            id = "focus-1",
            goalId = "goal-1",
            milestoneId = "ms-1",
            plannedDurationMinutes = 25L,
            actualDurationSeconds = 1500L,
            wasCompleted = 1L,
            xpEarned = 20L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = "2026-03-06T10:25:00",
            createdAt = "2026-03-06T09:55:00",
            mood = "HAPPY",
            ambientSound = "RAIN",
            focusTheme = "FOREST",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val session = entity.toDomain()

        assertEquals("focus-1", session.id)
        assertEquals("goal-1", session.goalId)
        assertEquals("ms-1", session.milestoneId)
        assertEquals(25, session.plannedDurationMinutes)
        assertEquals(1500, session.actualDurationSeconds)
        assertTrue(session.wasCompleted)
        assertEquals(20, session.xpEarned)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), session.startedAt)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 25, 0), session.completedAt)
        assertEquals(Mood.HAPPY, session.mood)
        assertEquals(AmbientSound.RAIN, session.ambientSound)
        assertEquals(FocusTheme.FOREST, session.focusTheme)
    }

    @Test
    fun `FocusSessionEntity toDomain maps wasCompleted 0 to false`() {
        val entity = FocusSessionEntity(
            id = "focus-2",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 600L,
            wasCompleted = 0L, xpEarned = 5L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertFalse(entity.toDomain().wasCompleted)
    }

    @Test
    fun `FocusSessionEntity toDomain maps null completedAt`() {
        val entity = FocusSessionEntity(
            id = "focus-3",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 600L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().completedAt)
    }

    @Test
    fun `FocusSessionEntity toDomain maps null mood`() {
        val entity = FocusSessionEntity(
            id = "focus-null-mood",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().mood)
    }

    @Test
    fun `FocusSessionEntity toDomain maps invalid mood to null`() {
        val entity = FocusSessionEntity(
            id = "focus-bad-mood",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = "INVALID_MOOD", ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().mood)
    }

    @Test
    fun `FocusSessionEntity toDomain maps all valid Mood values`() {
        for (mood in Mood.entries) {
            val entity = FocusSessionEntity(
                id = "focus-mood-${mood.name}",
                goalId = "g", milestoneId = "m",
                plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
                wasCompleted = 0L, xpEarned = 0L,
                startedAt = "2026-03-06T10:00:00",
                completedAt = null, createdAt = "2026-03-06T10:00:00",
                mood = mood.name, ambientSound = null, focusTheme = null,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            assertEquals(mood, entity.toDomain().mood, "Failed for mood $mood")
        }
    }

    @Test
    fun `FocusSessionEntity toDomain maps null ambientSound`() {
        val entity = FocusSessionEntity(
            id = "focus-null-ambient",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().ambientSound)
    }

    @Test
    fun `FocusSessionEntity toDomain maps invalid ambientSound to null`() {
        val entity = FocusSessionEntity(
            id = "focus-bad-ambient",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = "UNKNOWN_SOUND", focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().ambientSound)
    }

    @Test
    fun `FocusSessionEntity toDomain maps all valid AmbientSound values`() {
        for (sound in AmbientSound.entries) {
            val entity = FocusSessionEntity(
                id = "focus-sound-${sound.name}",
                goalId = "g", milestoneId = "m",
                plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
                wasCompleted = 0L, xpEarned = 0L,
                startedAt = "2026-03-06T10:00:00",
                completedAt = null, createdAt = "2026-03-06T10:00:00",
                mood = null, ambientSound = sound.name, focusTheme = null,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            assertEquals(sound, entity.toDomain().ambientSound, "Failed for sound $sound")
        }
    }

    @Test
    fun `FocusSessionEntity toDomain maps null focusTheme`() {
        val entity = FocusSessionEntity(
            id = "focus-null-theme",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().focusTheme)
    }

    @Test
    fun `FocusSessionEntity toDomain maps invalid focusTheme to null`() {
        val entity = FocusSessionEntity(
            id = "focus-bad-theme",
            goalId = "g", milestoneId = "m",
            plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
            wasCompleted = 0L, xpEarned = 0L,
            startedAt = "2026-03-06T10:00:00",
            completedAt = null, createdAt = "2026-03-06T10:00:00",
            mood = null, ambientSound = null, focusTheme = "UNKNOWN_THEME",
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().focusTheme)
    }

    @Test
    fun `FocusSessionEntity toDomain maps all valid FocusTheme values`() {
        for (theme in FocusTheme.entries) {
            val entity = FocusSessionEntity(
                id = "focus-theme-${theme.name}",
                goalId = "g", milestoneId = "m",
                plannedDurationMinutes = 25L, actualDurationSeconds = 0L,
                wasCompleted = 0L, xpEarned = 0L,
                startedAt = "2026-03-06T10:00:00",
                completedAt = null, createdAt = "2026-03-06T10:00:00",
                mood = null, ambientSound = null, focusTheme = theme.name,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            assertEquals(theme, entity.toDomain().focusTheme, "Failed for theme $theme")
        }
    }

    // ── FocusSession.toEntity ───────────────────────────────────────

    @Test
    fun `FocusSession toEntity maps all fields`() {
        val session = testFocusSession(
            id = "f-1",
            goalId = "g-1",
            milestoneId = "m-1",
            plannedDurationMinutes = 45,
            actualDurationSeconds = 2700,
            wasCompleted = true,
            xpEarned = 30,
            startedAt = LocalDateTime(2026, 3, 6, 10, 0, 0),
            completedAt = LocalDateTime(2026, 3, 6, 10, 45, 0),
            createdAt = LocalDateTime(2026, 3, 6, 9, 55, 0),
            mood = Mood.HAPPY,
            ambientSound = AmbientSound.RAIN,
            focusTheme = FocusTheme.OCEAN
        )

        val entity = session.toEntity()

        assertEquals("f-1", entity.id)
        assertEquals("g-1", entity.goalId)
        assertEquals("m-1", entity.milestoneId)
        assertEquals(45L, entity.plannedDurationMinutes)
        assertEquals(2700L, entity.actualDurationSeconds)
        assertEquals(1L, entity.wasCompleted)
        assertEquals(30L, entity.xpEarned)
        assertEquals("HAPPY", entity.mood)
        assertEquals("RAIN", entity.ambientSound)
        assertEquals("OCEAN", entity.focusTheme)
    }

    @Test
    fun `FocusSession toEntity maps wasCompleted false to 0L`() {
        val session = testFocusSession(wasCompleted = false)
        assertEquals(0L, session.toEntity().wasCompleted)
    }

    @Test
    fun `FocusSession toEntity maps null mood to null`() {
        val session = testFocusSession(mood = null)
        assertNull(session.toEntity().mood)
    }

    @Test
    fun `FocusSession toEntity maps null ambientSound to null`() {
        val session = testFocusSession(ambientSound = null)
        assertNull(session.toEntity().ambientSound)
    }

    @Test
    fun `FocusSession toEntity maps null focusTheme to null`() {
        val session = testFocusSession(focusTheme = null)
        assertNull(session.toEntity().focusTheme)
    }

    @Test
    fun `FocusSession toEntity maps null completedAt to null`() {
        val session = testFocusSession(completedAt = null)
        assertNull(session.toEntity().completedAt)
    }

    // ── FocusSession round trip ─────────────────────────────────────

    @Test
    fun `FocusSession round trip preserves id`() {
        val original = testFocusSession(id = "rt-focus")
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
    }

    @Test
    fun `FocusSession round trip preserves goalId and milestoneId`() {
        val original = testFocusSession(goalId = "g-99", milestoneId = "m-99")
        val restored = original.toEntity().toDomain()
        assertEquals(original.goalId, restored.goalId)
        assertEquals(original.milestoneId, restored.milestoneId)
    }

    @Test
    fun `FocusSession round trip preserves durations`() {
        val original = testFocusSession(plannedDurationMinutes = 60, actualDurationSeconds = 3600)
        val restored = original.toEntity().toDomain()
        assertEquals(original.plannedDurationMinutes, restored.plannedDurationMinutes)
        assertEquals(original.actualDurationSeconds, restored.actualDurationSeconds)
    }

    @Test
    fun `FocusSession round trip preserves wasCompleted`() {
        val original = testFocusSession(wasCompleted = false)
        val restored = original.toEntity().toDomain()
        assertEquals(original.wasCompleted, restored.wasCompleted)
    }

    @Test
    fun `FocusSession round trip preserves enum fields`() {
        val original = testFocusSession(
            mood = Mood.VERY_SAD,
            ambientSound = AmbientSound.WHITE_NOISE,
            focusTheme = FocusTheme.NIGHT_SKY
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original.mood, restored.mood)
        assertEquals(original.ambientSound, restored.ambientSound)
        assertEquals(original.focusTheme, restored.focusTheme)
    }

    @Test
    fun `FocusSession round trip preserves null enum fields`() {
        val original = testFocusSession(mood = null, ambientSound = null, focusTheme = null)
        val restored = original.toEntity().toDomain()
        assertNull(restored.mood)
        assertNull(restored.ambientSound)
        assertNull(restored.focusTheme)
    }
}
