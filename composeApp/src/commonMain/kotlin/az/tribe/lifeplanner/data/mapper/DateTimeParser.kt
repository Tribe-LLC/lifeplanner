package az.tribe.lifeplanner.data.mapper

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Safely parse a timestamp string into LocalDateTime.
 * Handles both plain LocalDateTime format (2026-03-03T13:06:35.429)
 * and Instant/timestamptz format with timezone offset (2026-03-03T13:06:35.429+00:00 or Z).
 */
fun parseLocalDateTime(value: String): LocalDateTime {
    return try {
        LocalDateTime.parse(value)
    } catch (_: Exception) {
        Instant.parse(value).toLocalDateTime(TimeZone.UTC)
    }
}
