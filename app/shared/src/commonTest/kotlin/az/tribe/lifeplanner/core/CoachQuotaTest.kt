package az.tribe.lifeplanner.core

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachQuotaTest {
    private class Gate(val premium: Boolean) : PremiumGate {
        override val billingAvailable = true
        override suspend fun isPremium() = premium
    }

    @Test
    fun free_user_is_stopped_after_twenty_messages() = runTest {
        val q = CoachQuota(MapSettings(), Gate(premium = false))
        repeat(FreeLimits.COACH_MESSAGES_PER_DAY) {
            assertTrue(q.canSend())
            q.record()
        }
        assertFalse(q.canSend())
    }

    @Test
    fun premium_user_is_never_stopped() = runTest {
        val q = CoachQuota(MapSettings(), Gate(premium = true))
        repeat(FreeLimits.COACH_MESSAGES_PER_DAY + 5) { q.record() }
        assertTrue(q.canSend())
    }

    @Test
    fun yesterdays_count_does_not_carry_over() = runTest {
        val s = MapSettings()
        s.putString("coach_quota_day", "2000-01-01")
        s.putInt("coach_quota_count", 99)
        assertTrue(CoachQuota(s, Gate(premium = false)).canSend())
    }
}
