#include "Voice.h"

#include <cmath>

namespace {
// De-clicker attack (the sample has the real speech) and a fake pipe-stop
// release. Values by ear.
constexpr float kAttackMs = 4.0f;
constexpr float kReleaseMs = 140.0f;

// int16 -> float, with a bit of headroom baked in so a handful of stops on
// one note doesn't instantly lean on the master limiter.
constexpr float kLayerScale = 0.5f / 32768.0f;
}  // namespace

void Voice::start(int note, uint32_t stopMask, const Rank* ranks,
                  float outputRate, uint32_t age) {
    note_ = note;
    age_ = age;
    outputRate_ = outputRate;

    layerCount_ = 0;
    for (int s = 0; s < kStopCount; ++s) {
        if ((stopMask & (1u << s)) == 0 || !ranks[s].loaded()) continue;
        const Pipe* pipe = ranks[s].nearestPipe(static_cast<float>(note));
        if (pipe == nullptr) continue;

        Layer& l = layers_[layerCount_++];
        l.pipe = pipe;
        l.gain = ranks[s].gain() * kLayerScale;
        l.pos = 0.0;
        // Two pitch corrections in one increment: the distance from the
        // pipe's own root note (the importer keeps one pipe per minor
        // third, so this is at most ±1 semitone), and the sample-rate vs
        // device-rate ratio.
        l.inc = std::exp2((static_cast<float>(note) - pipe->rootNote) / 12.0f)
                * (ranks[s].sampleRate() / outputRate);
    }

    if (layerCount_ == 0) {  // no stops pulled (or assets missing): silence
        active_ = false;
        return;
    }

    attackStep_ = 1.0f / (kAttackMs * 0.001f * outputRate);
    releaseStep_ = 1.0f / (kReleaseMs * 0.001f * outputRate);

    env_ = 0.0f;
    stage_ = Stage::Attack;
    active_ = true;
}

void Voice::release() {
    if (active_) stage_ = Stage::Release;
}

void Voice::fastRelease() {
    if (!active_) return;
    releaseStep_ = 1.0f / (0.010f * outputRate_);  // 10 ms
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
        float s = 0.0f;
        for (int li = 0; li < layerCount_; ++li) {
            Layer& l = layers_[li];

            // Wrap inside the sustain loop. The importer guarantees one
            // frame of slack past loopEnd, so reading idx+1 is always safe.
            if (l.pos >= static_cast<double>(l.pipe->loopEnd)) {
                l.pos -= static_cast<double>(l.pipe->loopEnd - l.pipe->loopStart);
            }

            const auto idx = static_cast<uint32_t>(l.pos);
            const float frac = static_cast<float>(l.pos - idx);
            const float a = l.pipe->data[idx];
            const float b = l.pipe->data[idx + 1];
            s += (a + frac * (b - a)) * l.gain;

            l.pos += l.inc;
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

        out[i] += s * env_;
    }
}
