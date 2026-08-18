# 02 — Features

## Priority legend

- **P0** — MVP, the app doesn't exist without it.
- **P1** — right after the MVP works.
- **P2** — future / nice to have.

---

## 1. On-screen keyboard

| # | Feature | Priority | Details |
|---|---|---|---|
| 1.1 | Playable keyboard (white and black keys) | P0 | Multitouch (chords with several fingers) |
| 1.2 | Full-compass monitor strip | P0 | Five octaves (C2–C7) at slim height; mirrors MIDI input, no octave shifting needed |
| 1.3 | Visual feedback on pressed keys | P0 | Key "sinks"/changes color — including when triggered by external MIDI |
| 1.4 | Landscape-only orientation | P0 | Locked via `sensorLandscape` (flippable for the OTG cable) |
| 1.5 | Glissando (sliding a finger across keys) | P1 | Previous note releases, new note sounds |
| 1.6 | Optional note labels (C4, D4...) | P1 | Toggle in settings |
| 1.7 | Adjustable key width | P2 | Zoom for larger/smaller hands |

## 2. MIDI over OTG (USB)

| # | Feature | Priority | Details |
|---|---|---|---|
| 2.1 | Automatic USB MIDI device detection | P0 | On plugging via OTG, connect with no user interaction |
| 2.2 | Note On / Note Off | P0 | Plays/releases the note in the audio engine |
| 2.3 | Connection status indicator | P0 | Icon/name of the connected device on screen |
| 2.4 | Reconnection on unplug/replug | P0 | Without restarting the app |
| 2.5 | Sustain pedal (CC 64) | P1 | Holds notes while pressed |
| 2.6 | Ignore velocity (organ mode) | P1 | Pipe organs have no per-key dynamics; on/off toggle |
| 2.7 | Listen on all MIDI channels (Omni) | P1 | Omni by default; specific channel selection as an option |
| 2.8 | Multiple simultaneous MIDI devices | P2 | E.g., two keyboards = two manuals |
| 2.9 | BLE MIDI (Bluetooth) | P2 | Out of MVP |

## 3. Audio engine / sound

| # | Feature | Priority | Details |
|---|---|---|---|
| 3.1 | Polyphonic organ sound | P0 | Minimum 16 voices; target 32 |
| 3.2 | Low latency | P0 | AAudio/Oboe in `LowLatency`/`Exclusive` mode |
| 3.3 | 2–4 stops/timbres | P0 | E.g., Principal 8', Flute 8', Strings 8', Tutti |
| 3.4 | Simple envelope (short attack/release) | P0 | Avoids clicks on note on/off |
| 3.5 | General Cancel piston | P0 | Retires all stops and silences everything, organ style |
| 3.6 | Master volume control | P0 | Slider in the app + phone hardware buttons |
| 3.7 | Simple fixed reverb | P0 | Freeverb-style room, always on — half the organ sound |
| 3.8 | Stop combination | P0 | Drawknobs toggle and stack like real ranks; no stops pulled = silence |
| 3.9 | Tremulant, historical tunings, transposition | P2 | Future |

## 4. Interface and settings

| # | Feature | Priority | Details |
|---|---|---|---|
| 4.1 | Single main screen (monitor keyboard + drawknob console) | P0 | No complex navigation in the MVP |
| 4.2 | Drawknob console: stops, General Cancel, volume, MIDI status | P0 | Everything reachable in 1 tap |
| 4.3 | Simple settings screen | P1 | Velocity on/off, labels, MIDI channel, reverb |
| 4.4 | Keep screen on while playing | P0 | `FLAG_KEEP_SCREEN_ON` |
| 4.5 | Persist last settings | P1 | DataStore/SharedPreferences |
| 4.6 | Dark theme | P1 | Dark by default (stage/church in low light) |

## 5. Quality / non-functional

- **Latency:** < 40 ms touch→sound (target < 20 ms).
- **No crackles** (clicks/pops) under dense chords.
- **No background audio** in the MVP: pause/release the stream when the app loses focus.
- **APK size:** < 60 MB (samples/SoundFont are what weighs; pick a lean SF2).
- **Compatibility:** Android 8.0 (API 26) or higher — comfortably covers the MIDI API (API 23+) and AAudio (API 26+).
- **Permissions:** no sensitive permissions; USB MIDI via `MidiManager` requires no user permission for MIDI-class devices.
