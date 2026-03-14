package az.tribe.lifeplanner.domain.model

import kotlin.test.*

class UserProgressTest {

    // ── calculateXpForLevel ─────────────────────────────────────────

    @Test
    fun `calculateXpForLevel returns 150 for level 1`() {
        assertEquals(150, UserProgress.calculateXpForLevel(1))
    }

    @Test
    fun `calculateXpForLevel returns 300 for level 2`() {
        assertEquals(300, UserProgress.calculateXpForLevel(2))
    }

    @Test
    fun `calculateXpForLevel returns 450 for level 3`() {
        assertEquals(450, UserProgress.calculateXpForLevel(3))
    }

    @Test
    fun `calculateXpForLevel scales linearly with level`() {
        // Formula: (100 * level * 1.5).toInt()
        for (level in 1..50) {
            val expected = (100 * level * 1.5).toInt()
            assertEquals(expected, UserProgress.calculateXpForLevel(level))
        }
    }

    // ── calculateTotalXpForLevel ────────────────────────────────────

    @Test
    fun `calculateTotalXpForLevel returns 0 for level 1`() {
        // Level 1 is the starting level, no XP accumulated before it
        assertEquals(0, UserProgress.calculateTotalXpForLevel(1))
    }

    @Test
    fun `calculateTotalXpForLevel returns 150 for level 2`() {
        // Need to complete level 1 (150 XP) to reach level 2
        assertEquals(150, UserProgress.calculateTotalXpForLevel(2))
    }

    @Test
    fun `calculateTotalXpForLevel returns 450 for level 3`() {
        // Level 1 (150) + Level 2 (300) = 450
        assertEquals(450, UserProgress.calculateTotalXpForLevel(3))
    }

    @Test
    fun `calculateTotalXpForLevel accumulates correctly`() {
        var accumulated = 0
        for (level in 1..20) {
            assertEquals(accumulated, UserProgress.calculateTotalXpForLevel(level))
            accumulated += UserProgress.calculateXpForLevel(level)
        }
    }

    // ── calculateLevelFromXp ────────────────────────────────────────

    @Test
    fun `calculateLevelFromXp returns 1 for 0 XP`() {
        assertEquals(1, UserProgress.calculateLevelFromXp(0))
    }

    @Test
    fun `calculateLevelFromXp returns 1 for 149 XP`() {
        // Level 1 needs 150 XP to complete
        assertEquals(1, UserProgress.calculateLevelFromXp(149))
    }

    @Test
    fun `calculateLevelFromXp returns 2 at exactly 150 XP`() {
        // 150 XP completes level 1
        assertEquals(2, UserProgress.calculateLevelFromXp(150))
    }

    @Test
    fun `calculateLevelFromXp returns 2 for 449 XP`() {
        // Level 2 needs 300 more XP (total 450)
        assertEquals(2, UserProgress.calculateLevelFromXp(449))
    }

    @Test
    fun `calculateLevelFromXp returns 3 at exactly 450 XP`() {
        assertEquals(3, UserProgress.calculateLevelFromXp(450))
    }

    @Test
    fun `calculateLevelFromXp is inverse of calculateTotalXpForLevel at boundaries`() {
        for (level in 1..30) {
            val totalXpAtLevel = UserProgress.calculateTotalXpForLevel(level)
            assertEquals(level, UserProgress.calculateLevelFromXp(totalXpAtLevel))
        }
    }

    @Test
    fun `calculateLevelFromXp returns correct level just below boundary`() {
        for (level in 2..20) {
            val totalXpAtLevel = UserProgress.calculateTotalXpForLevel(level)
            assertEquals(level - 1, UserProgress.calculateLevelFromXp(totalXpAtLevel - 1))
        }
    }

    @Test
    fun `calculateLevelFromXp handles very high XP`() {
        // 100,000 XP should return a reasonable high level without crashing
        val level = UserProgress.calculateLevelFromXp(100_000)
        assertTrue(level > 30, "100k XP should be well above level 30, got $level")
    }

    // ── Computed properties ─────────────────────────────────────────

