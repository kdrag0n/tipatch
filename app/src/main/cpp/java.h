#pragma once

#include <jni.h>
#include <exception>

#define check_exp() if(env->ExceptionCheck()) throw jv_exception();

class jv_exception : public std::exception {};

void rethrow(JNIEnv *env);