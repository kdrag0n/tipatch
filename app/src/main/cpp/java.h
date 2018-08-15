#pragma once

#include <jni.h>
#include <exception>
#include <android/log.h>
#include <stdexcept>

#define check_exp() if(env->ExceptionCheck()) throw jv_exception();

class jv_exception : public std::exception {};
class comp_exception : public std::runtime_error {
public:
    comp_exception(const std::string &what) : std::runtime_error(what) {}
};

void rethrow(JNIEnv *env);

#ifndef NDEBUG
#define dbg(...) __android_log_print(ANDROID_LOG_DEBUG, "TiCPP", __VA_ARGS__);
#else
#define dbg(...) ;
#endif