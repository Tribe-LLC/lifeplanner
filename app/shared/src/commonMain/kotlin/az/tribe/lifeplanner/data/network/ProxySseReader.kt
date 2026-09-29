package az.tribe.lifeplanner.data.network

/**
 * Reads the ai-proxy's stream, which writes each piece as `event: X\ndata: <text>\n\n` without
 * escaping line breaks inside the text. A piece holding "\n\n1. Wake up" therefore arrives over
 * several lines, and the old reader kept only the first one, so replies lost their line breaks and
 * whole list items. Here everything from `data: ` up to the next `event: ` line belongs to the same
 * piece, and only the blank line that ends it is dropped. Kept on the app side so every proxy
 * already deployed keeps working.
 */
internal class ProxySseReader {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Feeds one line; returns a finished (event, data) when this line starts the next one. */
    fun push(line: String): Pair<String, String>? {
        if (line.startsWith("event: ")) {
            val done = take()
            event = line.removePrefix("event: ").trim()
            return done
        }
        if (!hasData) {
            if (line.startsWith("data: ")) {
                hasData = true
                data.append(line.removePrefix("data: "))
            }
        } else {
            data.append('\n').append(line)
        }
        return null
    }

    /** The last piece, once the stream has ended. */
    fun finish(): Pair<String, String>? = take()

    private fun take(): Pair<String, String>? {
        val e = event
        val out = if (e != null && hasData) e to data.toString().removeSuffix("\n") else null
        event = null
        data.clear()
        hasData = false
        return out
    }
}
