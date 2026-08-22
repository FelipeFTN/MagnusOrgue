#pragma once

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <vector>

#include "EventQueue.h"
#include "Rank.h"
#include "Reverb.h"
#include "VoiceManager.h"

// Owns the Oboe output stream and everything that runs on the audio thread.
//
// Threading contract, because this is where it matters:
//   - onAudioReady() runs on Oboe's high-priority callback thread. It must
//     not allocate, lock, log, or touch JNI. Ever.
//   - Everything the app wants to tell the audio thread goes through the
//     lock-free EventQueue. The producer side is serialized with pushLock_
//     so the UI thread and the MIDI thread don't trample each other.
//   - Volume is just an atomic float; ordering vs. note events doesn't
//     matter for a volume knob.
class AudioEngine : public oboe::AudioStreamCallback {
public:
    bool start();
    void stop();

    // Parses one .mrk pack into ranks_[stopIndex]. Must be called before
    // start() — the audio thread reads the ranks without any locking, so
    // they have to be immutable by the time the stream runs.
    bool loadRank(int stopIndex, const uint8_t* bytes, size_t size);

    void pushEvent(const Event& e);
    void setVolume(float gain) { targetGain_.store(gain, std::memory_order_relaxed); }
    void setReverb(float amount) { reverbAmount_.store(amount, std::memory_order_relaxed); }

    // oboe::AudioStreamCallback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex streamLock_;  // guards open/close, never taken in the callback
    std::mutex pushLock_;    // funnels multiple app threads into the SPSC queue

    EventQueue queue_;
    VoiceManager voices_;
    Reverb reverb_;
    Rank ranks_[kStopCount];
    uint32_t stopMask_ = 1;  // Principale 8' pulled by default

    std::atomic<float> targetGain_{0.8f};
    std::atomic<float> reverbAmount_{0.6f};  // dry chapel .. full cathedral
    float smoothedGain_ = 0.0f;

    // Tremulant: a slow amplitude wobble on the whole organ, like the
    // real device shaking the wind supply. Depth eases in/out so toggling
    // the knob mid-chord doesn't step.
    bool tremulant_ = false;
    float tremPhase_ = 0.0f;
    float tremDepth_ = 0.0f;

    float sampleRate_ = 48000.0f;
    std::vector<float> mono_;  // scratch buffer, sized once in start()
};
