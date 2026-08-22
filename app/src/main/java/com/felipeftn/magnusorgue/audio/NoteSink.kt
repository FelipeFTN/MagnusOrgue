package com.felipeftn.magnusorgue.audio

/**
 * What the controller needs from the audio engine. Exists so the
 * controller's note bookkeeping (refcounts, coupler, sustain) can be unit
 * tested on the JVM — the real AudioEngine loads a .so, which is a
 * non-starter in local tests.
 */
interface NoteSink {
    fun noteOn(note: Int)
    fun noteOff(note: Int)
    fun allNotesOff()
    fun setStopMask(mask: Int)
    fun setTremulant(on: Boolean)
    fun setVolume(gain: Float)
}

/** The production sink: straight delegation to the native engine. */
object EngineSink : NoteSink {
    override fun noteOn(note: Int) = AudioEngine.noteOn(note)
    override fun noteOff(note: Int) = AudioEngine.noteOff(note)
    override fun allNotesOff() = AudioEngine.allNotesOff()
    override fun setStopMask(mask: Int) = AudioEngine.setStopMask(mask)
    override fun setTremulant(on: Boolean) = AudioEngine.setTremulant(on)
    override fun setVolume(gain: Float) = AudioEngine.setVolume(gain)
}
