#pragma once

#include "Voice.h"

// Fixed pool of voices. Fixed because the audio thread must never allocate,
// and 32 is plenty: ten fingers + generous release tails.
class VoiceManager {
public:
    static constexpr int kMaxVoices = 32;

    void noteOn(int note, uint32_t stopMask, float sampleRate);
    void noteOff(int note);
    void allOff();  // panic — fast-fade everything

    // Renders all live voices, summed into a mono buffer (must be zeroed
    // by the caller first).
    void render(float* out, int frames);

private:
    Voice* findVoiceToSteal();

    Voice voices_[kMaxVoices];
    uint32_t counter_ = 0;  // monotonically increasing "age" stamp
};
