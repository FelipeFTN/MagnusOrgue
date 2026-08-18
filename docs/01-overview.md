# 01 — Overview

## What it is

MagnusOrgue is an Android app that turns the phone into a **simple virtual organ**:

1. A playable organ keyboard on the phone screen.
2. Support for **any MIDI keyboard/controller** connected via an **OTG (USB) cable**, playing the same organ sound.

The focus is **simplicity and low latency** — this is not a DAW nor a complex synthesizer. It's "plug and play".

**Android package name:** `com.felipeftn.magnusorgue`.

## Who it's for

- Musicians/organists who want a portable practice instrument.
- Anyone with a "mute" MIDI keyboard (a controller with no built-in sound) who wants to use it with organ timbres.
- Honestly, first and foremost: myself. This starts as a personal instrument, so it has to be trivial to sideload and test on my own phone with my own controller.

## MVP scope (what the app DOES in v1)

- On-screen keyboard (1–2 visible octaves, with octave shift).
- Reasonably good polyphonic organ sound (additive synthesis, see [05-audio.md](05-audio.md)).
- Automatic detection of a USB MIDI device connected via OTG.
- MIDI Note On / Note Off play the sound (velocity optional — real organs don't have it).
- Panic button (silence all notes).
- Selection of 2–4 basic stops (e.g., Principal 8', Flute 8', Tutti).

## Out of MVP scope (explicitly)

- MIDI or audio recording/playback.
- Bluetooth MIDI (BLE MIDI) — deferred to v2.
- Advanced effects (configurable reverb, EQ) — at most a simple fixed reverb in the MVP.
- Pedalboard, multiple manuals, couplers — full-organ features come later.
- iOS support.

## MVP success criteria

1. Touch-to-sound latency **< 40 ms** on a reasonable Android phone (ideally < 20 ms with AAudio/Oboe).
2. A MIDI keyboard plugged in via OTG works **with zero configuration** (plug and play).
3. Minimum polyphony of 16 simultaneous notes without crackles (ideally 32+).
4. APK installable directly via `adb install` or file transfer.
