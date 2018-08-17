#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"
#include "lzo.h"
#include "util.h"
#include <vector>
#include <thread>
#include <future>
#include <tuple>

void Image::compress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return compress_ramdisk_gzip();
        case comp::lzo:
            return compress_ramdisk_lzo();
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

void Image::compress_ramdisk_lzo() {
    auto ret = lzo_init();
    if (ret != LZO_E_OK)
        throw comp_exception("Failed to initialize LZO compressor. (error code " + std::to_string(ret) + ")");

    auto in_data = ramdisk->data;
    auto in_len = ramdisk->len;

    unsigned int block_size = BLOCK_SIZE;
    lzo_uint lblock_size = block_size;
    auto orig_out_len = lblock_size + lblock_size / 16 + 64 + 3;
    auto out_len = orig_out_len;
    unsigned char __LZO_MMODEL last_out_buf[out_len];
    unsigned char __LZO_MMODEL out_buf[out_len];

    HEAP_ALLOC(wrkmem, LZO1X_MEM_COMPRESS);

    ramdisk->resize(sizeof(lzop_header) + sizeof(uint32_t) + (orig_out_len * (in_len / block_size)));
    ramdisk->reset_pos();

    lzop_header hdr;
    memcpy(hdr.magic, lzop_magic, sizeof(hdr.magic));

    // big endian
    hdr.version = __builtin_bswap16(hdr.version);
    hdr.lib_version = __builtin_bswap16(hdr.lib_version);
    hdr.version_needed_to_extract = __builtin_bswap16(hdr.version_needed_to_extract);
    hdr.flags = __builtin_bswap32(hdr.flags);
    hdr.mode = __builtin_bswap32(hdr.mode);
    hdr.mtime_low = __builtin_bswap32(hdr.mtime_low);
    hdr.mtime_high = __builtin_bswap32(hdr.mtime_high);

    ramdisk->write(hdr);

    auto header_check = lzo_adler32(1L, (byte *) &hdr, sizeof(lzop_header));
    ramdisk->write_u32be(header_check);

    unsigned int total_block_len = 0;

    for (unsigned int blocks_processed = 0;; blocks_processed++) {
        auto read_len = std::max(std::min<unsigned int>(block_size, (unsigned int) in_len), static_cast<unsigned int>(0));
        if (read_len == 0) {
            auto block_out = (byte *) malloc(sizeof(read_len));

            // big endian...
            auto swapped = __builtin_bswap32(read_len);
            memcpy(block_out, &swapped, sizeof(read_len));
            blocks.push_back(byte_array(block_out, sizeof(read_len)));
            break;
        }

        auto uncomp_check = lzo_adler32(1L, in_data, read_len);

        ret = lzo1x_1_compress(in_data, read_len, out_buf, &out_len, wrkmem);
        if (ret != LZO_E_OK)
            throw comp_exception("Failed to compress ramdisk block #" + std::to_string(blocks_processed) + " with LZO. (error code " + std::to_string(ret) + ")");

        auto comp_size = (uint32_t) out_len;
        auto comp_check = lzo_adler32(1L, out_buf, out_len);

        ramdisk->write_u32be(read_len);
        ramdisk->write_u32be((unsigned int) out_len);
        ramdisk->write_u32be(uncomp_check);
        ramdisk->write_u32be(comp_check);
        ramdisk->write(out_buf, out_len);

        in_data += read_len;
        in_len -= read_len;
        out_len = orig_out_len;
    }
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
