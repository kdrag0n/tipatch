#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"
#include "lzo.h"
#include "util.h"
#include <vector>

void Image::compress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return compress_ramdisk_gzip();
        case comp::lzo:
            return compress_ramdisk_lzo();
        case comp::xz:
            return compress_ramdisk_xz();
        default:
            // this isn't comp_exception because it's a problem with the input
            throw img_exception("Ramdisk compression mode '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::compress_ramdisk_gzip() {
    gzip::Comp comp(gzip::Comp::Level::Max, true);
    if (!comp.IsSucc()) {
        throw comp_exception("Error preparing to compress gzip ramdisk.");
    }

    gzip::Data toCompData(new gzip::DataBlock, [](gzip::DataBlock *p) {
        delete p;
    });

    toCompData->ptr = ramdisk->data();
    toCompData->size = ramdisk->length();

    gzip::DataList data_list = comp.Process(toCompData, true);

    auto compData = gzip::ExpandDataList(data_list);
    ramdisk = std::make_shared<std::string>(compData->ptr, compData->size);
}

void Image::compress_ramdisk_lzo() {
    auto ret = lzo_init();
    if (ret != LZO_E_OK)
        throw comp_exception("Failed to initialize LZO compressor. (error code " + std::to_string(ret) + ")");

    auto in_data = (unsigned char *) ramdisk->data();
    auto in_len = ramdisk->length();

    unsigned int block_size = BLOCK_SIZE;
    lzo_uint lblock_size = block_size;
    auto orig_out_len = lblock_size + lblock_size / 16 + 64 + 3;
    auto out_len = orig_out_len;
    unsigned char __LZO_MMODEL out_buf[out_len];

    HEAP_ALLOC(wrkmem, LZO1X_MEM_COMPRESS);

    std::vector<byte_array> blocks;
    blocks.reserve(in_len / block_size);
    finally free_buffers([&blocks]{
        for (auto buf : blocks) {
            free(buf.data);
        }
    });

    unsigned int total_block_len = 0;

    for (unsigned int blocks_processed = 0;; blocks_processed++) {
        auto read_len = std::max(std::min<unsigned int>(block_size, in_len), static_cast<unsigned int>(0));
        if (read_len == 0) {
            auto block_out = (char *) malloc(sizeof(read_len));

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

        uint32_t comp_size = out_len;
        auto comp_check = lzo_adler32(1L, out_buf, out_len);

        auto block_out_len = sizeof(read_len) + sizeof(uncomp_check) + sizeof(comp_size) + sizeof(comp_check) + out_len;
        auto block_out_buf = (char *) malloc(block_out_len);
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
    hdr.version = 0x1040 & 0xffff;
    hdr.version_needed_to_extract = 0x0940;
    hdr.lib_version = lzo_version() & 0xffff;
    hdr.method = (unsigned char) 1; // lzo1x_1
    hdr.level = (unsigned char) 5; // lzo1x_1

    hdr.flags = 0;
    hdr.flags |= 0x03000000L & 0xff000000L; // Unix
    hdr.flags |= 0x00000000L & 0xff000000L; // Native

    // big endian
    hdr.version = __builtin_bswap16(hdr.version);
    hdr.lib_version = __builtin_bswap16(hdr.lib_version);
    hdr.version_needed_to_extract = __builtin_bswap16(hdr.version_needed_to_extract);
    hdr.flags = __builtin_bswap32(hdr.flags);
    hdr.mode = __builtin_bswap32(hdr.mode);
    hdr.mtime_low = __builtin_bswap32(hdr.mtime_low);
    hdr.mtime_high = __builtin_bswap32(hdr.mtime_high);

    auto header_data = (unsigned char *) &hdr;
    auto header_check = lzo_adler32(1L, header_data, sizeof(lzop_header));
    header_check = __builtin_bswap32(header_check);

    auto final_buf = (char *) malloc(sizeof(lzop_magic) + sizeof(lzop_header) + sizeof(header_check) + total_block_len);
    auto final_pos = final_buf;

    memcpy(final_pos, lzop_magic, sizeof(lzop_magic));
    final_pos += sizeof(lzop_magic);

    memcpy(final_pos, header_data, sizeof(lzop_header));
    final_pos += sizeof(lzop_header);

    memcpy(final_pos, &header_check, sizeof(header_check));
    final_pos += sizeof(header_check);

    for (auto buf : blocks) {
        memcpy(final_pos, buf.data, buf.len);
        final_pos += buf.len;
    }

    ramdisk = std::make_shared<std::string>(final_buf, total_block_len);
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
