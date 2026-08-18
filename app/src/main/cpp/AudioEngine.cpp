#include "AudioEngine.h"

#include <android/log.h>

#include <cmath>
#include <cstring>
#include <thread>

#define LOG_TAG "MagnusOrgue"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(streamLock_);

    // start() doubles as restart() after a disconnect, so tear down first.
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }

    Voice::initWavetable();

    // Ask for the low-latency path. Notably we do NOT set a sample rate:
    // taking the device's native rate skips the resampler entirely.
    // See https://github.com/google/oboe/blob/main/docs/FullGuide.md
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setFormatConversionAllowed(true)
            ->setChannelCount(2)
            ->setUsage(oboe::Usage::Media)
            ->setCallback(this);

    const oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return false;
    }

    sampleRate_ = static_cast<float>(stream_->getSampleRate());

    // Two bursts is the usual sweet spot: one burst is glitch-roulette,
    // more just adds latency.
    stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 2);

    // Scratch buffer big enough for any callback size we could see.
    mono_.assign(static_cast<size_t>(stream_->getBufferCapacityInFrames()) + 1024, 0.0f);

    // Reverb delay lines depend on the sample rate, so (re)build them here,
    // safely off the audio thread.
    reverb_.prepare(sampleRate_);

    // Log what we actually got — devices love to silently downgrade these.
    LOGI("Stream open: %d Hz, burst %d, buffer %d, perf=%s, sharing=%s",
         stream_->getSampleRate(), stream_->getFramesPerBurst(),
         stream_->getBufferSizeInFrames(),
         stream_->getPerformanceMode() == oboe::PerformanceMode::LowLatency ? "LowLatency" : "other",
         stream_->getSharingMode() == oboe::SharingMode::Exclusive ? "Exclusive" : "Shared");

    stream_->requestStart();
    return true;
}

void AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(streamLock_);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void AudioEngine::pushEvent(const Event& e) {
    // The ring buffer is single-producer; this mutex is what makes "single"
    // true. It's only ever contended between the UI and MIDI threads for
    // nanoseconds, so nobody real-time is waiting on it.
    std::lock_guard<std::mutex> lock(pushLock_);
    queue_.push(e);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* stream,
                                                   void* audioData,
                                                   int32_t numFrames) {
    // ===== REAL-TIME THREAD from here on. No malloc, no locks, no logging. =====

    Event e;
    while (queue_.pop(e)) {
        switch (e.type) {
            case Event::NoteOn:
                // No stops pulled = silence, same as the real thing.
                if (stopMask_ != 0) voices_.noteOn(e.value, stopMask_, sampleRate_);
                break;
            case Event::NoteOff:
                voices_.noteOff(e.value);
                break;
            case Event::SetStopMask:
                // Applies to new notes only; held notes keep the
                // registration they started with. Simple, and it's what
                // you want musically anyway.
                stopMask_ = static_cast<uint32_t>(e.value) & ((1u << kStopCount) - 1u);
                break;
            case Event::AllOff:
                voices_.allOff();
                break;
        }
    }

    // Paranoia: mono_ is sized off bufferCapacity, so this shouldn't trip.
    if (static_cast<size_t>(numFrames) > mono_.size()) {
        numFrames = static_cast<int32_t>(mono_.size());
    }

    std::memset(mono_.data(), 0, sizeof(float) * numFrames);
    voices_.render(mono_.data(), numFrames);
    reverb_.process(mono_.data(), numFrames);

    // Mono → interleaved stereo, with a one-pole smoother on the gain (a raw
    // jump on the volume slider = zipper noise) and tanh as a soft safety
    // limiter — a 10-finger Tutti chord overshoots 1.0 and hard clipping
    // sounds like a chainsaw, while tanh just leans into it politely.
    // The drive used to be *4.0 here, which saturated single notes and made
    // everything honk. Lesson learned: the limiter is a seatbelt, not an
    // effect.
    auto* out = static_cast<float*>(audioData);
    const float target = targetGain_.load(std::memory_order_relaxed);
    const int channels = stream->getChannelCount();
    for (int i = 0; i < numFrames; ++i) {
        smoothedGain_ += (target - smoothedGain_) * 0.005f;
        const float s = std::tanh(mono_[i] * smoothedGain_ * 1.5f) * 0.9f;
        for (int c = 0; c < channels; ++c) {
            out[i * channels + c] = s;
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    // The stream died under us: headphones yanked, output device changed,
    // etc. Recreating it from this thread is forbidden, so hand off.
    // https://github.com/google/oboe/blob/main/docs/notes/disconnect.md
    if (error == oboe::Result::ErrorDisconnected) {
        LOGI("Stream disconnected, reopening");
        std::thread([this] { start(); }).detach();
    }
}
