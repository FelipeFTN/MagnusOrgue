#pragma once

#include <cstdint>
#include "Stops.h"

// One sounding note: a bank of sine harmonics (wavetable lookups) plus a
// dead-simple attack/release envelope. Organs have no decay/sustain stages —
// a pipe speaks, holds forever, then stops. The short ramps only exist so
// the edges don't click.
class Voice {
public:
    // Builds the shared sine table. Call once, from a normal thread,
    // before the first render.
    static void initWavetable();

    // stopMask: bitmask over kStops. Like on a real organ, pulling several
    // stops just stacks their pipe ranks — here, their harmonic recipes.
    void start(int note, uint32_t stopMask, float sampleRate, uint32_t age);
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

    static constexpr int kTableSize = 2048;

    bool active_ = false;
    int note_ = -1;
    uint32_t age_ = 0;

    // Per-harmonic state. Zero-amplitude and above-Nyquist harmonics are
    // filtered out in start(), so these arrays are packed and count_ is
    // usually well below kMaxHarmonics.
    int count_ = 0;
    float phase_[kMaxHarmonics];   // in table-index units, not radians
    float inc_[kMaxHarmonics];
    float amp_[kMaxHarmonics];
    float gain_ = 0.0f;

    Stage stage_ = Stage::Attack;
    float env_ = 0.0f;
    float attackStep_ = 0.0f;
    float releaseStep_ = 0.0f;
    float sampleRate_ = 48000.0f;
};
