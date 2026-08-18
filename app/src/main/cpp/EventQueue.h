#pragma once

#include <atomic>
#include <cstdint>

// Tiny single-producer/single-consumer ring buffer that carries note events
// from the app into the audio callback.
//
// Why not a mutex? Because the consumer is the real-time audio thread, and
// blocking it — even for the few microseconds a contended lock can take —
// means audible glitches. The producer side IS allowed to lock (see
// AudioEngine::pushEvent), which is how we cheat several app threads into
// the "single producer" role.
//
// Classic Lamport queue. Good explainer:
// https://www.snellman.net/blog/archive/2016-12-13-ring-buffers/

struct Event {
    enum Type : int32_t {
        NoteOn,
        NoteOff,
        SetStopMask,   // value = bitmask over kStops (bit N = stop N pulled)
        SetTremulant,  // value = 0/1
        AllOff,        // panic
    };
    Type type;
    int32_t value;
};

class EventQueue {
public:
    bool push(const Event& e) {
        const uint32_t head = head_.load(std::memory_order_relaxed);
        const uint32_t next = (head + 1) & kMask;
        if (next == tail_.load(std::memory_order_acquire)) {
            return false;  // full — drop the event. 256 pending events means
                           // something else is very wrong anyway.
        }
        slots_[head] = e;
        head_.store(next, std::memory_order_release);
        return true;
    }

    bool pop(Event& out) {
        const uint32_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire)) {
            return false;  // empty
        }
        out = slots_[tail];
        tail_.store((tail + 1) & kMask, std::memory_order_release);
        return true;
    }

private:
    static constexpr uint32_t kSize = 256;  // must stay a power of two
    static constexpr uint32_t kMask = kSize - 1;

    Event slots_[kSize];
    std::atomic<uint32_t> head_{0};  // written by producer
    std::atomic<uint32_t> tail_{0};  // written by consumer
};
