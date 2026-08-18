# 08 — Testing on your Android phone

## One-time phone setup

1. **Enable Developer Options:** Settings → About phone → tap "Build number" 7 times.
2. **Enable USB debugging:** Settings → Developer options → USB debugging.
3. Connect the phone to the computer via USB and accept the "Allow USB debugging?" prompt.
4. Verify from the computer:
   ```bash
   adb devices        # phone must appear as "device"
   ```

> On Linux (CachyOS/Arch): if the phone shows as `unauthorized`/missing, install `android-udev` (udev rules) and re-plug.

## Everyday dev loop

```bash
./gradlew installDebug        # build + install on the connected phone
adb logcat -s MagnusOrgue     # watch the app's logs (use one shared tag)
```

Or simply the ▶ Run button in Android Studio. **Wireless debugging** (Developer options → Wireless debugging → `adb pair`) is very handy for this project because **the USB port will be busy with the OTG MIDI cable** — you can't have the computer and the keyboard on the same port at once.

## Testing MIDI with the real keyboard

Recommended setup:

1. Pair the phone with the computer over **Wi-Fi adb** (see above), so USB is free.
2. Plug the MIDI keyboard into the phone via the OTG cable.
3. Launch the app; the status chip should turn green with the device name.
4. Play — watch keys light up on screen and `adb logcat` for parsed events.

Checklist for Phase 3 acceptance:

- [ ] Device detected when plugged **before** app launch.
- [ ] Device detected when plugged **while** app is open (hotplug).
- [ ] Unplug mid-chord → notes release, chip goes gray, no crash.
- [ ] Replug → works again without app restart.
- [ ] Fast repeated notes and big chords → no stuck notes.
- [ ] Keyboard that sends Note On velocity 0 as Note Off → handled.
- [ ] Phone in battery saver / screen rotated mid-note → nothing breaks.

## Testing MIDI *without* a physical keyboard

- **Parser unit tests** (run on the computer, no device): feed crafted byte arrays into `MidiMessageParser` — the highest-value tests in the project.
- **Virtual MIDI apps:** e.g., "MIDI Keyboard" apps that expose a virtual MIDI output; Android routes them through the same `MidiManager`, so most of the pipeline is exercised.

## Latency testing (practical, no lab gear)

1. **Ear test:** play staccato repeated notes from the MIDI keyboard — delay should be imperceptible (<20 ms) or barely there (<40 ms).
2. **Slow-mo video:** record phone screen + your finger at 240 fps with another phone; count frames between key contact and sound (use a percussive attack setting for measurement).
3. **Oboe diagnostics:** log `stream->getFramesPerBurst()`, buffer size, and whether `PerformanceMode::LowLatency` + `SharingMode::Exclusive` were actually granted (devices may silently downgrade — log it!).

## Audio quality checklist

- [ ] 10-note chord: no crackles, no dropouts (watch logcat for Oboe xruns).
- [ ] Note on/off has no click (envelope working).
- [ ] Volume slider produces no zipper noise.
- [ ] Panic during a big chord: fast fade, not a pop.
- [ ] Bluetooth headphones: works (latency will be bad — that's BT, not us; wired/speaker is the reference).
- [ ] Phone call arrives while playing → audio yields, app recovers afterward.

## Automated tests (scope for this project)

| Layer | Test type | Priority |
|---|---|---|
| `MidiMessageParser` | JVM unit tests | **High** — pure function, cheap, catches real-device quirks |
| `VoiceManager` (C++) | Native unit tests (voice stealing, note tracking) | Medium |
| `OrganController` state | JVM unit tests with fake engine | Medium |
| UI / keyboard hit testing | Compose UI tests | Low — manual testing covers it for an app this size |

## Installing on the phone without a computer (sharing builds)

- Build a release APK: `./gradlew assembleRelease` (signed with a personal keystore).
- Transfer the APK (Drive, USB, etc.) and open it on the phone; allow "install from unknown sources" for the file manager.
- No Play Store needed for personal use.
