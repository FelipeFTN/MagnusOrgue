// JNI glue for com.felipeftn.magnusorgue.audio.AudioEngine.
// Kept intentionally dumb: unpack the argument, poke the engine, done.
// All the thinking happens in AudioEngine.

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <jni.h>

#include <vector>

#include "AudioEngine.h"

namespace {
AudioEngine gEngine;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_start(JNIEnv*, jobject) {
    return gEngine.start() ? JNI_TRUE : JNI_FALSE;
}

// Loads every rank pack from the APK's assets. Called once at startup,
// before start() — the audio thread reads ranks lock-free, so they must
// be settled before the stream exists.
JNIEXPORT jboolean JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_loadRanks(JNIEnv* env, jobject,
                                                           jobject assetManager) {
    AAssetManager* am = AAssetManager_fromJava(env, assetManager);
    if (am == nullptr) return JNI_FALSE;

    bool allOk = true;
    for (int s = 0; s < kStopCount; ++s) {
        AAsset* asset = AAssetManager_open(am, kStops[s].assetPath, AASSET_MODE_BUFFER);
        if (asset == nullptr) {
            allOk = false;  // pack missing — that stop will just be silent
            continue;
        }
        const auto size = static_cast<size_t>(AAsset_getLength(asset));
        const auto* bytes = static_cast<const uint8_t*>(AAsset_getBuffer(asset));
        if (bytes == nullptr || !gEngine.loadRank(s, bytes, size)) allOk = false;
        AAsset_close(asset);
    }
    return allOk ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_stop(JNIEnv*, jobject) {
    gEngine.stop();
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_noteOn(JNIEnv*, jobject, jint note) {
    if (note < 0 || note > 127) return;  // garbage in, nothing out
    gEngine.pushEvent({Event::NoteOn, note});
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_noteOff(JNIEnv*, jobject, jint note) {
    if (note < 0 || note > 127) return;
    gEngine.pushEvent({Event::NoteOff, note});
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_setStopMask(JNIEnv*, jobject, jint mask) {
    gEngine.pushEvent({Event::SetStopMask, mask});
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_setVolume(JNIEnv*, jobject, jfloat gain) {
    if (gain < 0.0f) gain = 0.0f;
    if (gain > 1.0f) gain = 1.0f;
    gEngine.setVolume(gain);
}

JNIEXPORT void JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_allNotesOff(JNIEnv*, jobject) {
    gEngine.pushEvent({Event::AllOff, 0});
}

}  // extern "C"
