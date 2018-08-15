#pragma once
#include "lzo/minilzo.h"

#define HEAP_ALLOC(var,size) \
    lzo_align_t __LZO_MMODEL var [ ((size) + (sizeof(lzo_align_t) - 1)) / sizeof(lzo_align_t) ]

#define BLOCK_SIZE (256 * 1024l)

static const unsigned char lzop_magic[9] =
        {(unsigned char) '\211', 'L', 'Z', 'O', '\0', '\r', '\n', '\032', '\n'};

struct lzop_header {
    uint16_t version = 0;
    uint16_t lib_version = 0;
    uint16_t version_needed_to_extract = 0;
    unsigned char method = 0;
    unsigned char level = 0;
    lzo_uint32 flags = 0;
    lzo_uint32 mode = 0;
    lzo_uint32 mtime_low = 0;
    lzo_uint32 mtime_high = 0;
    uint8_t name_len = 0;
} __attribute__((packed));
