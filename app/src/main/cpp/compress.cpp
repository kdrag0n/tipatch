#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"
#include "util.h"
#include <thread>
#include <vector>
#include <future>
#include <tuple>

void Image::compress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return compress_ramdisk_gzip();
        case comp::lzo:
            return compress_ramdisk_lzo();
        case comp::xz:
            return compress_ramdisk_xz();
        default:
            throw img_exception("Ramdisk compression mode '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::compress_ramdisk_gzip() {
    size_t jobs = 4;
    std::vector<std::future<std::tuple<gzip::Data, gzip::Data>>> threads;
    threads.reserve(jobs);
    size_t split_len = ramdisk->length() / jobs;

    for (size_t i = 0; i < jobs; i++) {
        threads.push_back(std::async(std::launch::async, [&, i]{
            gzip::Comp comp(gzip::Comp::Level::Max, i == 0);
            if (!comp.IsSucc()) {
                throw img_exception("Error preparing to compress gzip ramdisk.");
            }

            gzip::Data to_comp_data(new gzip::DataBlock, [](gzip::DataBlock *p) {
                delete p;
            });

            to_comp_data->ptr = ramdisk->data() + (split_len * i);

            if (i == jobs - 1) {
                to_comp_data->size = ramdisk->end().base() - to_comp_data->ptr;
            } else{
                to_comp_data->size = split_len;
            }

            if (i < jobs - 1) {
                auto flushed_data = comp.Process(to_comp_data, Z_SYNC_FLUSH);
                return std::make_tuple(gzip::ExpandDataList(flushed_data), gzip::AllocateData(0));
                /*
                auto flushed_data = comp.Process(to_comp_data, Z_NO_FLUSH);
                auto finished_data = gzip::AllocateData(0);

                return std::make_tuple(gzip::ExpandDataList(flushed_data), finished_data);*/
            } else {
                auto finished_data = comp.Process(to_comp_data, Z_FINISH);
                return std::make_tuple(gzip::ExpandDataList(finished_data), gzip::AllocateData(0));
            }
        }));
    }

    std::vector<gzip::Data> blocks;
    blocks.reserve(jobs * 2);
    size_t total_len(0);
    for (auto &thread : threads) {
        gzip::Data flushed_data;
        gzip::Data finished_data;

        std::tie(flushed_data, finished_data) = thread.get();

        blocks.push_back(flushed_data);
        total_len += flushed_data->size;

        blocks.push_back(finished_data);
        total_len += finished_data->size;
    }

    char *final_buf = (char *) malloc(total_len + sizeof(uLong));
    finally free_buf([&]{
        free(final_buf);
    });

    char *cur_pos = final_buf;
    for (auto &block : blocks) {
        memcpy(cur_pos, block->ptr, block->size);
        cur_pos += block->size;
    }

    uLong crc = crc32(0L, (Bytef *) ramdisk->data(), ramdisk->length());
    memcpy(cur_pos, (void *) &crc, sizeof(uLong));

    ramdisk = std::make_shared<std::string>(final_buf, total_len);
}

void Image::compress_ramdisk_lzo() {
    throw img_exception("Ramdisk compression mode 'lzo' is not supported.");
}

void Image::compress_ramdisk_xz() {
    throw img_exception("Ramdisk compression mode 'xz' is not supported.");
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
