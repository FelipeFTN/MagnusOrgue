package com.felipeftn.magnusorgue.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.felipeftn.magnusorgue.audio.EngineSink
import com.felipeftn.magnusorgue.audio.NoteSink
import com.felipeftn.magnusorgue.settings.ConsoleState

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
class OrganController(private val engine: NoteSink = EngineSink) {

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

    /** Transposition in semitones (-6..+6). Applied to every input. */
    var transpose by mutableStateOf(0)
        private set

    var volume by mutableStateOf(0.8f)
        private set

    /** Reverb amount 0..1 — how much cathedral the room answers with. */
    var reverb by mutableStateOf(0.6f)
        private set

    /** Product name of the connected MIDI device, or null. */
    var midiDeviceName by mutableStateOf<String?>(null)

    /** False when the audio stream refused to open — shows the error banner. */
    var audioReady by mutableStateOf(true)

    /** Combination pistons: the saved stop-mask of each, -1 = empty. */
    var pistons by mutableStateOf(List(PISTON_COUNT) { -1 })
        private set

    // Set once from MainActivity; null in previews/tests.
    private var persisted: ConsoleState? = null

    /** Restores the last session's console and wires up persistence. */
    fun attachPersistence(state: ConsoleState) {
        persisted = state
        activeStops = maskToSet(state.stopMask)
        tremulant = state.tremulant
        subOctaveCoupler = state.subOctaveCoupler
        transpose = state.transpose
        volume = state.volume
        reverb = state.reverb
        pistons = List(PISTON_COUNT) { state.piston(it) }
        // Push the restored console into the engine.
        pushStopMask()
        engine.setTremulant(tremulant)
        engine.setVolume(volume)
        engine.setReverb(reverb)
    }

    // Events arrive concurrently from the UI thread and the MIDI callback
    // thread; one lock keeps the maps and the snapshot writes consistent.
    // The audio thread never comes near this — it's app-side bookkeeping.
    private val lock = Any()

    // physical key -> (how many sources hold it, which engine notes it triggered)
    private class Press(var count: Int, val targets: List<Int>)

    private val presses = HashMap<Int, Press>()
    private val engineRefs = HashMap<Int, Int>()

    // Sustain pedal (MIDI CC 64): while down, released keys go here instead
    // of actually releasing; pedal-up flushes them. Guarded by `lock`.
    private var sustainDown = false
    private val sustained = mutableListOf<Press>()

    fun noteOn(note: Int) {
        if (note !in 0..127) return
        synchronized(lock) {
            val existing = presses[note]
            if (existing != null) {
                // Same key from a second source (touch + MIDI): no new sound.
                existing.count++
                return
            }
            // Transpose and coupler targets are decided at press time and
            // remembered, so changing either mid-chord only affects new
            // presses — held keys still release exactly what they started.
            val base = note + transpose
            if (base !in 0..127) return
            val targets = if (subOctaveCoupler && base >= 12) listOf(base, base - 12)
                          else listOf(base)
            presses[note] = Press(1, targets)
            for (t in targets) {
                val refs = engineRefs.merge(t, 1, Int::plus)!!
                if (refs == 1) engine.noteOn(t)
            }
            activeNotes = engineRefs.keys.toSet()
        }
    }

    fun noteOff(note: Int) {
        synchronized(lock) {
            val press = presses[note] ?: return
            if (--press.count > 0) return
            presses.remove(note)
            if (sustainDown) {
                // Key up, pedal down: the pipes keep speaking until the
                // pedal lifts. (Purists: yes, tracker organs have no
                // sustain pedal. MIDI keyboards do, and it's handy.)
                sustained += press
                return
            }
            releaseTargets(press)
            activeNotes = engineRefs.keys.toSet()
        }
    }

    /** MIDI CC 64. Values >= 64 mean pedal down, per the MIDI spec. */
    fun sustain(down: Boolean) {
        synchronized(lock) {
            sustainDown = down
            if (!down) {
                sustained.forEach(::releaseTargets)
                sustained.clear()
                activeNotes = engineRefs.keys.toSet()
            }
        }
    }

    // Must be called with `lock` held.
    private fun releaseTargets(press: Press) {
        for (t in press.targets) {
            val refs = engineRefs.merge(t, -1, Int::plus) ?: continue
            if (refs <= 0) {
                engineRefs.remove(t)
                engine.noteOff(t)
            }
        }
    }

    /** Silence everything, now. MIDI CC 120/123 and a few edge cases. */
    fun panic() {
        synchronized(lock) {
            presses.clear()
            engineRefs.clear()
            sustained.clear()
            activeNotes = emptySet()
        }
        engine.allNotesOff()
    }

    /** Pull or retire one stop. Stops combine, like ranks on a real organ. */
    fun toggleStop(index: Int) {
        activeStops = if (index in activeStops) activeStops - index else activeStops + index
        pushStopMask()
    }

    fun toggleTremulant() {
        tremulant = !tremulant
        engine.setTremulant(tremulant)
        persisted?.tremulant = tremulant
    }

    fun toggleSubOctaveCoupler() {
        // Takes effect on the next press; held notes keep their targets.
        subOctaveCoupler = !subOctaveCoupler
        persisted?.subOctaveCoupler = subOctaveCoupler
    }

    /** Shift the whole organ by semitones, clamped to a fourth either way. */
    fun changeTranspose(delta: Int) {
        transpose = (transpose + delta).coerceIn(-6, 6)
        persisted?.transpose = transpose
    }

    /**
     * Combination piston, console rules: short press applies the stored
     * registration, long press captures the current one. Applying replaces
     * the whole registration (a "general" piston, not additive).
     */
    fun pressPiston(index: Int) {
        val mask = pistons.getOrNull(index) ?: return
        if (mask < 0) return  // empty piston: nothing to apply
        activeStops = maskToSet(mask)
        pushStopMask()
    }

    fun storePiston(index: Int) {
        val mask = setToMask(activeStops)
        pistons = pistons.toMutableList().also { it[index] = mask }
        persisted?.setPiston(index, mask)
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
        persisted?.subOctaveCoupler = false
        if (transpose != 0) changeTranspose(-transpose)
        panic()
    }

    private fun pushStopMask() {
        val mask = setToMask(activeStops)
        engine.setStopMask(mask)
        persisted?.stopMask = mask
    }

    // Not setVolume(): the `volume` property's generated setter already
    // claims that JVM signature and Kotlin refuses the clash.
    fun changeVolume(value: Float) {
        volume = value
        engine.setVolume(value)
        persisted?.volume = value
    }

    fun changeReverb(value: Float) {
        reverb = value
        engine.setReverb(value)
        persisted?.reverb = value
    }

    private fun setToMask(stops: Set<Int>) = stops.fold(0) { mask, i -> mask or (1 shl i) }

    private fun maskToSet(mask: Int) = (0 until 32).filter { mask and (1 shl it) != 0 }.toSet()

    companion object {
        const val PISTON_COUNT = 4
    }
}
