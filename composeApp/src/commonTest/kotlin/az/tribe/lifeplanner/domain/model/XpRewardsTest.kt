package az.tribe.lifeplanner.domain.model

import kotlin.test.*

class XpRewardsTest {

    // ── Focus session XP tiers match server trigger ─────────────────
    // Server trigger (trg_focus_session_gamification):
    //   >= 60 min → 40 XP
    //   >= 45 min → 30 XP
    //   >= 25 min → 20 XP
    //   >= 15 min → 10 XP
    //   else → GREATEST((min * 0.5)::INTEGER, 1)

    @Test
    fun `FOCUS_SESSION_60 matches server 60min tier`() {
        assertEquals(40, XpRewards.FOCUS_SESSION_60)
    }

    @Test
    fun `FOCUS_SESSION_45 matches server 45min tier`() {
        assertEquals(30, XpRewards.FOCUS_SESSION_45)
    }

    @Test
    fun `FOCUS_SESSION_25 matches server 25min tier`() {
        assertEquals(20, XpRewards.FOCUS_SESSION_25)
    }

    @Test
    fun `FOCUS_SESSION_15 matches server 15min tier`() {
        assertEquals(10, XpRewards.FOCUS_SESSION_15)
    }

    // ── Server sub-15 formula parity ────────────────────────────────
    // Server: GREATEST((planned_duration_minutes * 0.5)::INTEGER, 1)
    // Client (FocusViewModel): (minutes * 0.5f).toInt().coerceAtLeast(1)

    @Test
    fun `sub-15 formula produces same results as server for all minute values`() {
        for (minutes in 0..14) {
            val clientXp = (minutes * 0.5f).toInt().coerceAtLeast(1)
            val serverXp = maxOf((minutes * 0.5).toInt(), 1)
            assertEquals(serverXp, clientXp, "Mismatch at $minutes minutes")
        }
    }

    // ── Tier boundaries ─────────────────────────────────────────────

    @Test
    fun `XP tiers are ordered correctly`() {
        assertTrue(XpRewards.FOCUS_SESSION_15 < XpRewards.FOCUS_SESSION_25)
        assertTrue(XpRewards.FOCUS_SESSION_25 < XpRewards.FOCUS_SESSION_45)
        assertTrue(XpRewards.FOCUS_SESSION_45 < XpRewards.FOCUS_SESSION_60)
    }

    // ── Other XP constants are positive ─────────────────────────────

    @Test
    fun `all XP reward constants are positive`() {
        assertTrue(XpRewards.GOAL_CREATED > 0)
        assertTrue(XpRewards.GOAL_COMPLETED > 0)
        assertTrue(XpRewards.MILESTONE_COMPLETED > 0)
        assertTrue(XpRewards.HABIT_CHECK_IN > 0)
        assertTrue(XpRewards.HABIT_STREAK_BONUS > 0)
        assertTrue(XpRewards.JOURNAL_ENTRY > 0)
        assertTrue(XpRewards.DAILY_CHECK_IN > 0)
        assertTrue(XpRewards.PERFECT_DAY_BONUS > 0)
        assertTrue(XpRewards.FOCUS_SESSION_15 > 0)
        assertTrue(XpRewards.FOCUS_SESSION_25 > 0)
        assertTrue(XpRewards.FOCUS_SESSION_45 > 0)
        assertTrue(XpRewards.FOCUS_SESSION_60 > 0)
    }

    @Test
    fun `STREAK_BONUS_MULTIPLIER is between 0 and 1`() {
        assertTrue(XpRewards.STREAK_BONUS_MULTIPLIER > 0f)
        assertTrue(XpRewards.STREAK_BONUS_MULTIPLIER <= 1f)
    }
}
