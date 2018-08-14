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