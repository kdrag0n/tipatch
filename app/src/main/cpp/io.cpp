#include "io.h"
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

char *jv_bytes::bytes() {
    return (char *) jbytes;
}

std::string jv_bytes::string() {
    std::string ret((char *) jbytes, len);
    this->~jv_bytes();
    return ret;
}

class finally  {
    std::function<void(void)> functor;
public:
    finally(const std::function<void(void)> &functor) : functor(functor) {}
    ~finally() {
        functor();
    }
};

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
void write_bytes(JNIEnv *env, jobject fos, char *data, unsigned long length) {
    // create the buffer
    jbyteArray buffer = env->NewByteArray(length);
    check_exp();

    finally free_buf([&]{
        env->DeleteLocalRef(buffer);
    });

    // fill the buffer
    env->SetByteArrayRegion(buffer, 0, length, (jbyte *) data);
    check_exp();

    // method: void OutputStream#write(byte[])
    jclass clazz = env->GetObjectClass(fos);
    check_exp();

    jmethodID writer = env->GetMethodID(clazz, "write", "([B)V");
    check_exp();

    env->CallVoidMethod(fos, writer, buffer);
    check_exp();
}

void write_padding(JNIEnv *env, jobject fos, unsigned long item_size, unsigned int page_size) {
    auto count = padding_size((unsigned int) item_size, page_size);
    if (count == 0)
        return;

    auto buf = (char *) malloc(count);
    finally free_buf([&]{
        free(buf);
    });

    write_bytes(env, fos, buf, count);
}