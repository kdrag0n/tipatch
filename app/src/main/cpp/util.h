#pragma once

#include <functional>
#ifndef NDEBUG
#include <chrono>
#endif

using byte = unsigned char;

class byte_array {
public:
    byte_array(byte *data = nullptr, size_t len = 0) : data(data), len(len) {};
    byte_array(size_t len);
    ~byte_array();

    void write(void *src, size_t src_len);

    byte *data;
    byte *pos;
    size_t len;

    static std::shared_ptr<byte_array> ref(byte *data, size_t len, bool copy = false);
};

using byte_obj = std::shared_ptr<byte_array>;

class finally {
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

void write_uint32(byte *dst, unsigned long orig);