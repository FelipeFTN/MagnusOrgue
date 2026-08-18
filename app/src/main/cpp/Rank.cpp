#include "Rank.h"

#include <cmath>
#include <cstring>

namespace {
// Little helpers because casting through misaligned pointers is UB and
// the asset buffer offers no alignment promises.
uint32_t readU32(const uint8_t* p) {
    uint32_t v;
    std::memcpy(&v, p, 4);
    return v;
}
int32_t readI32(const uint8_t* p) {
    int32_t v;
    std::memcpy(&v, p, 4);
    return v;
}
float readF32(const uint8_t* p) {
    float v;
    std::memcpy(&v, p, 4);
    return v;
}
}  // namespace

bool Rank::load(const uint8_t* bytes, size_t size) {
    // Header: "MORK", version, sampleRate, pipeCount, gain. 20 bytes.
    if (size < 20 || std::memcmp(bytes, "MORK", 4) != 0) return false;
    // v2 keys pipes by keyboard position instead of true pitch; v1 packs
    // would sound wrong (un-detuned celeste, transposed 16'), so refuse.
    if (readU32(bytes + 4) != 2) return false;
    sampleRate_ = static_cast<float>(readU32(bytes + 8));
    const uint32_t pipeCount = readU32(bytes + 12);
    gain_ = readF32(bytes + 16);

    const size_t tableBytes = static_cast<size_t>(pipeCount) * 20;
    if (size < 20 + tableBytes) return false;
    const uint8_t* table = bytes + 20;
    const uint8_t* blob = table + tableBytes;
    const size_t blobBytes = size - 20 - tableBytes;

    // One copy into our own buffer, then the Pipe entries point into it.
    pcm_.resize(blobBytes / 2);
    std::memcpy(pcm_.data(), blob, pcm_.size() * 2);

    pipes_.clear();
    pipes_.reserve(pipeCount);
    for (uint32_t i = 0; i < pipeCount; ++i) {
        const uint8_t* row = table + i * 20;
        Pipe p;
        p.keyNote = static_cast<float>(readI32(row)) / 1000.0f;
        p.loopStart = readU32(row + 4);
        p.loopEnd = readU32(row + 8);
        p.frameCount = readU32(row + 12);
        const uint32_t offsetBytes = readU32(row + 16);

        // Sanity: everything must fit inside the blob, loop inside the data.
        if (offsetBytes / 2 + p.frameCount > pcm_.size() ||
            p.loopEnd + 1 >= p.frameCount || p.loopStart >= p.loopEnd) {
            pipes_.clear();
            pcm_.clear();
            return false;
        }
        p.data = pcm_.data() + offsetBytes / 2;
        pipes_.push_back(p);
    }
    return true;
}

const Pipe* Rank::nearestPipe(float note) const {
    if (pipes_.empty()) return nullptr;
    const Pipe* best = &pipes_[0];
    float bestDist = std::fabs(pipes_[0].keyNote - note);
    for (const Pipe& p : pipes_) {
        const float d = std::fabs(p.keyNote - note);
        if (d < bestDist) {
            bestDist = d;
            best = &p;
        }
    }
    // The importer keeps one pipe per minor third, so anything farther
    // than ~2 semitones means the key is outside this rank's compass.
    // Ranks simply don't speak there — the Voce Umana has no bass octave,
    // the pedal ranks stop at F4, and stretching a pipe several semitones
    // sounds like a tape machine dying anyway.
    return (bestDist > 2.01f) ? nullptr : best;
}
