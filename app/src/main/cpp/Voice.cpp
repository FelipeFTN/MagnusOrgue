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

void Voice::start(int note, const StopDefinition& stop, float sampleRate, uint32_t age) {
    note_ = note;
    age_ = age;
    sampleRate_ = sampleRate;

    // Equal temperament, A4 = 440 Hz = MIDI 69.
    const float freq = 440.0f * std::exp2((note - 69) / 12.0f);

    // Pack only the harmonics we'll actually hear. Anything at or above
    // ~0.45 * sampleRate gets dropped — pushing partials past Nyquist gives
    // aliasing, which on an organ sounds like a broken AM radio.
    count_ = 0;
    float ampSum = 0.0f;
    for (int h = 0; h < kMaxHarmonics; ++h) {
        const float a = stop.harmonics[h];
        if (a <= 0.0f) continue;
        const float f = freq * static_cast<float>(h + 1);
        if (f >= sampleRate * 0.45f) break;  // higher harmonics only get worse
        amp_[count_] = a;
        inc_[count_] = f * kTableSize / sampleRate;
        phase_[count_] = 0.0f;
        ampSum += a;
        ++count_;
    }

    // Normalize by sqrt of the amplitude sum: keeps stops in the same
    // loudness ballpark while still letting Tutti (more harmonics = more
    // power) sound bigger, like it should. The 0.15 is per-voice headroom
    // so a two-handed chord doesn't slam the master limiter instantly.
    gain_ = (ampSum > 0.0f) ? 0.15f / std::sqrt(ampSum) : 0.0f;

    // Envelope steps are per-sample deltas for a linear ramp.
    attackStep_ = 1.0f / (stop.attackMs * 0.001f * sampleRate);
    releaseStep_ = 1.0f / (stop.releaseMs * 0.001f * sampleRate);

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
