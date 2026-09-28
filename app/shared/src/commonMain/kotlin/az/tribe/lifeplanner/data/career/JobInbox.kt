package az.tribe.lifeplanner.data.career

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A job ad or link shared into the app from another one (Android's share sheet), waiting for the
 * Career page to open "Add an application" with it. Held until taken, so it survives sign-in.
 */
object JobInbox {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String) {
        text.trim().takeIf { it.isNotEmpty() }?.let { _pending.value = it }
    }

    fun take(): String? = _pending.value.also { _pending.value = null }
}
