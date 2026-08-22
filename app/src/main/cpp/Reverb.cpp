#include "Reverb.h"

namespace {
// Comb delays in ms — mutually prime-ish so their echoes don't pile up on
// a common period (that would ring like a metal pipe, ironically). Long,
// because the "room" here is a nave, not a rehearsal booth.
constexpr float kCombMs[8] = {50.0f, 56.1f, 61.7f, 68.3f, 72.9f, 78.1f, 83.3f, 89.9f};
constexpr float kAllpassMs[4] = {12.6f, 10.0f, 7.7f, 5.1f};

// First reflection off a far wall: ~7 m there and back.
constexpr float kPreDelayMs = 22.0f;

// RT60 with these numbers lands around 4 s — release a chord and it hangs
// in the air the way a real cathedral answers back. Push feedback past
// ~0.93 and the tail starts to feel like it never leaves; don't.
constexpr float kFeedback = 0.90f;
constexpr float kDamp = 0.28f;  // treble absorption in the tail
constexpr float kWet = 0.34f;   // the samples carry some room of their own,
                                // but a cathedral should be unmistakable
}  // namespace

void Reverb::prepare(float sampleRate) {
    predelay_.assign(static_cast<size_t>(kPreDelayMs * 0.001f * sampleRate), 0.0f);
    prePos_ = 0;
    for (int i = 0; i < 8; ++i) {
        combs_[i].line.assign(static_cast<size_t>(kCombMs[i] * 0.001f * sampleRate), 0.0f);
        combs_[i].pos = 0;
        combs_[i].store = 0.0f;
    }
    for (int i = 0; i < 4; ++i) {
        allpasses_[i].line.assign(static_cast<size_t>(kAllpassMs[i] * 0.001f * sampleRate), 0.0f);
        allpasses_[i].pos = 0;
    }
    ready_ = true;
}

void Reverb::process(float* buf, int frames) {
    if (!ready_) return;
    for (int i = 0; i < frames; ++i) {
        const float dry = buf[i];

        // Pre-delay: the wet path hears the organ a beat later than we do.
        const float fed = predelay_[prePos_];
        predelay_[prePos_] = dry;
        if (++prePos_ >= static_cast<int>(predelay_.size())) prePos_ = 0;

        float wet = 0.0f;
        for (Comb& c : combs_) {
            wet += c.tick(fed, kFeedback, kDamp);
        }
        wet *= 0.125f;  // average of the eight combs

        for (Allpass& a : allpasses_) {
            wet = a.tick(wet);
        }

        buf[i] = dry + wet * kWet;
    }
}
