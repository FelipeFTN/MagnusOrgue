package com.felipeftn.magnusorgue

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.felipeftn.magnusorgue.audio.AudioEngine
import com.felipeftn.magnusorgue.controller.OrganController
import com.felipeftn.magnusorgue.midi.MidiInputManager
import com.felipeftn.magnusorgue.settings.ConsoleState
import com.felipeftn.magnusorgue.ui.OrganScreen
import com.felipeftn.magnusorgue.ui.theme.MagnusOrgueTheme

class MainActivity : ComponentActivity() {

    private val controller = OrganController()
    private var midi: MidiInputManager? = null
    private var focusRequest: AudioFocusRequest? = null

    // The engine contract says loadRanks() must finish before start() — the
    // audio thread reads the ranks lock-free. So the ~100 MB load runs on a
    // worker thread and start() waits for `ranksLoaded` (either onStart came
    // first and left `startPending`, or it comes later and starts directly).
    // All flag access happens on the main thread; the worker only posts.
    private var ranksLoaded = false
    private var startPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // An instrument that lets the screen sleep mid-piece is useless.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Hardware volume keys should drive media volume, not ringtone.
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Restore last session's registration, pistons and volume.
        controller.attachPersistence(ConsoleState(this))

        Thread {
            AudioEngine.loadRanks(assets)
            runOnUiThread {
                ranksLoaded = true
                if (startPending) {
                    startPending = false
                    controller.audioReady = AudioEngine.start()
                }
            }
        }.start()

        midi = MidiInputManager(this, controller).also { it.start() }

        setContent {
            MagnusOrgueTheme {
                OrganScreen(
                    controller = controller,
                    onRetryAudio = { controller.audioReady = AudioEngine.start() },
                )
            }
        }
    }

    // Engine follows foreground visibility: no background audio in the MVP.
    override fun onStart() {
        super.onStart()
        requestAudioFocus()
        if (ranksLoaded) {
            controller.audioReady = AudioEngine.start()
        } else {
            startPending = true  // the loader thread will start the engine
        }
    }

    override fun onStop() {
        // If we're backgrounded while the ranks are still loading, the
        // loader must not start the engine behind our back.
        startPending = false
        // Kill notes BEFORE closing the stream, or held keys come back as
        // zombies when the stream restarts.
        controller.panic()
        AudioEngine.stop()
        abandonAudioFocus()
        super.onStop()
    }

    override fun onDestroy() {
        midi?.stop()
        super.onDestroy()
    }

    // --- Audio focus -------------------------------------------------------
    // Play nice with the rest of the system: if a call comes in or another
    // app grabs the output, we shut up. We don't auto-resume; the player
    // will just press a key again.

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            controller.panic()
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }
}
