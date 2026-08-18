package com.felipeftn.magnusorgue.audio

/**
 * Kotlin face of the native audio engine (see src/main/cpp).
 *
 * Every call here is fire-and-forget and safe from any thread: the native
 * side just drops an event into a lock-free queue that the audio callback
 * drains. Nothing blocks, nothing waits.
 */
object AudioEngine {

    init {
        System.loadLibrary("magnusorgue")
    }

    /** Opens and starts the output stream. Returns false if the device said no. */
    external fun start(): Boolean

    /** Stops and closes the stream. Safe to call start() again afterwards. */
    external fun stop()

    external fun noteOn(note: Int)
    external fun noteOff(note: Int)

    /**
     * Pulled stops as a bitmask (bit N = stop N in the native kStops order).
     * Zero means no stops — which, like on a real organ, means silence.
     */
    external fun setStopMask(mask: Int)

    /** Master gain, 0.0..1.0. Smoothed on the native side. */
    external fun setVolume(gain: Float)

    /** Panic: fast-fades every sounding voice. */
    external fun allNotesOff()
}
