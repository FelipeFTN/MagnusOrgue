# 04 — MIDI over OTG

## How USB MIDI on Android works

1. The user connects a MIDI keyboard to the phone with a **USB OTG cable/adapter** (USB-C or micro-USB on the phone side, USB-B/USB-A on the keyboard side).
2. Virtually every modern MIDI keyboard is a **USB MIDI class-compliant device** — no drivers needed.
3. Android exposes it through **`android.media.midi.MidiManager`** (API 23+). For class-compliant devices, **no runtime permission dialog** is shown.
4. The phone acts as **USB host** and typically powers the keyboard through the cable (some larger keyboards need their own power supply — worth mentioning in the app's help text).

> Alternative not used: the `usb-midi-driver` style raw `UsbManager` approach. Only needed for non-class-compliant devices; out of scope.

## Connection flow (what the app does)

```
App start
  │
  ├─► midiManager.devices  ──► any MIDI device already plugged?
  │                              └─► yes: openDevice() → connect input
  │
  └─► registerDeviceCallback(...)
        ├─► onDeviceAdded(device)   → openDevice() → connect
        └─► onDeviceRemoved(device) → release ports, allNotesOff(),
                                      update status UI
```

Details:

- `MidiManager.openDevice(deviceInfo, listener, handler)` is **asynchronous** — the listener receives the opened `MidiDevice`.
- We read from the device's **output port** (`device.openOutputPort(0)` — "output" from the device's perspective = notes coming *out* of the keyboard *into* the phone) and attach our `MidiReceiver`.
- Status shown in the UI: device product name from `MidiDeviceInfo.getProperties()`.

## Message parsing

`MidiReceiver.onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long)` delivers raw MIDI bytes. The parser must handle:

| Message | Status byte | MVP action |
|---|---|---|
| Note On | `0x9n` (n = channel) | `noteOn(note)`; **Note On with velocity 0 = Note Off** (very common!) |
| Note Off | `0x8n` | `noteOff(note)` |
| Control Change 64 (sustain) | `0xBn`, data1=64 | P1: hold notes while value ≥ 64 |
| Control Change 120/123 (all sound/notes off) | `0xBn` | `allNotesOff()` |
| Everything else (pitch bend, aftertouch, program change, SysEx, clock) | — | Ignore silently in the MVP |

Parser must also handle:

- **Running status** (status byte omitted on repeated messages) — some devices use it.
- **Multiple messages in one packet** — `onSend` can deliver several MIDI messages in a single byte array.
- **Channel handling:** Omni mode (accept all 16 channels) by default.

## Data classes (Kotlin sketch)

```kotlin
sealed interface MidiEvent {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : MidiEvent
    data class NoteOff(val channel: Int, val note: Int) : MidiEvent
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : MidiEvent
}
```

`MidiMessageParser` is a pure function `ByteArray → List<MidiEvent>` — trivially unit-testable without a device.

## Latency considerations

- MIDI input latency over USB is small (~1–3 ms); the dominant latency is **audio output** (see [05-audio.md](05-audio.md)).
- Forward events to the audio engine **directly from the MIDI callback thread** (via the lock-free queue) — do not bounce through the main thread. UI highlighting can be updated asynchronously afterward.

## Manifest / hardware declarations

```xml
<uses-feature android:name="android.software.midi" android:required="false"/>
<uses-feature android:name="android.hardware.usb.host" android:required="false"/>
```

Both `required="false"` so the app still installs on devices without USB host — the on-screen keyboard works everywhere.

## Testing MIDI without a physical keyboard

- **Unit tests:** feed byte arrays to `MidiMessageParser` (covers running status, velocity-0, multi-message packets).
- **On-device without hardware:** apps like *MIDI Keyboard* + Android's virtual MIDI, or `adb` + a second app publishing a `MidiDeviceService`. Simplest realistic test is the real keyboard via OTG — see [08-testing.md](08-testing.md).
