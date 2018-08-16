#pragma once

#include <functional>
#ifndef NDEBUG
#include <chrono>
#endif

using byte = unsigned char;

class byte_array {
public:
    byte_array(byte *data = nullptr, size_t len = 0) : data(data), pos(data), len(len) {};
    byte_array(size_t len);
    ~byte_array();

    void write(const void *src, size_t src_len);
    template <typename T> void write(const T &obj) {
        write(reinterpret_cast<const void *>(&obj), sizeof(obj));
    }

    byte *data;
    byte *pos;
    size_t len;

    static std::shared_ptr<byte_array> ref(byte *data, size_t len, bool copy = false);
    std::shared_ptr<byte_array> as_ref();
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