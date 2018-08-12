#include <jni.h>
#include "tipatch.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_kdrag0n_tipatch_jni_Image_init(
        JNIEnv *env, jobject, jobject fis) {
    //jclass clazz = env->GetObjectClass(fis);

    // int InputStream#read(byte[])
    //jmethodID read = env->GetMethodID(clazz, "read", "([B)I");

    // call it
    //env->CallObjectMethod(request, header, env->NewStringUTF(STR_AUTHORIZATION), env->NewStringUTF(getKey()));

    Image *ptr = new Image();
    return (jlong) ptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image_free(
        JNIEnv *env, jobject, jlong handle) {
    Image* ptr = (Image*) handle;
    delete ptr;
}