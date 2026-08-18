#pragma once

#include <vector>

// Small fixed "church" reverb, mono, in-place. A dry organ sounds like a
// test tone; pipes only make sense inside a room, so this is not optional
// polish — it's half the instrument.
//
// Structure is a slimmed-down Freeverb: four damped comb filters in
// parallel feeding two allpasses in series. Nothing fancy, very cheap.
// Background: https://ccrma.stanford.edu/~jos/pasp/Freeverb.html
class Reverb {
public:
    // Allocates the delay lines — call from a normal thread, never from
    // the audio callback.
    void prepare(float sampleRate);

    // Mixes the wet signal into buf. Safe on the audio thread (no
    // allocation, just ring-buffer reads/writes).
    void process(float* buf, int frames);

private:
    struct Comb {
        std::vector<float> line;
        int pos = 0;
        float store = 0.0f;  // one-pole lowpass state for damping

        float tick(float in, float feedback, float damp) {
            float out = line[pos];
            // Damp the tail: high frequencies die faster, like in a real
            // room full of pews and people.
            store = out * (1.0f - damp) + store * damp;
            line[pos] = in + store * feedback;
            if (++pos >= static_cast<int>(line.size())) pos = 0;
            return out;
        }
    };

    struct Allpass {
        std::vector<float> line;
        int pos = 0;

        float tick(float in) {
            const float delayed = line[pos];
            line[pos] = in + delayed * 0.5f;
            if (++pos >= static_cast<int>(line.size())) pos = 0;
            return delayed - in;
        }
    };

    Comb combs_[4];
    Allpass allpasses_[2];
    bool ready_ = false;
};
