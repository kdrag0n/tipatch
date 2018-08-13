#include "io.h"

jv_bytes::jv_bytes(JNIEnv *env, jbyteArray array, jbyte *jbytes, unsigned int len) {
    this->env = env;
    this->array = array;
    this->jbytes = jbytes;
    this->len = len;
}

jv_bytes::~jv_bytes() {
    env->ReleaseByteArrayElements(array, jbytes, JNI_ABORT);
}

char *jv_bytes::bytes() {
    return (char *) jbytes;
}

std::string jv_bytes::string() {
    std::string ret((char *) jbytes, len);
    env->ReleaseByteArrayElements(array, jbytes, JNI_ABORT);
    return ret;
}

jv_bytes read_bytes(JNIEnv *env, jobject fis, unsigned int count) {
    // create the buffer
    jbyteArray buffer = env->NewByteArray(count);
    check_exp();

    // method: int InputStream#read(byte[])
    jclass clazz = env->GetObjectClass(fis);
    check_exp();

    jmethodID reader = env->GetMethodID(clazz, "read", "([B)I");
    check_exp();

    jint bytesRead = env->CallIntMethod(fis, reader, buffer);
    check_exp();

    if (bytesRead != count) {
        throw std::runtime_error(std::to_string(count) + " bytes requested; " +
                                 std::to_string(bytesRead) + " bytes read");
    }

    // get the data
    jboolean isCopy;
    jbyte *jbytes = env->GetByteArrayElements(buffer, &isCopy);

    return jv_bytes(env, buffer, jbytes, count);
}

void read_padding(JNIEnv *env, jobject fis, unsigned int item_size, unsigned int page_size) {
    unsigned int page_mask = page_size - 1;
    if ((item_size & page_mask) == 0)
        return;

    unsigned int count = page_size - (item_size & page_mask);
    read_bytes(env, fis, count);
}