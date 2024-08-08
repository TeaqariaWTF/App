//
// Created by nick on 01.08.24.
//

#include <jni.h>

extern "C" void simulator_start(JNIEnv* env, jobject thiz, jobject bitmap, jint w, jint h);
extern "C" void simulator_stop(JNIEnv* env);
extern "C" void simulator_key(jint key);

extern "C" JNIEXPORT void JNICALL Java_org_andbootmgr_app_themes_Simulator_key(JNIEnv* env, jobject thiz, jint key) {
	simulator_key(key);
}

extern "C" JNIEXPORT void JNICALL Java_org_andbootmgr_app_themes_Simulator_stop(JNIEnv* env, jobject thiz) {
	simulator_stop(env);
}

extern "C" JNIEXPORT void JNICALL Java_org_andbootmgr_app_themes_Simulator_start(JNIEnv* env, jobject thiz, jobject bitmap, jint w, jint h) {
	simulator_start(env, thiz, bitmap, w, h);
}