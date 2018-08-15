#include "java.h"
#include "image.h"
#include "io.h"

void rethrow(JNIEnv *env) {
    try {
        throw;
    } catch (const jv_exception &) {
    } catch (const img_exception &e) {
        jclass clazz = env->FindClass("com/kdrag0n/tipatch/jni/ImageException");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const io_exception &e) {
        jclass clazz = env->FindClass("java/io/IOException");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const comp_exception &e) {
        jclass clazz = env->FindClass("com/kdrag0n/tipatch/jni/CompressException");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const std::bad_alloc &e) {
        jclass clazz = env->FindClass("java/lang/OutOfMemoryError");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const std::out_of_range &e) {
        jclass clazz = env->FindClass("java/lang/IndexOutOfBoundsException");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const std::exception &e) { // unknown
        jclass clazz = env->FindClass("java/lang/Error");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    }
}