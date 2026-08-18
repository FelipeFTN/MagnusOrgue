# 03 — Architecture

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin** | Standard for modern Android; concise, safe |
| UI | **Jetpack Compose** | Fast to build a custom keyboard view with `Canvas` + pointer input; single-screen app fits perfectly |
| MIDI | **`android.media.midi` (MidiManager)** | Built into Android since API 23; handles USB MIDI-class devices with zero permissions |
| Audio | **Oboe (C++) via NDK** | Google's low-latency audio library (wraps AAudio/OpenSL ES); the only realistic way to hit < 20 ms |
| Synthesis | **Sample playback of organ waveforms** (see [05-audio.md](05-audio.md)) | Simplest path to a convincing organ; alternative: FluidSynth + SF2 |
| Build | Gradle (Kotlin DSL) + CMake for the native part | Standard Android NDK setup |
| Min SDK | 26 (Android 8.0) | AAudio available; covers ~95%+ of active devices |

**Note on the native layer:** low latency demands audio rendering in C++ (Oboe). The rest of the app (UI, MIDI plumbing, settings) stays in Kotlin. The boundary is a thin JNI interface (~6 functions).

## High-level architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI (Compose)                      │
│  KeyboardView · TopBar (stops, octave, volume,       │
│  MIDI status, panic) · SettingsScreen                │
└──────────────┬───────────────────────────▲──────────┘
               │ noteOn/noteOff/setStop     │ state (StateFlow)
┌──────────────▼───────────────────────────┴──────────┐
│                 OrganController (Kotlin)             │
│  Single entry point for note events. Merges input   │
│  from touch and MIDI. Holds app state (active notes,│
│  current stop, octave, volume).                     │
└───────▲──────────────────────────┬──────────────────┘
        │ MIDI events              │ JNI calls
┌───────┴────────────┐   ┌─────────▼────────────────┐
│  MidiInputManager  │   │  AudioEngine (C++/Oboe)  │
│  (Kotlin)          │   │  Voice allocator,        │
│  MidiManager,      │   │  sample playback, mixer, │
│  device hotplug,   │   │  envelope, master gain   │
│  MIDI msg parsing  │   │  Real-time audio thread  │
└────────────────────┘   └──────────────────────────┘
```

### Key design decisions

1. **One event funnel.** Touch and MIDI both call the same `OrganController.noteOn(note)` / `noteOff(note)`. The audio engine never knows where a note came from. This also lets the UI highlight keys played from the external keyboard (feature 1.3).

2. **Real-time audio thread is sacred.** The Oboe callback must never allocate, lock, or log. Note events cross from Kotlin to the audio thread through a **lock-free queue** (single-producer/single-consumer ring buffer) written via JNI.

3. **State down, events up.** Compose UI observes a `StateFlow<OrganUiState>` (active notes, connected MIDI device name, current stop, octave, volume). All mutations go through the controller.

4. **No Service in the MVP.** Audio runs only while the Activity is in the foreground. `onStop()` → pause stream; `onStart()` → resume. (A foreground service for background play is a P2 item.)

## Module / package structure

Single Gradle module (`app`) is enough for this size. Package layout:

```
app/
├── src/main/
│   ├── java/com/felipeftn/magnusorgue/
│   │   ├── MainActivity.kt
│   │   ├── controller/
│   │   │   └── OrganController.kt        # central note/state hub
│   │   ├── midi/
│   │   │   ├── MidiInputManager.kt       # MidiManager wiring, hotplug
│   │   │   └── MidiMessageParser.kt      # raw bytes → NoteOn/NoteOff/CC
│   │   ├── audio/
│   │   │   └── AudioEngine.kt            # JNI bridge (external funs)
│   │   ├── ui/
│   │   │   ├── OrganScreen.kt            # main screen scaffold
│   │   │   ├── KeyboardView.kt           # Canvas keyboard + multitouch
│   │   │   ├── TopBar.kt
│   │   │   └── theme/
│   │   └── settings/
│   │       └── SettingsRepository.kt     # DataStore
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── jni_bridge.cpp                # JNI entry points
│   │   ├── AudioEngine.{h,cpp}           # Oboe stream + callback
│   │   ├── VoiceManager.{h,cpp}          # polyphony, voice stealing
│   │   ├── Voice.{h,cpp}                 # one playing note (osc/sample + env)
│   │   └── EventQueue.h                  # lock-free SPSC ring buffer
│   └── assets/
│       └── (organ samples or .sf2 file)
└── build.gradle.kts
```

## JNI interface (the whole native API)

```kotlin
object AudioEngine {
    external fun start(): Boolean       // open + start Oboe stream
    external fun stop()                 // stop + close stream
    external fun noteOn(note: Int)      // MIDI note number 0..127
    external fun noteOff(note: Int)
    external fun setStop(stopId: Int)   // select timbre/registration
    external fun setVolume(gain: Float) // 0.0..1.0
    external fun allNotesOff()          // panic
}
```

Deliberately tiny. Everything else lives on one side or the other.

## Threading model

| Thread | Owns | Notes |
|---|---|---|
| Main (UI) | Compose, touch input, controller state | Calls JNI (non-blocking enqueue) |
| MIDI callback thread | `MidiReceiver.onSend()` | Parses bytes, forwards to controller (thread-safe) |
| Oboe audio thread | Sample rendering | Reads event queue, renders buffer; never blocks |

## Error handling & edge cases

- **Audio stream disconnect** (headphones plugged/unplugged, device change): Oboe reports stream disconnected → recreate stream automatically.
- **MIDI device unplugged mid-note:** controller releases all notes originated from that device.
- **App backgrounded with notes held:** `allNotesOff()` before pausing the stream.
- **Unsupported USB device:** if `MidiManager` doesn't list it, show a friendly hint ("device not recognized as MIDI class").
