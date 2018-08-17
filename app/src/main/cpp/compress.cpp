#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"
#include "util.h"
#include <vector>
#include <thread>
#include <future>
#include <tuple>

void Image::compress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return compress_ramdisk_gzip();
        case comp::none:
            return; // uncompressed
        default:
            throw comp_exception("Method '" + comp::name(comp_mode) + "' is not supported");
    }
}

void Image::compress_ramdisk_gzip() {
    size_t jobs = std::max<unsigned>(std::min<unsigned>(std::thread::hardware_concurrency(), 1), 4);
    std::vector<std::future<gzip::DataList>> threads;
    threads.reserve(jobs);
    size_t split_len = ramdisk->len / jobs;

    for (size_t i = 0; i < jobs; i++) {
        threads.push_back(std::async(std::launch::async, [&, i]{
            gzip::Comp comp(gzip::Comp::Level::Max, false);
            if (!comp.IsSucc()) {
                throw comp_exception("Error preparing to compress gzip ramdisk with gzip");
            }

            gzip::Data to_comp_data(new gzip::DataBlock, [](gzip::DataBlock *p) {
                delete p;
            });

            to_comp_data->ptr = (char *) ramdisk->data + (split_len * i);

            if (i == jobs - 1) {
                to_comp_data->size = (ramdisk->data + ramdisk->len) - (byte *) to_comp_data->ptr;
            } else{
                to_comp_data->size = split_len;
            }

            if (i < jobs - 1) {
                auto flushed_data = comp.Process(to_comp_data, Z_SYNC_FLUSH);
                return flushed_data;
            } else {
                auto finished_data = comp.Process(to_comp_data, Z_FINISH);
                return finished_data;
            }
        }));
    }

    std::vector<gzip::DataList> blockLists;
    blockLists.reserve(jobs);
    size_t total_len = 0;
    for (auto &thread : threads) {
        auto list = thread.get();

        for (auto &block : list) {
            total_len += block->size;
        }

        blockLists.push_back(list);
    }

    byte header[10] = {
            31,                // Magic number (short)
            139,               // Magic number (short)
            8,                 // Compression method (CM)
            0,                 // Flags (FLG)
            0,                 // Modification time MTIME (int)
            0,                 // Modification time MTIME (int)
            0,                 // Modification time MTIME (int)
            0,                 // Modification time MTIME (int)
            2,                 // Extra flags (XFLG)
            3                  // Operating system (OS)
    };

    // uncompressed crc32
    uLong crc = crc32(0L, ramdisk->data, (uInt) ramdisk->len);
    uLong ulen = ramdisk->len;

    ramdisk->resize(sizeof(header) + total_len + (sizeof(uint32_t) * 2));
    ramdisk->reset_pos();
    ramdisk->write(header);

    for (auto &blockList : blockLists) {
        for (auto &block : blockList) {
            ramdisk->write(block->ptr, block->size);
        }
    }

    // uncompressed crc32
    ramdisk->write_u32le(crc);

    // uncompressed length
    ramdisk->write_u32le(ulen);
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1compressRamdisk(JNIEnv *env, jobject, jlong handle, jbyte comp_mode) {
    try {
        Image *image = (Image*) handle;
        image->compress_ramdisk(comp_mode);
    } catch (...) {
        rethrow(env);
    }
}
