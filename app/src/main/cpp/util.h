#pragma once

#include <functional>
#ifndef NDEBUG
#include <chrono>
#endif

using byte = unsigned char;

void write_uint32le(byte *dst, unsigned long orig);
void write_uint32be(byte *dst, unsigned int orig);

class byte_array {
public:
    byte_array(byte *data = nullptr, size_t len = 0) : data(data), pos(data), len(len) {};
    byte_array(size_t len);
    ~byte_array();

    void write(const void *src, size_t src_len);
    template <typename T> inline void write(const T &obj) {
        write(reinterpret_cast<const void *>(&obj), sizeof(obj));
    }

    inline void write_u32le(unsigned long num) {
        if (pos + sizeof(uint32_t) > data + len) {
            throw std::out_of_range("Attempting to write 4 bytes to byte array of length " + std::to_string(len) + "; however, " + std::to_string(pos - data) + " bytes have already been used");
        }

        write_uint32le(pos, num);
        pos += sizeof(uint32_t);
    }

    inline void write_u32be(unsigned int num) {
        if (pos + sizeof(uint32_t) > data + len) {
            throw std::out_of_range("Attempting to write 4 bytes to byte array of length " + std::to_string(len) + "; however, " + std::to_string(pos - data) + " bytes have already been used");
        }

        write_uint32be(pos, num);
        pos += sizeof(uint32_t);
    }

    byte *data;
    byte *pos;
    size_t len;

    void resize(size_t new_len);
    void reset_pos();

    static std::shared_ptr<byte_array> ref(byte *data, size_t len, bool copy = false);
    std::shared_ptr<byte_array> as_ref();

    byte_array(byte_array&& that) {
        data = that.data;
        pos = that.data; // reset position
        len = that.len;
        that.data = nullptr;
        that.pos = nullptr;
        that.len = 0;
    }

    // too lazy to implement copy-and-swap
    byte_array(const byte_array &) = delete;
    byte_array &operator=(const byte_array &) = delete;
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