# 07 — Roadmap

Each phase ends with something **runnable on your phone**. No phase depends on future phases to be testable.

---

## Phase 0 — Project skeleton (½ day)

- Create the Android project: Kotlin, Compose, min SDK 26, package `com.felipeftn.magnusorgue`.
- Add NDK/CMake setup and the Oboe dependency; a native lib that just logs "hello".
- Empty main screen with the app name.
- **Done when:** `./gradlew installDebug` puts a launchable app on the phone.

## Phase 1 — Audio engine core (1–2 days)

- Oboe stream (LowLatency/Exclusive, float, native rate).
- `Voice` (sine only, fixed pitch) + envelope; `VoiceManager` with the 32-voice pool.
- Lock-free event queue + the 7-function JNI bridge.
- Temporary debug button in the UI: press = noteOn(60), release = noteOff(60).
- **Done when:** pressing the debug button produces a clean, click-free tone with obviously low latency.

## Phase 2 — On-screen keyboard (1–2 days)

- `KeyboardView` in Compose Canvas: layout, hit testing, multitouch.
- `OrganController` wiring touch → engine; pressed-key highlighting.
- Octave shift buttons; landscape orientation; keep-screen-on.
- **Done when:** you can play chords on the screen with both hands, no stuck notes, no clicks.

## Phase 3 — MIDI over OTG (1–2 days)

- `MidiInputManager`: enumerate devices, hotplug callback, open output port.
- `MidiMessageParser` (+ unit tests: velocity-0, running status, multi-message packets).
- Note events → controller → engine; MIDI-pressed keys light up on screen.
- Status chip in the top bar; release device's notes on unplug.
- **Done when:** plugging your MIDI keyboard via OTG plays notes with zero configuration, and unplug/replug works. **This is the heart of the app — test hard here** (see [08-testing.md](08-testing.md)).

## Phase 4 — Organ voicing & stops (1–2 days)

- Additive harmonics in `Voice`; define the 4 stops (Principal 8', Flute 8', Strings 8', Tutti).
- Stop selector in the top bar; master volume slider with smoothing.
- Panic button; audio focus handling; pause/resume engine with Activity lifecycle.
- **Done when:** the 4 stops sound distinct and organ-like; 10-finger chords stay clean.

## Phase 5 — MVP polish (1 day)

- App icon, dark theme colors, MIDI-connected snackbar.
- Error banner for audio-start failure; edge cases from [03-architecture.md](03-architecture.md).
- Release build config (minify, signing for personal use).
- **Done when:** a signed release APK you'd happily keep on your phone. 🎉 **MVP shipped.**

---

## Post-MVP (P1) — in rough order

1. Sustain pedal (CC 64).
2. Settings screen + persistence (DataStore): velocity toggle, labels, channel, reverb.
3. Simple fixed reverb (e.g., Freeverb port in the native layer).
4. Glissando on the touch keyboard; note labels.

## Future (P2) — ideas parking lot

- Sample-based voices (swap `Voice` implementation) / SoundFont support.
- Stop combination (multiple stops at once, drawbar-style).
- Multiple MIDI devices as multiple manuals; BLE MIDI.
- Foreground service for background audio; transposition; tremulant; historical temperaments.
- Play Store release (privacy policy, store listing, screenshots).

## Estimated total for MVP

**~6–9 working days** of focused effort (phases 0–5), heavily dependent on how much latency/device tuning Phase 1 needs.
