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
  truncated right after the sustain loop. ~47 MB for four ranks.
- Each voice loops its pipe recording forever; loop points come from the
  `smpl` chunk of the original WAVs. Root pitch (with tuning fraction) too,
  so the organ keeps its real tuning.
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

- **Per layer:** nearest pipe by root note, fractional read position, linear interpolation, wrap inside the sustain loop.
- **Pitch shift:** `inc = 2^((note - rootNote) / 12) * (packRate / deviceRate)` — at most ±1 semitone of shift thanks to the minor-third pipe spacing.
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

Current registration: Principale 8', Flauto 8' (a camino), Gamba 8', Ottava 4'. Stops combine as bitmask layers; adding a stop = one importer entry + one table entry + one knob.

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
