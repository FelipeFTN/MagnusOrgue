#pragma once

#include <cstdint>

#include "Rank.h"
#include "Stops.h"

// One sounding note: up to one sample layer per pulled stop, all summed.
// Each layer loops its pipe recording forever (a pipe never runs out of
// wind), pitch-shifted from the nearest sampled pipe to the played note.
//
// The envelope is only a de-clicker now: the attack ramp is a few ms (the
// recording carries the real pipe speech), the release fakes the pipe
// closing since we don't ship release samples. TODO: real release samples
// — the set has them (R0..R3), they're just a lot more data.
class Voice {
public:
    void start(int note, uint32_t stopMask, const Rank* ranks,
               float outputRate, uint32_t age);
    void release();              // normal note-off
    void fastRelease();          // panic: ~10 ms fade, quick but not a click
    void retrigger();            // same note struck again: restart the attack

    // Adds (not overwrites!) this voice into a mono buffer.
    void render(float* out, int frames);

    bool active() const { return active_; }
    bool releasing() const { return active_ && stage_ == Stage::Release; }
    int note() const { return note_; }
    uint32_t age() const { return age_; }
    float envelope() const { return env_; }

private:
    enum class Stage { Attack, Sustain, Release };

    struct Layer {
        const Pipe* pipe;
        float gain;
        double pos;  // fractional read position, in frames
        double inc;  // frames advanced per output sample (the pitch shift)
    };

    bool active_ = false;
    int note_ = -1;
    uint32_t age_ = 0;

    Layer layers_[kStopCount];
    int layerCount_ = 0;

    Stage stage_ = Stage::Attack;
    float env_ = 0.0f;
    float attackStep_ = 0.0f;
    float releaseStep_ = 0.0f;
    float outputRate_ = 48000.0f;
};
