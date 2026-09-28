@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.util.TimeZone

actual class CalendarWriter {

    private val context: Context by lazy { KoinPlatform.getKoin().get() }

    private fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    actual suspend fun canWrite(): Boolean = granted() && defaultCalendarId() != null

    /** The primary visible calendar we may write to, else the first writable one. */
    private suspend fun defaultCalendarId(): Long? = withContext(Dispatchers.IO) {
        if (!granted()) return@withContext null
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } catch (e: Exception) {
            Logger.w("CalendarWriter") { "No writable calendar: ${e.message}" }
            null
        }
    }

    actual suspend fun addEvent(title: String, startEpochMs: Long, endEpochMs: Long, notes: String?): String? {
        val calendarId = defaultCalendarId() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, startEpochMs)
                    put(CalendarContract.Events.DTEND, endEpochMs)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    if (notes != null) put(CalendarContract.Events.DESCRIPTION, notes)
                }
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment
            } catch (e: Exception) {
                Logger.w("CalendarWriter") { "Failed to add event: ${e.message}" }
                null
            }
        }
    }

    actual suspend fun moveEvent(id: String, startEpochMs: Long, endEpochMs: Long): Boolean = update(id) {
        put(CalendarContract.Events.DTSTART, startEpochMs)
        put(CalendarContract.Events.DTEND, endEpochMs)
    }

    actual suspend fun deleteEvent(id: String): Boolean = withContext(Dispatchers.IO) {
        val eventId = id.toLongOrNull() ?: return@withContext false
        if (!granted()) return@withContext false
        try {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun update(id: String, block: ContentValues.() -> Unit): Boolean = withContext(Dispatchers.IO) {
        val eventId = id.toLongOrNull() ?: return@withContext false
        if (!granted()) return@withContext false
        try {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), ContentValues().apply(block), null, null,
            ) > 0
        } catch (e: Exception) {
            false
        }
    }
}
