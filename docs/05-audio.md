# 05 — Audio Engine

## Goal

Polyphonic organ sound with **touch-to-sound latency under 40 ms** (target < 20 ms), no clicks or dropouts with 16–32 simultaneous voices.

## Synthesis approach

The MVP shipped with **additive synthesis** (Option A below) — zero assets,
great latency, but ultimately it sounded electronic ("like a horn", per the
first field test). The engine now plays **sampled pipes** (Option B),
imported from the Giubiasco GrandOrgue set with `tools/import_ranks.py`:

- One `.mrk` pack per stop: mono 16-bit attack samples, one pipe per minor
  third (the engine pitch-shifts at most ±1 semitone to the nearest pipe),
  truncated right after the sustain loop. ~104 MB for eleven ranks.
- Each voice loops its pipe recording forever; loop points come from the
  `smpl` chunk of the original WAVs.
- Pipes are keyed by **keyboard position** (the filename number), not by
  their true pitch, and shifted chromatically. That preserves the organ's
  real temperament, the Voce Umana's celeste detune and the 16'/4'/2'
  footages — correcting to true pitch (the v1 mistake) quietly erased all
  three. A rank outside its compass simply doesn't speak (`nearestPipe`
  returns null beyond ~2 semitones), which is what gates the pedal ranks
  and the bass-less celeste for free.
- A stop is a rank; pulled stops = one sample layer each per voice.
- The packs stay out of git (the samples belong to the set's author);
  anyone building the app runs the importer against their own copy.
- Release samples (R0..R3 in the set) are not imported yet — a ~140 ms
  envelope release fakes the pipe closing. TODO, they'd double the assets.

## Output: Oboe

[Oboe](https://github.com/google/oboe) is Google's C++ library that picks AAudio (API 27+) or OpenSL ES automatically.

Stream configuration:

```cpp
oboe::AudioStreamBuilder builder;
builder.setDirection(oboe::Direction::Output)
       ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
       ->setSharingMode(oboe::SharingMode::Exclusive)
       ->setFormat(oboe::AudioFormat::Float)
       ->setChannelCount(2)
       ->setSampleRate(48000)            // negotiate; use device native rate
       ->setCallback(audioCallback);
```

- Use the device's **native sample rate and optimal buffer size** (`getFramesPerBurst`) to hit the fast path.
- Handle `onErrorAfterClose` → recreate the stream (device change, headphones, etc.).

## Engine structure

```
onAudioReady(float* output, int numFrames):        // real-time thread
    drain event queue (noteOn/noteOff/setStop/…)   // lock-free reads only
    for each active Voice:
        voice.render(mixBuffer, numFrames)
    apply master gain (smoothed)
    write to output
```

### Voice

One playing note: up to one sample layer per pulled stop, all summed.

- **Per layer:** nearest pipe by keyboard key, fractional read position, linear interpolation, wrap inside the sustain loop.
- **Pitch shift:** `inc = 2^((note - keyNote) / 12) * (packRate / deviceRate)` — at most ±1 semitone of shift thanks to the minor-third pipe spacing.
- **Envelope:** a de-clicker only. Attack ~4 ms (the recording carries the real pipe speech); release ~140 ms fakes the pipe closing until release samples land.

### VoiceManager (polyphony)

- Fixed pool of 32 voices (no allocation on the audio thread).
- `noteOn`: reuse a voice already playing the same note (retrigger) → else grab a free voice → else **steal** the voice in release stage the longest / oldest note.
- `noteOff`: put matching voice(s) into release stage.
- `allNotesOff`: hard-release everything (panic uses a very short release, not a hard cut, to avoid a click).

### Stops (ranks)

A stop is a pointer to its rank pack:

```cpp
struct StopDefinition {
    const char* name;       // "Principale 8'"
    const char* assetPath;  // "ranks/principale8.mrk"
};
```

Current registration — Manuale: Principale 8', Voce Umana 8' (celeste), Flauto 8' (a camino), Gamba 8', Ottava 4', Flauto Conico 4', Quintadecima 2', Regale 8' (reed). Pedale: Subbasso 16', Flauto 8', Contro Fagotto 16' (reed). Stops combine as bitmask layers; adding a stop = one importer entry + one table entry + one knob.

### Accessories (not stops)

- **Tremulant:** a ~5.5 Hz, shallow amplitude LFO on the whole organ, applied before the reverb (the wind wobbles, the room doesn't), with an eased depth so toggling mid-chord doesn't step.
- **Ottava Bassa (sub-octave coupler):** lives in the Kotlin controller, not the engine — each key press also triggers its lower octave. Engine notes are reference-counted there so coupled/doubled presses release cleanly.

## Rules of the real-time thread

1. **No allocations, no locks, no JNI calls, no logging** inside `onAudioReady`.
2. Events arrive through a **single-producer/single-consumer lock-free ring buffer** (producer: any app thread via one mutex-guarded JNI entry point that only enqueues; consumer: audio thread).
3. Parameter changes (volume, stop) are **smoothed** over a few ms to avoid zipper noise/clicks.
4. Denormal protection (flush-to-zero) enabled on the audio thread.

## Latency budget (approximate)

| Stage | Typical |
|---|---|
| Touch input → app event | 10–20 ms (screen scanning; MIDI input is faster: 1–3 ms) |
| Event queue → next audio callback | 0–5 ms |
| Audio buffer (2 bursts × ~96–240 frames @ 48 kHz) | 4–10 ms |
| DAC/output path | 2–10 ms |

Net effect: MIDI-triggered notes will feel noticeably tighter than touch-triggered ones — that's expected and fine (the OTG keyboard is the primary "instrument-grade" input).

## Volume & audio focus

- Master gain slider (0–1, applied with smoothing) + hardware volume keys control `STREAM_MUSIC`.
- Request **audio focus** on start; on loss (call, another app), `allNotesOff()` and pause the stream.
