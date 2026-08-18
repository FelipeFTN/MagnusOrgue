#include "Voice.h"

#include <cmath>

namespace {
// Shared sine wavetable. One extra guard sample at the end lets the linear
// interpolation read table[i + 1] without a wrap check in the hot loop.
float gSine[2048 + 1];
bool gTableReady = false;
}  // namespace

void Voice::initWavetable() {
    if (gTableReady) return;
    for (int i = 0; i <= kTableSize; ++i) {
        gSine[i] = std::sin(2.0 * M_PI * i / kTableSize);
    }
    gTableReady = true;
}

void Voice::start(int note, uint32_t stopMask, float sampleRate, uint32_t age) {
    note_ = note;
    age_ = age;
    sampleRate_ = sampleRate;

    // Merge the recipes of every pulled stop, exactly like stacking pipe
    // ranks. Attack follows the fastest stop (the quickest pipe defines
    // when you hear something), release the slowest.
    float merged[kMaxHarmonics] = {0.0f};
    float attackMs = 1000.0f;
    float releaseMs = 0.0f;
    for (int s = 0; s < kStopCount; ++s) {
        if ((stopMask & (1u << s)) == 0) continue;
        const StopDefinition& stop = kStops[s];
        for (int h = 0; h < kMaxHarmonics; ++h) merged[h] += stop.harmonics[h];
        if (stop.attackMs < attackMs) attackMs = stop.attackMs;
        if (stop.releaseMs > releaseMs) releaseMs = stop.releaseMs;
    }
    if (releaseMs <= 0.0f) {  // no stops pulled: a real organ goes silent too
        active_ = false;
        return;
    }

    // Equal temperament, A4 = 440 Hz = MIDI 69.
    const float freq = 440.0f * std::exp2((note - 69) / 12.0f);

    // Pack only the harmonics we'll actually hear. Anything at or above
    // ~0.45 * sampleRate gets dropped — pushing partials past Nyquist gives
    // aliasing, which on an organ sounds like a broken AM radio.
    // Cheap LCG for phases/detune. Only ever touched from the audio thread
    // (start() runs inside the event drain), so a plain static is fine.
    static uint32_t rng = 0x6f726775;  // "orgu"
    auto frand = []() {  // uniform [0, 1)
        rng = rng * 1664525u + 1013904223u;
        return static_cast<float>(rng >> 8) * (1.0f / 16777216.0f);
    };

    count_ = 0;
    float ampSum = 0.0f;
    for (int h = 0; h < kMaxHarmonics; ++h) {
        const float a = merged[h];
        if (a <= 0.0f) continue;
        const float f = freq * static_cast<float>(h + 1);
        if (f >= sampleRate * 0.45f) break;  // higher harmonics only get worse
        amp_[count_] = a;
        // Random start phase + a whisker of detune (±0.1%) per harmonic.
        // Phase-locked sines all starting at zero sum into a buzzy, sawtooth-
        // ish wave — the app's early "car horn" period. Real pipes share
        // neither phase nor exact pitch; the detune adds a slow, gentle
        // shimmer as harmonics drift against each other.
        const float detune = 1.0f + (frand() - 0.5f) * 0.002f;
        inc_[count_] = f * detune * kTableSize / sampleRate;
        phase_[count_] = frand() * kTableSize;
        ampSum += a;
        ++count_;
    }

    // Normalize by sqrt of the amplitude sum: keeps stops in the same
    // loudness ballpark while still letting Tutti (more harmonics = more
    // power) sound bigger, like it should. The 0.2 is per-voice headroom
    // so a two-handed chord doesn't slam the master limiter instantly.
    gain_ = (ampSum > 0.0f) ? 0.2f / std::sqrt(ampSum) : 0.0f;

    // Envelope steps are per-sample deltas for a linear ramp.
    attackStep_ = 1.0f / (attackMs * 0.001f * sampleRate);
    releaseStep_ = 1.0f / (releaseMs * 0.001f * sampleRate);

    env_ = 0.0f;
    stage_ = Stage::Attack;
    active_ = true;
}

void Voice::release() {
    if (active_) stage_ = Stage::Release;
}

void Voice::fastRelease() {
    if (!active_) return;
    releaseStep_ = 1.0f / (0.010f * sampleRate_);  // 10 ms
    stage_ = Stage::Release;
}

void Voice::retrigger() {
    // Note struck again while still sounding (or double-triggered by touch
    // + MIDI at once). Ramp back up from wherever the envelope currently is
    // — restarting from zero would click.
    stage_ = Stage::Attack;
}

void Voice::render(float* out, int frames) {
    for (int i = 0; i < frames; ++i) {
        // Sum the harmonics: table lookup with linear interpolation.
        float s = 0.0f;
        for (int h = 0; h < count_; ++h) {
            float p = phase_[h];
            const int idx = static_cast<int>(p);
            const float frac = p - static_cast<float>(idx);
            s += amp_[h] * (gSine[idx] + frac * (gSine[idx + 1] - gSine[idx]));
            p += inc_[h];
            if (p >= kTableSize) p -= kTableSize;
            phase_[h] = p;
        }

        switch (stage_) {
            case Stage::Attack:
                env_ += attackStep_;
                if (env_ >= 1.0f) {
                    env_ = 1.0f;
                    stage_ = Stage::Sustain;
                }
                break;
            case Stage::Sustain:
                break;  // a pipe just... keeps going
            case Stage::Release:
                env_ -= releaseStep_;
                if (env_ <= 0.0f) {
                    env_ = 0.0f;
                    active_ = false;
                    return;  // dead — earlier frames are already in the mix
                }
                break;
        }

        out[i] += s * env_ * gain_;
    }
}
