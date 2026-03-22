package az.tribe.lifeplanner.util

/**
 * Compare two semver-style version strings (e.g. "2.1", "2.1.3").
 * Returns true if [current] is strictly below [minimum].
 */
fun isVersionBelow(current: String, minimum: String): Boolean {
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val minimumParts = minimum.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(currentParts.size, minimumParts.size)
    for (i in 0 until maxLen) {
        val c = currentParts.getOrElse(i) { 0 }
        val m = minimumParts.getOrElse(i) { 0 }
        if (c < m) return true
        if (c > m) return false
    }
    return false // equal
}
