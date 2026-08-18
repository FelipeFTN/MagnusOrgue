// JNI glue for com.felipeftn.magnusorgue.audio.AudioEngine.
// Kept intentionally dumb: unpack the argument, poke the engine, done.
// All the thinking happens in AudioEngine.

#include <jni.h>

#include "AudioEngine.h"

namespace {
AudioEngine gEngine;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_felipeftn_magnusorgue_audio_AudioEngine_start(JNIEnv*, jobject) {
    return gEngine.start() ? JNI_TRUE : JNI_FALSE;
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
Java_com_felipeftn_magnusorgue_audio_AudioEngine_setStop(JNIEnv*, jobject, jint index) {
    gEngine.pushEvent({Event::SetStop, index});
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
