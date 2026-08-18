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
 * Note bookkeeping got subtler with the Ottava Bassa coupler: one physical
 * key can drive TWO engine notes (the key and its lower octave), and two
 * physical keys an octave apart can want the SAME engine note. So engine
 * notes are reference-counted, and each physical press remembers exactly
 * which engine notes it triggered — that way toggling the coupler while
 * keys are held can't strand a note, which on an organ is not a bug but an
 * emergency.
 *
 * All public fields are Compose snapshot state so the UI redraws itself.
 */
class OrganController {

    /** Engine notes currently sounding, from any input. Drives key highlighting. */
    var activeNotes by mutableStateOf(emptySet<Int>())
        private set

    /** Indices of the pulled stops (into the shared stop list). */
    var activeStops by mutableStateOf(setOf(0))
        private set

    /** Tremulant: gentle wind wobble, whole organ. */
    var tremulant by mutableStateOf(false)
        private set

    /** Ottava Bassa: each key also plays its lower octave. */
    var subOctaveCoupler by mutableStateOf(false)
        private set

    var volume by mutableStateOf(0.8f)
        private set

    /** Product name of the connected MIDI device, or null. */
    var midiDeviceName by mutableStateOf<String?>(null)

    /** False when the audio stream refused to open — shows the error banner. */
    var audioReady by mutableStateOf(true)

    // Events arrive concurrently from the UI thread and the MIDI callback
    // thread; one lock keeps the maps and the snapshot writes consistent.
    // The audio thread never comes near this — it's app-side bookkeeping.
    private val lock = Any()

    // physical key -> (how many sources hold it, which engine notes it triggered)
    private class Press(var count: Int, val targets: List<Int>)

    private val presses = HashMap<Int, Press>()
    private val engineRefs = HashMap<Int, Int>()

    fun noteOn(note: Int) {
        if (note !in 0..127) return
        synchronized(lock) {
            val existing = presses[note]
            if (existing != null) {
                // Same key from a second source (touch + MIDI): no new sound.
                existing.count++
                return
            }
            // Coupler targets are decided at press time and remembered, so a
            // later coupler toggle only affects new presses.
            val targets = if (subOctaveCoupler && note >= 12) listOf(note, note - 12)
                          else listOf(note)
            presses[note] = Press(1, targets)
            for (t in targets) {
                val refs = engineRefs.merge(t, 1, Int::plus)!!
                if (refs == 1) AudioEngine.noteOn(t)
            }
            activeNotes = engineRefs.keys.toSet()
        }
    }

    fun noteOff(note: Int) {
        synchronized(lock) {
            val press = presses[note] ?: return
            if (--press.count > 0) return
            presses.remove(note)
            for (t in press.targets) {
                val refs = engineRefs.merge(t, -1, Int::plus) ?: continue
                if (refs <= 0) {
                    engineRefs.remove(t)
                    AudioEngine.noteOff(t)
                }
            }
            activeNotes = engineRefs.keys.toSet()
        }
    }

    /** Silence everything, now. MIDI CC 120/123 and a few edge cases. */
    fun panic() {
        synchronized(lock) {
            presses.clear()
            engineRefs.clear()
            activeNotes = emptySet()
        }
        AudioEngine.allNotesOff()
    }

    /** Pull or retire one stop. Stops combine, like ranks on a real organ. */
    fun toggleStop(index: Int) {
        activeStops = if (index in activeStops) activeStops - index else activeStops + index
        pushStopMask()
    }

    fun toggleTremulant() {
        tremulant = !tremulant
        AudioEngine.setTremulant(tremulant)
    }

    fun toggleSubOctaveCoupler() {
        // Takes effect on the next press; held notes keep their targets.
        subOctaveCoupler = !subOctaveCoupler
    }

    /**
     * The General Cancel piston: retires every stop and accessory and
     * silences held notes. What the old PANIC button grew up into.
     */
    fun generalCancel() {
        activeStops = emptySet()
        pushStopMask()
        if (tremulant) toggleTremulant()
        subOctaveCoupler = false
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
