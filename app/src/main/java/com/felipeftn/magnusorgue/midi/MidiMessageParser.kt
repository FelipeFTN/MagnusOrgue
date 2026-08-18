package com.felipeftn.magnusorgue.midi

sealed interface MidiEvent {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    data class NoteOff(val channel: Int, val note: Int) : MidiEvent
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiEvent
}

/**
 * Raw MIDI bytes -> events. Stateful, because two of the wire format's
 * quirks cross packet boundaries:
 *
 *  - Running status: after one Note On, a device may send just data-byte
 *    pairs and expect you to remember the status byte. Plenty of hardware
 *    actually does this.
 *  - Real-time bytes (0xF8 clock, 0xFE active sensing...) can be injected
 *    ANYWHERE, including between the two data bytes of another message.
 *    They must be skipped without disturbing the parser state. Yes, really.
 *
 * One parser instance per device connection — state must not be shared.
 *
 * Spec summary: https://midi.org/summary-of-midi-1-0-messages
 */
class MidiMessageParser {

    private var status = 0    // current (possibly running) status byte, 0 = none
    private var firstData = -1 // buffered first data byte, -1 = none yet

    fun feed(data: ByteArray, offset: Int, count: Int): List<MidiEvent> {
        val events = mutableListOf<MidiEvent>()

        for (i in offset until offset + count) {
            val b = data[i].toInt() and 0xFF
            when {
                // Real-time: ignore, and crucially, don't touch any state.
                b >= 0xF8 -> {}

                // System common (SysEx and friends) cancels running status.
                // We don't parse them — an organ has no use for SysEx.
                b >= 0xF0 -> {
                    status = 0
                    firstData = -1
                }

                // New channel status byte.
                b >= 0x80 -> {
                    status = b
                    firstData = -1
                }

                // Data byte.
                else -> consumeData(b, events)
            }
        }
        return events
    }

    private fun consumeData(b: Int, out: MutableList<MidiEvent>) {
        if (status == 0) return // stray data with no status — nothing to do

        val kind = status and 0xF0
        // Program Change and Channel Pressure carry a single data byte.
        // We don't emit them, but they still have to be framed correctly or
        // running status after them falls apart.
        if (kind == 0xC0 || kind == 0xD0) return

        if (firstData < 0) {
            firstData = b
            return
        }

        emit(kind, status and 0x0F, firstData, b, out)
        firstData = -1 // keep `status` — that's running status doing its thing
    }

    private fun emit(kind: Int, channel: Int, d1: Int, d2: Int, out: MutableList<MidiEvent>) {
        when (kind) {
            // The famous one: Note On with velocity 0 IS a Note Off. Most
            // keyboards send it that way to exploit running status.
            0x90 -> out += if (d2 == 0) MidiEvent.NoteOff(channel, d1)
                           else MidiEvent.NoteOn(channel, d1, d2)
            0x80 -> out += MidiEvent.NoteOff(channel, d1)
            0xB0 -> out += MidiEvent.ControlChange(channel, d1, d2)
            // 0xA0 (poly aftertouch) and 0xE0 (pitch bend): framed above so
            // the stream stays in sync, but an organ has nothing to do with
            // them, so no event.
        }
    }
}