    @Test
    fun `xpForCurrentLevel returns correct XP for level 1`() {
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 0, currentLevel = 1)
        assertEquals(150, progress.xpForCurrentLevel)
    }

    @Test
    fun `xpInCurrentLevel returns 0 at level boundary`() {
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 150, currentLevel = 2)
        assertEquals(0, progress.xpInCurrentLevel)
    }

    @Test
    fun `xpInCurrentLevel returns accumulated XP within level`() {
        // At level 2 with 250 total XP: 250 - 150 (total for level 2) = 100
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 250, currentLevel = 2)
        assertEquals(100, progress.xpInCurrentLevel)
    }

    @Test
    fun `xpInCurrentLevel is never negative`() {
        // Edge case: currentLevel inconsistent with totalXp
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 0, currentLevel = 5)
        assertEquals(0, progress.xpInCurrentLevel)
    }

    @Test
    fun `xpRemainingForNextLevel is correct mid-level`() {
        // Level 2, 250 total XP: in-level = 100, level needs 300, remaining = 200
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 250, currentLevel = 2)
        assertEquals(200, progress.xpRemainingForNextLevel)
    }

    @Test
    fun `xpRemainingForNextLevel equals xpForCurrentLevel at level start`() {
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 150, currentLevel = 2)
        assertEquals(300, progress.xpRemainingForNextLevel)
    }

    @Test
    fun `levelProgress is 0 at level start`() {
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 150, currentLevel = 2)
        assertEquals(0f, progress.levelProgress, 0.001f)
    }

    @Test
    fun `levelProgress is 0_5 at midpoint`() {
        // Level 2 needs 300 XP. Half = 150. Total at level 2 start = 150. So 150 + 150 = 300.
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 300, currentLevel = 2)
        assertEquals(0.5f, progress.levelProgress, 0.001f)
    }

    @Test
    fun `levelProgress is clamped to 1_0`() {
        // totalXp is more than what this level needs (inconsistent state)
        val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, totalXp = 500, currentLevel = 2)
        assertEquals(1.0f, progress.levelProgress, 0.001f)
    }

    @Test
    fun `levelProgress is 0 for fresh user`() {
        val progress = UserProgress.default()
        assertEquals(0f, progress.levelProgress, 0.001f)
    }

    // ── Title ───────────────────────────────────────────────────────

    @Test
    fun `title is Novice for levels 1 to 4`() {
        for (level in 1..4) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Novice", progress.title, "Level $level should be Novice")
        }
    }

    @Test
    fun `title is Beginner for levels 5 to 9`() {
        for (level in 5..9) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Beginner", progress.title, "Level $level should be Beginner")
        }
    }

    @Test
    fun `title is Intermediate for levels 10 to 14`() {
        for (level in 10..14) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Intermediate", progress.title, "Level $level should be Intermediate")
        }
    }

    @Test
    fun `title is Proficient for levels 15 to 19`() {
        for (level in 15..19) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Proficient", progress.title, "Level $level should be Proficient")
        }
    }

    @Test
    fun `title is Advanced for levels 20 to 24`() {
        for (level in 20..24) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Advanced", progress.title, "Level $level should be Advanced")
        }
    }

    @Test
    fun `title is Expert for levels 25 to 29`() {
        for (level in 25..29) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Expert", progress.title, "Level $level should be Expert")
        }
    }

    @Test
    fun `title is Champion for levels 30 to 39`() {
        for (level in 30..39) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Champion", progress.title, "Level $level should be Champion")
        }
    }

    @Test
    fun `title is Grandmaster for levels 40 to 49`() {
        for (level in 40..49) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Grandmaster", progress.title, "Level $level should be Grandmaster")
        }
    }

    @Test
    fun `title is Life Master for level 50 and above`() {
        for (level in listOf(50, 51, 100)) {
            val progress = UserProgress(currentStreak = 0, lastCheckInDate = null, currentLevel = level)
            assertEquals("Life Master", progress.title, "Level $level should be Life Master")
        }
    }

    // ── default() ───────────────────────────────────────────────────

    @Test
    fun `default has zero XP and level 1`() {
        val default = UserProgress.default()
        assertEquals(0, default.totalXp)
        assertEquals(1, default.currentLevel)
        assertEquals(0, default.currentStreak)
        assertNull(default.lastCheckInDate)
    }

    // ── Client-server parity: level calculation ─────────────────────
    // The server uses: floor(100 * level * 1.5)::INTEGER
    // The client uses: (100 * level * 1.5).toInt()
    // These must match for all reasonable levels.

    @Test
    fun `calculateXpForLevel matches server formula for all levels up to 100`() {
        for (level in 1..100) {
            val clientResult = UserProgress.calculateXpForLevel(level)
            // Server: floor(100 * level * 1.5)::INTEGER
            val serverResult = kotlin.math.floor(100.0 * level * 1.5).toInt()
            assertEquals(serverResult, clientResult, "Mismatch at level $level")
        }
    }
}
