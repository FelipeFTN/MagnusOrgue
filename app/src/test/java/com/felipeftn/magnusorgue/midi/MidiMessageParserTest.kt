package com.felipeftn.magnusorgue.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser is the one piece of this app most likely to break against
 * real hardware, so it gets the most tests. Every case here is something
 * an actual keyboard does on the wire.
 */
class MidiMessageParserTest {

    private fun parse(vararg bytes: Int): List<MidiEvent> {
        val data = ByteArray(bytes.size) { bytes[it].toByte() }
        return MidiMessageParser().feed(data, 0, data.size)
    }

    @Test
    fun `plain note on and off`() {
        val events = parse(0x90, 60, 100, 0x80, 60, 0)
        assertEquals(
            listOf(MidiEvent.NoteOn(0, 60, 100), MidiEvent.NoteOff(0, 60)),
            events,
        )
    }

    @Test
    fun `note on with velocity zero is a note off`() {
        // Most keyboards do this instead of sending a real 0x80.
        val events = parse(0x90, 60, 0)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOff(0, 60)), events)
    }

    @Test
    fun `running status repeats the last status byte`() {
        // One 0x90, then three note pairs with no further status bytes.
        val events = parse(0x90, 60, 100, 64, 100, 67, 100)
        assertEquals(
            listOf(
                MidiEvent.NoteOn(0, 60, 100),
                MidiEvent.NoteOn(0, 64, 100),
                MidiEvent.NoteOn(0, 67, 100),
            ),
            events,
        )
    }

    @Test
    fun `running status survives across feed calls`() {
        val parser = MidiMessageParser()
        val first = byteArrayOf(0x90.toByte(), 60, 100)
        val second = byteArrayOf(64, 100) // no status — relies on remembered 0x90
        parser.feed(first, 0, first.size)
        val events = parser.feed(second, 0, second.size)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 64, 100)), events)
    }

    @Test
    fun `message split across packets`() {
        val parser = MidiMessageParser()
        val first = byteArrayOf(0x90.toByte(), 60) // cut mid-message
        val second = byteArrayOf(100)
        assertTrue(parser.feed(first, 0, first.size).isEmpty())
        assertEquals(
            listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)),
            parser.feed(second, 0, second.size),
        )
    }

    @Test
    fun `realtime bytes interleaved mid-message are ignored`() {
        // 0xF8 (clock) rudely injected between the data bytes of a Note On.
        // The spec allows it. The parser must not flinch.
        val events = parse(0x90, 60, 0xF8, 100)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), events)
    }

    @Test
    fun `several messages in one packet`() {
        val events = parse(0x90, 60, 100, 0x90, 64, 100, 0x80, 60, 0)
        assertEquals(3, events.size)
    }

    @Test
    fun `channel is extracted from the status byte`() {
        val events = parse(0x95, 60, 100) // channel 6 (0-indexed: 5)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(5, 60, 100)), events)
    }

    @Test
    fun `control change comes through`() {
        val events = parse(0xB0, 123, 0)
        assertEquals(listOf<MidiEvent>(MidiEvent.ControlChange(0, 123, 0)), events)
    }

    @Test
    fun `program change does not desync running status`() {
        // PC carries one data byte. If the parser framed it as two, the
        // following Note On would be misread.
        val events = parse(0xC0, 5, 0x90, 60, 100)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), events)
    }

    @Test
    fun `pitch bend is framed but not emitted`() {
        val events = parse(0xE0, 0x00, 0x40, 0x90, 60, 100)
        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(0, 60, 100)), events)
    }

    @Test
    fun `stray data bytes without status are dropped`() {
        assertTrue(parse(60, 100).isEmpty())
    }
}
