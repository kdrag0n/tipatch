#pragma once

#include <functional>
#ifndef NDEBUG
#include <chrono>
#endif

class finally  {
    std::function<void(void)> functor;
public:
    finally(const std::function<void(void)> &functor);
    ~finally();
};

#ifndef NDEBUG
class Timer {
public:
    Timer();
    void reset();
    double elapsed() const;

private:
    typedef std::chrono::high_resolution_clock clock_;
    typedef std::chrono::duration<double, std::ratio<1>> second_;
    std::chrono::time_point<clock_> beg_;
};
#endif