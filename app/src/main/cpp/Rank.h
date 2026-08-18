#pragma once

#include <cstdint>
#include <vector>

// One rank of sampled pipes, loaded from a .mrk pack (see
// tools/import_ranks.py for the format and how packs are produced).
//
// The whole rank lives in RAM as int16 PCM. Four ranks of Giubiasco land
// around 47 MB, which sounds like a lot until you remember phones ship
// with 8 GB these days.

struct Pipe {
    // The keyboard key (MIDI number) this pipe belongs to — NOT its true
    // pitch. A 16' pipe keyed at 48 sounds an octave lower; that's its job.
    float keyNote;
    uint32_t loopStart;   // frames
    uint32_t loopEnd;
    uint32_t frameCount;
    const int16_t* data;  // points into Rank::pcm_
};

class Rank {
public:
    // Parses a .mrk blob. Returns false (and stays unloaded) on any
    // mismatch — a bad asset should mean silence, not a crash.
    bool load(const uint8_t* bytes, size_t size);

    bool loaded() const { return !pipes_.empty(); }
    float sampleRate() const { return sampleRate_; }
    float gain() const { return gain_; }

    // Closest pipe by keyboard key; the voice pitch-shifts the remainder.
    // Returns null when the played key is too far outside the rank's
    // compass (e.g. the celeste has no bass, pedal ranks have no treble).
    const Pipe* nearestPipe(float note) const;

private:
    std::vector<int16_t> pcm_;
    std::vector<Pipe> pipes_;
    float sampleRate_ = 48000.0f;
    float gain_ = 1.0f;
};
