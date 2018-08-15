#include "util.h"

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

void write_uint32(char *dst, unsigned long orig) {
    dst[0] = (unsigned char) ((orig >> 24) & 0xFF);
    dst[1] = (unsigned char) ((orig >> 16) & 0xFF);
    dst[2] = (unsigned char) ((orig >> 8) & 0xFF);
    dst[3] = (unsigned char) (orig & 0xFF);
}