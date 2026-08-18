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

    /** Indices of the pulled stops (into the shared stop list). */
    var activeStops by mutableStateOf(setOf(0))
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

    /** Pull or retire one stop. Stops combine, like ranks on a real organ. */
    fun toggleStop(index: Int) {
        activeStops = if (index in activeStops) activeStops - index else activeStops + index
        pushStopMask()
    }

    /**
     * The General Cancel piston: retires every stop and silences held
     * notes. What the old PANIC button grew up into.
     */
    fun generalCancel() {
        activeStops = emptySet()
        pushStopMask()
        panic()
    }

    private fun pushStopMask() {
        AudioEngine.setStopMask(activeStops.fold(0) { mask, i -> mask or (1 shl i) })
    }

    // Not setVolume(): the `volume` property's generated setter already
    // claims that JVM signature and Kotlin refuses the clash.
    fun changeVolume(value: Float) {
        volume = value
        AudioEngine.setVolume(value)
    }
}
