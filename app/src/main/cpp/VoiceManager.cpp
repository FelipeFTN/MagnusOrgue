#include "VoiceManager.h"

void VoiceManager::noteOn(int note, uint32_t stopMask, float sampleRate) {
    // Same note already sounding? Just restart its attack. Happens with
    // repeated keystrokes whose release tail hasn't finished, or when the
    // touch keyboard and the MIDI keyboard hit the same note.
    for (Voice& v : voices_) {
        if (v.active() && v.note() == note && !v.releasing()) {
            v.retrigger();
            return;
        }
    }

    Voice* target = nullptr;
    for (Voice& v : voices_) {
        if (!v.active()) {
            target = &v;
            break;
        }
    }
    if (target == nullptr) {
        target = findVoiceToSteal();
    }
    target->start(note, stopMask, sampleRate, counter_++);
}

Voice* VoiceManager::findVoiceToSteal() {
    // All 32 voices busy. Steal the one that will be missed the least:
    // the most-faded releasing voice, or failing that, the oldest note.
    // Stealing causes a small click in theory; in practice you need more
    // fingers than I have to notice.
    Voice* best = nullptr;
    for (Voice& v : voices_) {
        if (v.releasing() && (best == nullptr || v.envelope() < best->envelope())) {
            best = &v;
        }
    }
    if (best != nullptr) return best;

    for (Voice& v : voices_) {
        if (best == nullptr || v.age() < best->age()) {
            best = &v;
        }
    }
    return best;
}

void VoiceManager::noteOff(int note) {
    // Release every matching voice, not just the first — retriggers can
    // leave more than one voice on the same note.
    for (Voice& v : voices_) {
        if (v.active() && v.note() == note && !v.releasing()) {
            v.release();
        }
    }
}

void VoiceManager::allOff() {
    for (Voice& v : voices_) {
        if (v.active()) v.fastRelease();
    }
}

void VoiceManager::render(float* out, int frames) {
    for (Voice& v : voices_) {
        if (v.active()) v.render(out, frames);
    }
}
