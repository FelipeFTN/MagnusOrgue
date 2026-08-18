#include "Reverb.h"

namespace {
// Comb delays in ms — mutually prime-ish so their echoes don't pile up on
// a common period (that would ring like a metal pipe, ironically).
constexpr float kCombMs[4] = {29.7f, 37.1f, 41.1f, 43.7f};
constexpr float kAllpassMs[2] = {5.0f, 1.7f};

constexpr float kFeedback = 0.78f;  // tail length — higher = bigger church
constexpr float kDamp = 0.30f;      // treble absorption in the tail
constexpr float kWet = 0.22f;       // gentler now: the Giubiasco samples
                                    // already carry their own room
}  // namespace

void Reverb::prepare(float sampleRate) {
    for (int i = 0; i < 4; ++i) {
        combs_[i].line.assign(static_cast<size_t>(kCombMs[i] * 0.001f * sampleRate), 0.0f);
        combs_[i].pos = 0;
        combs_[i].store = 0.0f;
    }
    for (int i = 0; i < 2; ++i) {
        allpasses_[i].line.assign(static_cast<size_t>(kAllpassMs[i] * 0.001f * sampleRate), 0.0f);
        allpasses_[i].pos = 0;
    }
    ready_ = true;
}

void Reverb::process(float* buf, int frames) {
    if (!ready_) return;
    for (int i = 0; i < frames; ++i) {
        const float dry = buf[i];

        float wet = 0.0f;
        for (Comb& c : combs_) {
            wet += c.tick(dry, kFeedback, kDamp);
        }
        wet *= 0.25f;  // average of the four combs

        for (Allpass& a : allpasses_) {
            wet = a.tick(wet);
        }

        buf[i] = dry + wet * kWet;
    }
}
