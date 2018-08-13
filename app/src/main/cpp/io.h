#pragma once

#include <string>
#include "java.h"

class jv_bytes {
public:
    jv_bytes(JNIEnv *env, jbyteArray array, jbyte *jbytes, unsigned int len);
    ~jv_bytes();

    char *bytes();
    std::string string();

private:
    JNIEnv *env;
    jbyteArray array;
    jbyte *jbytes;
    unsigned int len;
};

jv_bytes read_bytes(JNIEnv *env, jobject fis, unsigned int count);
void read_padding(JNIEnv *env, jobject fis, unsigned int item_size, unsigned int page_size);