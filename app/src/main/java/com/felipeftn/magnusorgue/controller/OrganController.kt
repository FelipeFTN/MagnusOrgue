package com.felipeftn.magnusorgue.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.felipeftn.magnusorgue.audio.AudioEngine

/**
 * The single funnel for note events. Touch and MIDI both land here, and the
 * audio engine never knows (or cares) which one a note came from. As a bonus,
 * MIDI notes light up the on-screen keys for free.
 *
 * All fields are Compose snapshot state so the UI redraws itself when
 * anything changes.
 */
class OrganController {

    /** Notes currently sounding, from any input. Drives key highlighting. */
    var activeNotes by mutableStateOf(emptySet<Int>())
        private set

    var stopIndex by mutableStateOf(0)
        private set

    /** Bottom octave of the on-screen keyboard (C4 = octave 4 = MIDI 60). */
    var baseOctave by mutableStateOf(4)
        private set

    var volume by mutableStateOf(0.8f)
        private set

    /** Product name of the connected MIDI device, or null. */
    var midiDeviceName by mutableStateOf<String?>(null)

    /** False when the audio stream refused to open — shows the error banner. */
    var audioReady by mutableStateOf(true)

    // noteOn/noteOff arrive concurrently from the UI thread and the MIDI
    // callback thread. Snapshot writes are individually thread-safe, but
    // "read set, add note, write set" is not — hence the lock. The audio
    // thread never comes near this; it's UI bookkeeping only.
    private val notesLock = Any()

    fun noteOn(note: Int) {
        if (note !in 0..127) return
        AudioEngine.noteOn(note)
        synchronized(notesLock) { activeNotes = activeNotes + note }
    }

    fun noteOff(note: Int) {
        if (note !in 0..127) return
        AudioEngine.noteOff(note)
        synchronized(notesLock) { activeNotes = activeNotes - note }
    }

    /** Silence everything, now. Bound to the PANIC button and a few edge cases. */
    fun panic() {
        AudioEngine.allNotesOff()
        synchronized(notesLock) { activeNotes = emptySet() }
    }

    fun selectStop(index: Int) {
        stopIndex = index
        AudioEngine.setStop(index)
    }

    // Not setVolume(): the `volume` property's generated setter already
    // claims that JVM signature and Kotlin refuses the clash.
    fun changeVolume(value: Float) {
        volume = value
        AudioEngine.setVolume(value)
    }

    /** Shifts the on-screen keyboard. MIDI input is never transposed. */
    fun shiftOctave(delta: Int) {
        baseOctave = (baseOctave + delta).coerceIn(1, 6)
    }
}
