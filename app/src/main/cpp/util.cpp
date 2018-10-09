#include <string>
#include "util.h"

byte_array::byte_array(size_t len) {
    data = (byte *) malloc(len);
    initial_data = data;
    pos = data;
    this->len = len;
}

byte_array::~byte_array() {
    free(initial_data);
}

void byte_array::write(const void *src, size_t src_len) {
    if (pos + src_len > data + len) {
        throw std::out_of_range("Attempting to write " + std::to_string(src_len) + " bytes to byte array of length " + std::to_string(len) + "; however, " + std::to_string(pos - data) + " bytes have already been used");
    }

    memcpy(pos, src, src_len);
    pos += src_len;
}

void byte_array::resize(size_t new_len) {
    if (new_len == len)
        return;

    if (new_len == 0)
        throw std::out_of_range("Attempting to resize byte array of length " + std::to_string(len) + " to 0 bytes");

    auto ret = realloc(initial_data, new_len);
    if (ret == nullptr)
        throw std::bad_alloc();

    auto old_data = data;
    data = (byte *) ret;
    initial_data = data;
    pos = data + (pos - old_data);
    len = new_len;
}

void byte_array::reset_pos() {
    pos = data;
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
    auto that = ref(data, len, false);
    initial_data = nullptr;
    data = nullptr;
    pos = nullptr;
    len = 0;

    return that;
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

void write_uint32le(byte *dst, unsigned long orig) {
    dst[0] = (byte) orig;
    dst[1] = (byte) (orig >> 8);
    dst[2] = (byte) (orig >> 16);
    dst[3] = (byte) (orig >> 24);
}

void write_uint32be(byte *dst, unsigned int orig) {
    dst[0] = (byte) ((orig >> 24) & 0xFF);
    dst[1] = (byte) ((orig >> 16) & 0xFF);
    dst[2] = (byte) ((orig >> 8) & 0xFF);
    dst[3] = (byte) (orig & 0xFF);
}