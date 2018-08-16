#include <string>
#include "util.h"

byte_array::byte_array(size_t len) {
    data = (byte *) malloc(len);
    pos = data;
    this->len = len;
}

byte_array::~byte_array() {
    free(data);
}

void byte_array::write(const void *src, size_t src_len) {
    if (pos + src_len > data + len) {
        throw std::out_of_range("Attempting to write " + std::to_string(src_len) + "to byte array of length " + std::to_string(len) + "; however, " + std::to_string(pos - data) + " bytes have already been used.");
    }

    memcpy(data, src, len);
    pos += len;
}

byte_obj byte_array::ref(byte *data, size_t len, bool copy) {
    byte *to_ref = data;
    if (copy) {
        to_ref = (byte *) malloc(len);
        memcpy(to_ref, data, len);
    }

    return std::make_shared<byte_array>(to_ref, len);
}

byte_obj byte_array::as_ref() {
    return ref(data, len, false);
}

finally::finally(const std::function<void(void)> &functor) : functor(functor) {}

finally::~finally() {
    functor();
}

#ifndef NDEBUG
Timer::Timer() : beg_(clock_::now()) {}

void Timer::reset() {
    beg_ = clock_::now();
}

double Timer::elapsed() const {
    return std::chrono::duration_cast<second_>(clock_::now() - beg_).count();
}
#endif

void write_uint32(byte *dst, unsigned long orig) {
    dst[0] = (byte) orig;
    dst[1] = (byte) (orig >> 8);
    dst[2] = (byte) (orig >> 16);
    dst[3] = (byte) (orig >> 24);
}