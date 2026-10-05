package az.tribe.lifeplanner.core

import co.touchlab.kermit.Logger
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo

/**
 * Premium entitlement gate, backed by the RevenueCat `premium` entitlement.
 *
 * Fails open on purpose. With no RevenueCat key (billing off) or on a transient error the answer
 * is "premium", so a config gap or a flaky network never takes away something a user already had.
 * [isPremium] is suspend because the RevenueCat check is a network round trip on a cold cache.
 */
interface PremiumGate {
    /** True when RevenueCat is configured, i.e. there is something to buy. */
    val billingAvailable: Boolean
    suspend fun isPremium(): Boolean
}

class RevenueCatPremiumGate(
    private val entitlementId: String = PREMIUM_ENTITLEMENT,
) : PremiumGate {
    override val billingAvailable: Boolean get() = Purchases.isConfigured

    override suspend fun isPremium(): Boolean {
        if (!Purchases.isConfigured) return true
        return runCatching {
            Purchases.sharedInstance.awaitCustomerInfo().entitlements.active[entitlementId] != null
        }.getOrElse {
            Logger.w("PremiumGate") { "entitlement check failed, staying open: ${it.message}" }
            true
        }
    }

    companion object {
        /** Must match the entitlement identifier in the RevenueCat dashboard. */
        const val PREMIUM_ENTITLEMENT = "premium"
    }
}

/** Always open. For tests, or when billing is deliberately off. */
class DefaultPremiumGate : PremiumGate {
    override val billingAvailable: Boolean = false
    override suspend fun isPremium(): Boolean = true
}
