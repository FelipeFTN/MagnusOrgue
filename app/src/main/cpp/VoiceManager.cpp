#include "VoiceManager.h"

void VoiceManager::noteOn(int note, uint32_t stopMask, const Rank* ranks, float outputRate) {
    // Same note already sounding and not releasing? Restart its attack.
    // The Kotlin controller refcounts notes so this "shouldn't happen" —
    // but if the event queue ever drops a NoteOff under pressure, this is
    // what keeps a duplicate voice from piling onto the same pipe.
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
    target->start(note, stopMask, ranks, outputRate, counter_++);
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
