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
        case comp::xz:
            return compress_ramdisk_xz();
        case comp::none:
            return; // uncompressed
        default:
            // this isn't comp_exception because it's a problem with the input
            throw img_exception("Ramdisk compression mode '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::compress_ramdisk_gzip() {
    size_t jobs = 4;
    std::vector<std::future<gzip::Data>> threads;
    threads.reserve(jobs);
    size_t split_len = ramdisk->len / jobs;

    for (size_t i = 0; i < jobs; i++) {
        threads.push_back(std::async(std::launch::async, [&, i]{
            gzip::Comp comp(gzip::Comp::Level::Max, false);
            if (!comp.IsSucc()) {
                throw comp_exception("Error preparing to compress gzip ramdisk with gzip.");
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
                auto flushed_data = gzip::ExpandDataList(comp.Process(to_comp_data, Z_SYNC_FLUSH));
                return flushed_data;
            } else {
                auto finished_data = gzip::ExpandDataList(comp.Process(to_comp_data, Z_FINISH));
                return finished_data;
            }
        }));
    }

    std::vector<gzip::Data> blocks;
    blocks.reserve(jobs * 2);
    size_t total_len(0);
    for (auto &thread : threads) {
        auto data = thread.get();

        blocks.push_back(data);
        total_len += data->size;
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

    auto final = byte_array(sizeof(header) + total_len + (sizeof(uint32_t) * 2));
    final.write(header);

    for (auto &block : blocks) {
        final.write(block->ptr, block->size);
    }

    // uncompressed crc32
    uLong crc = crc32(0L, ramdisk->data, (uInt) ramdisk->len);
    write_uint32(final.pos, crc);
    final.pos += sizeof(uint32_t);

    // uncompressed length
    uLong ulen = ramdisk->len;
    write_uint32(final.pos, ulen);

    ramdisk = final.as_ref();
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
    unsigned char __LZO_MMODEL out_buf[out_len];

    HEAP_ALLOC(wrkmem, LZO1X_MEM_COMPRESS);

    std::vector<byte_array> blocks;
    blocks.reserve(in_len / block_size);
    finally free_buffers([&blocks]{
        for (auto &buf : blocks) {
            free(buf.data);
        }
    });

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

        auto block_out_len = sizeof(read_len) + sizeof(uncomp_check) + sizeof(comp_size) + sizeof(comp_check) + out_len;
        auto block_out_buf = (byte *) malloc(block_out_len);
        auto block_out_pos = block_out_buf;

        // big endian...
        auto swapped = __builtin_bswap32(read_len);
        memcpy(block_out_pos, &swapped, sizeof(read_len));
        block_out_pos += sizeof(read_len);

        swapped = __builtin_bswap32(out_len);
        memcpy(block_out_pos, &swapped, sizeof(out_len));
        block_out_pos += sizeof(out_len);

        swapped = __builtin_bswap32(uncomp_check);
        memcpy(block_out_pos, &swapped, sizeof(uncomp_check));
        block_out_pos += sizeof(uncomp_check);

        swapped = __builtin_bswap32(comp_check);
        memcpy(block_out_pos, &swapped, sizeof(comp_check));
        block_out_pos += sizeof(comp_check);

        memcpy(block_out_pos, out_buf, out_len);
        blocks.push_back(byte_array(block_out_buf, block_out_len));

        in_data += read_len;
        in_len -= read_len;
        out_len = orig_out_len;
        total_block_len += block_out_len;
    }
    
    lzop_header hdr;

    // big endian
    hdr.version = __builtin_bswap16(hdr.version);
    hdr.lib_version = __builtin_bswap16(hdr.lib_version);
    hdr.version_needed_to_extract = __builtin_bswap16(hdr.version_needed_to_extract);
    hdr.flags = __builtin_bswap32(hdr.flags);
    hdr.mode = __builtin_bswap32(hdr.mode);
    hdr.mtime_low = __builtin_bswap32(hdr.mtime_low);
    hdr.mtime_high = __builtin_bswap32(hdr.mtime_high);

    auto header_data = (byte *) &hdr;
    auto header_check = lzo_adler32(1L, header_data, sizeof(lzop_header));
    header_check = __builtin_bswap32(header_check);

    auto final_buf = (byte *) malloc(sizeof(lzop_magic) + sizeof(lzop_header) + sizeof(header_check) + total_block_len);
    auto final_pos = final_buf;

    memcpy(final_pos, lzop_magic, sizeof(lzop_magic));
    final_pos += sizeof(lzop_magic);

    memcpy(final_pos, header_data, sizeof(lzop_header));
    final_pos += sizeof(lzop_header);

    memcpy(final_pos, &header_check, sizeof(header_check));
    final_pos += sizeof(header_check);

    for (auto &buf : blocks) {
        memcpy(final_pos, buf.data, buf.len);
        final_pos += buf.len;
    }

    ramdisk = byte_array::ref(final_buf, total_block_len);
}

void Image::compress_ramdisk_xz() {
    throw comp_exception("Ramdisk compression mode 'xz' is not supported.");
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
