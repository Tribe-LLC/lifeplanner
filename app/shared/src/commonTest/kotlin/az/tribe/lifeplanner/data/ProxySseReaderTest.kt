package az.tribe.lifeplanner.data

import az.tribe.lifeplanner.data.network.ProxySseReader
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxySseReaderTest {
    /** Splits a wire string the way readUTF8Line does and runs it through the reader. */
    private fun read(wire: String): List<Pair<String, String>> {
        val r = ProxySseReader()
        val lines = wire.split("\n").let { if (wire.endsWith("\n")) it.dropLast(1) else it }
        return lines.mapNotNull { r.push(it) } + listOfNotNull(r.finish())
    }

    private fun piece(event: String, data: String) = "event: $event\ndata: $data\n\n"

    @Test
    fun keepsLineBreaksAndListItems() {
        val wire = piece("text", "Here are ideas:") + piece("text", "\n\n1. Wake up early\n2. Stretch") + piece("done", "{}")
        val text = read(wire).filter { it.first == "text" }.joinToString("") { it.second }
        assertEquals("Here are ideas:\n\n1. Wake up early\n2. Stretch", text)
    }

    @Test
    fun plainPiecesUnchanged() {
        assertEquals(listOf("text" to "Hello", "text" to " there", "done" to "{}"), read(piece("text", "Hello") + piece("text", " there") + piece("done", "{}")))
    }

    @Test
    fun trailingNewlineInsideAPieceIsKept() {
        assertEquals("text" to "one\n", read(piece("text", "one\n")).first())
    }

    @Test
    fun streamCutWithoutBlankLine() {
        assertEquals(listOf("text" to "partial"), read("event: text\ndata: partial"))
    }
}
