#pragma once

#include <jni.h>
#include <exception>
#include <android/log.h>

#define check_exp() if(env->ExceptionCheck()) throw jv_exception();

class jv_exception : public std::exception {};

void rethrow(JNIEnv *env);

#define dbg(...) __android_log_print(ANDROID_LOG_DEBUG, "TiCPP", __VA_ARGS__);