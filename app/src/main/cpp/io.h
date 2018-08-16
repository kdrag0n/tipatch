#pragma once

#include <string>
#include "java.h"
#include "util.h"

class jv_bytes {
public:
    jv_bytes(JNIEnv *env, jbyteArray array, jbyte *jbytes, unsigned int len);
    ~jv_bytes();

    byte *bytes();
    byte *copy_bytes();

    unsigned int len;
private:
    JNIEnv *env;
    jbyteArray array;
    jbyte *jbytes;
};

class io_exception : public std::runtime_error {
public:
    io_exception(const std::string &what) : std::runtime_error(what) {}
};

// read
jv_bytes read_bytes(JNIEnv *env, jobject fis, unsigned int count);
void read_padding(JNIEnv *env, jobject fis, unsigned int item_size, unsigned int page_size);

// write
void write_bytes(JNIEnv *env, jobject fos, byte *data, size_t length);
void write_padding(JNIEnv *env, jobject fos, size_t item_size, unsigned int page_size);