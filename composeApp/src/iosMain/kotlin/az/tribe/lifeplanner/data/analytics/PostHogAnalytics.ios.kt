package az.tribe.lifeplanner.data.analytics

/**
 * iOS PostHog bridge — delegates to native PostHog Swift SDK via bridge interface.
 * The bridge is set in iOSApp.swift during app initialization.
 */
interface PostHogBridge {
    fun setup(apiKey: String, host: String)
    fun identify(userId: String, properties: Map<String, Any>)
    fun reset()
    fun capture(event: String, properties: Map<String, Any>)
    fun screen(screenName: String, properties: Map<String, Any>)
    fun isFeatureEnabled(flag: String): Boolean
    fun getFeatureFlag(flag: String): Any?
    fun reloadFeatureFlags()
    fun group(type: String, key: String, properties: Map<String, Any>)
    fun flush()
}

actual object PostHogAnalytics {
    var bridge: PostHogBridge? = null

    actual fun setup(apiKey: String, host: String) {
        bridge?.setup(apiKey, host)
    }

    actual fun identify(userId: String, properties: Map<String, Any>) {
        bridge?.identify(userId, properties)
    }

    actual fun reset() {
        bridge?.reset()
    }

    actual fun capture(event: String, properties: Map<String, Any>) {
        bridge?.capture(event, properties)
    }

    actual fun setUserProperties(properties: Map<String, Any>) {
        // iOS: identify with current distinctId to set properties
        bridge?.capture("\$set", properties)
    }

    actual fun screen(screenName: String, properties: Map<String, Any>) {
        bridge?.screen(screenName, properties)
    }

    actual fun isFeatureEnabled(flag: String): Boolean {
        return bridge?.isFeatureEnabled(flag) ?: false
    }

    actual fun getFeatureFlag(flag: String): Any? {
        return bridge?.getFeatureFlag(flag)
    }

    actual fun reloadFeatureFlags() {
        bridge?.reloadFeatureFlags()
    }

    actual fun group(type: String, key: String, properties: Map<String, Any>) {
        bridge?.group(type, key, properties)
    }

    actual fun flush() {
        bridge?.flush()
    }
}
