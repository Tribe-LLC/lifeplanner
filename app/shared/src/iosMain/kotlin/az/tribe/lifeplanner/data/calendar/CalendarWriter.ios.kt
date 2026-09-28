@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.calendar

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpan
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
actual class CalendarWriter {

    // A store per call for the same reason as CalendarReader: a long-lived one keeps its
    // pre-permission snapshot.
    private fun store() = EKEventStore()

    private fun granted(): Boolean {
        val status = EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent)
        return status == EKAuthorizationStatusAuthorized || status == EKAuthorizationStatusFullAccess
    }

    private fun date(ms: Long) = NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)

    actual suspend fun canWrite(): Boolean = granted() && store().defaultCalendarForNewEvents != null

    actual suspend fun addEvent(title: String, startEpochMs: Long, endEpochMs: Long, notes: String?): String? {
        if (!granted()) return null
        val store = store()
        val calendar = store.defaultCalendarForNewEvents ?: return null
        val event = EKEvent.eventWithEventStore(store).apply {
            setTitle(title)
            setStartDate(date(startEpochMs))
            setEndDate(date(endEpochMs))
            setNotes(notes)
            setCalendar(calendar)
        }
        return if (store.saveEvent(event, EKSpan.EKSpanThisEvent, null)) event.eventIdentifier else {
            Logger.w("CalendarWriter") { "Failed to add event" }
            null
        }
    }

    actual suspend fun moveEvent(id: String, startEpochMs: Long, endEpochMs: Long): Boolean {
        if (!granted()) return false
        val store = store()
        val event = store.eventWithIdentifier(id) ?: return false
        event.setStartDate(date(startEpochMs))
        event.setEndDate(date(endEpochMs))
        return store.saveEvent(event, EKSpan.EKSpanThisEvent, null)
    }

    actual suspend fun deleteEvent(id: String): Boolean {
        if (!granted()) return false
        val store = store()
        val event = store.eventWithIdentifier(id) ?: return false
        return store.removeEvent(event, EKSpan.EKSpanThisEvent, null)
    }
}
