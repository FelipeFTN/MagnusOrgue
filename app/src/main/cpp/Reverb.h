#pragma once

#include <vector>

// Fixed "cathedral" reverb, mono, in-place. A dry organ sounds like a test
// tone; pipes only make sense inside a big stone room, so this is not
// optional polish — it's half the instrument.
//
// Structure is Freeverb-shaped: a pre-delay (the time the first reflection
// needs to come back off a distant wall), eight damped comb filters in
// parallel for the tail, and four allpasses in series to smear the echoes
// into a wash. Tuned for a ~4 s tail — release a chord and the building
// answers.
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
            // Damp the tail: high frequencies die faster, the way stone,
            // air and a few hundred pews absorb treble first.
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

    std::vector<float> predelay_;
    int prePos_ = 0;

    Comb combs_[8];
    Allpass allpasses_[4];
    bool ready_ = false;
};
