package com.felipeftn.magnusorgue.controller

import com.felipeftn.magnusorgue.audio.NoteSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller's note bookkeeping is the subtlest logic in the app —
 * coupler and sustain both multiply/hold notes, and any refcount slip
 * means a pipe droning until someone finds General Cancel. These tests
 * poke exactly those corners.
 */
class OrganControllerTest {

    /** Records engine calls; sounding = notes currently on. */
    private class FakeEngine : NoteSink {
        val sounding = mutableSetOf<Int>()
        var allOffCalls = 0
        override fun noteOn(note: Int) { sounding += note }
        override fun noteOff(note: Int) { sounding -= note }
        override fun allNotesOff() { allOffCalls++; sounding.clear() }
        override fun setStopMask(mask: Int) {}
        override fun setTremulant(on: Boolean) {}
        override fun setVolume(gain: Float) {}
    }

    private val engine = FakeEngine()
    private val organ = OrganController(engine)

    @Test
    fun `plain press and release`() {
        organ.noteOn(60)
        assertEquals(setOf(60), engine.sounding)
        organ.noteOff(60)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `same key from touch and midi releases once both let go`() {
        organ.noteOn(60)  // touch
        organ.noteOn(60)  // midi, same key
        organ.noteOff(60)
        assertEquals(setOf(60), engine.sounding)  // still held by one source
        organ.noteOff(60)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `coupler doubles the note an octave down`() {
        organ.toggleSubOctaveCoupler()
        organ.noteOn(60)
        assertEquals(setOf(60, 48), engine.sounding)
        organ.noteOff(60)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `toggling the coupler off mid-press cannot strand the coupled note`() {
        organ.toggleSubOctaveCoupler()
        organ.noteOn(60)
        organ.toggleSubOctaveCoupler()  // off while the key is down
        organ.noteOff(60)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `coupled and direct presses on the same engine note share a refcount`() {
        organ.toggleSubOctaveCoupler()
        organ.noteOn(60)  // sounds 60 and 48
        organ.noteOn(48)  // 48 again, directly (plus its own 36)
        organ.noteOff(60) // must NOT kill 48 — the direct press still holds it
        assertEquals(setOf(48, 36), engine.sounding)
        organ.noteOff(48)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `sustain holds released keys until pedal up`() {
        organ.sustain(true)
        organ.noteOn(60)
        organ.noteOff(60)
        assertEquals(setOf(60), engine.sounding)  // pedal is holding it
        organ.sustain(false)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `re-striking a sustained key does not double the refcount`() {
        organ.sustain(true)
        organ.noteOn(60)
        organ.noteOff(60)   // parked in the sustain list
        organ.noteOn(60)    // struck again while still sounding
        organ.sustain(false) // flushes the parked press
        assertEquals(setOf(60), engine.sounding)  // the live press remains
        organ.noteOff(60)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `transpose shifts new notes and release still matches`() {
        organ.changeTranspose(+2)
        organ.noteOn(60)
        assertEquals(setOf(62), engine.sounding)
        organ.changeTranspose(-2)  // back to 0 while the key is down
        organ.noteOff(60)          // must release 62, not 60
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `transpose clamps at a fourth`() {
        repeat(12) { organ.changeTranspose(+1) }
        assertEquals(6, organ.transpose)
    }

    @Test
    fun `panic clears everything including sustained notes`() {
        organ.sustain(true)
        organ.noteOn(60)
        organ.noteOff(60)
        organ.panic()
        assertTrue(engine.sounding.isEmpty())
        assertEquals(1, engine.allOffCalls)
        // and the sustain list is really gone: pedal-up must not re-release
        organ.sustain(false)
        assertTrue(engine.sounding.isEmpty())
    }

    @Test
    fun `pistons store and recall a registration`() {
        organ.toggleStop(2)
        organ.toggleStop(5)          // now {0, 2, 5}
        organ.storePiston(0)
        organ.generalCancel()        // wipes the registration
        assertTrue(organ.activeStops.isEmpty())
        organ.pressPiston(0)
        assertEquals(setOf(0, 2, 5), organ.activeStops)
    }

    @Test
    fun `empty piston does nothing`() {
        organ.toggleStop(3)
        organ.pressPiston(2)  // never stored
        assertEquals(setOf(0, 3), organ.activeStops)
    }
}
