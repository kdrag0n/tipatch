#pragma once
#include "lzo/minilzo.h"

#define HEAP_ALLOC(var,size) \
    lzo_align_t __LZO_MMODEL var [ ((size) + (sizeof(lzo_align_t) - 1)) / sizeof(lzo_align_t) ]

#define BLOCK_SIZE (256 * 1024l)

static const byte lzop_magic[9] =
        {(byte) '\211', 'L', 'Z', 'O', '\0', '\r', '\n', '\032', '\n'};

struct lzop_header {
    uint16_t version = 0x1040 & 0xffff;
    uint16_t lib_version = (uint16_t) (LZO_VERSION & 0xffff);
    uint16_t version_needed_to_extract = 0x0940;
    byte method = 1; // lzo1x_1
    byte level = 5; // lzo1x_1
    lzo_uint32 flags = (0x03000000L & 0xff000000L) | // OS: Unix
            (0x00000000L & 0xff000000L); // Character encoding: Native
    lzo_uint32 mode = 0;
    lzo_uint32 mtime_low = 0;
    lzo_uint32 mtime_high = 0;
    uint8_t name_len = 0;
} __attribute__((packed));
