#include "io.h"
#include "util.h"
#include <functional>

jv_bytes::jv_bytes(JNIEnv *env, jbyteArray array, jbyte *jbytes, unsigned int len) {
    this->env = env;
    this->array = array;
    this->jbytes = jbytes;
    this->len = len;
}

jv_bytes::~jv_bytes() {
    env->ReleaseByteArrayElements(array, jbytes, JNI_ABORT);
}

byte *jv_bytes::bytes() {
    return (byte *) jbytes;
}

byte *jv_bytes::copy_bytes() {
    // copy
    auto copy = (byte *) malloc(len);
    memcpy(copy, jbytes, len);
    this->~jv_bytes();

    return copy;
}

jv_bytes read_bytes(JNIEnv *env, jobject fis, unsigned int count) {
    // create the buffer
    jbyteArray buffer = env->NewByteArray(count);
    check_exp();

    // method: int InputStream#read(byte b[], int off, int len)
    jclass clazz = env->GetObjectClass(fis);
    check_exp();

    jmethodID reader = env->GetMethodID(clazz, "read", "([BII)I");
    check_exp();

    jint bytes_read = 0;
    while (bytes_read < count) {
        bytes_read += env->CallIntMethod(fis, reader, buffer, bytes_read, count - bytes_read);
        check_exp();
    }

    // get the data
    jbyte *jbytes = env->GetByteArrayElements(buffer, nullptr);

    return jv_bytes(env, buffer, jbytes, count);
}

unsigned int padding_size(unsigned int item_size, unsigned int page_size) {
    unsigned int page_mask = page_size - 1;
    if ((item_size & page_mask) == 0)
        return 0;

    return page_size - (item_size & page_mask);
}

void read_padding(JNIEnv *env, jobject fis, unsigned int item_size, unsigned int page_size) {
    auto count = padding_size(item_size, page_size);
    if (count == 0)
        return;

    read_bytes(env, fis, count);
}

// writing
void write_bytes(JNIEnv *env, jobject fos, byte *data, size_t length) {
    // create the buffer
    jbyteArray buffer = env->NewByteArray(static_cast<jsize>(length));
    check_exp();

    finally free_buf([&]{
        env->DeleteLocalRef(buffer);
    });

    // fill the buffer
    if (data != nullptr) { // nullptr = write zeroes
        env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(length), (jbyte *) data);
        check_exp();
    }

    // method: void OutputStream#write(byte[])
    jclass clazz = env->GetObjectClass(fos);
    check_exp();

    jmethodID writer = env->GetMethodID(clazz, "write", "([B)V");
    check_exp();

    env->CallVoidMethod(fos, writer, buffer);
    check_exp();
}

void write_padding(JNIEnv *env, jobject fos, size_t item_size, unsigned int page_size) {
    auto count = padding_size((unsigned int) item_size, page_size);
    if (count == 0)
        return;

    write_bytes(env, fos, nullptr, count);
}