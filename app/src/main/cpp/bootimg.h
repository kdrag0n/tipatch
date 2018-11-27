/* tools/mkbootimg/bootimg.h
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include <stdint.h>
#include "util.h"

#pragma once

namespace boot {
    constexpr auto magic = "ANDROID!";
    constexpr int magic_size = 8;
    constexpr int name_size = 16;
    constexpr int args_size = 512;
    constexpr int extra_args_size = 1024;
    constexpr int max_hdr_version = 4;
}

struct boot_img_hdr_v0 {
    uint8_t magic[boot::magic_size];

    uint32_t kernel_size;  /* size in bytes */
    uint32_t kernel_addr;  /* physical load addr */

    uint32_t ramdisk_size; /* size in bytes */
    uint32_t ramdisk_addr; /* physical load addr */

    uint32_t second_size;  /* size in bytes */
    uint32_t second_addr;  /* physical load addr */

    uint32_t tags_addr;    /* physical addr for kernel tags */
    uint32_t page_size;    /* flash page size we assume */

    union {
        uint32_t dt_size;      /* device tree in bytes */
        uint32_t header_version;
    } v0v1;

    /* operating system version and security patch level; for
     * version "A.B.C" and patch level "Y-M-D":
     * ver = A << 14 | B << 7 | C         (7 bits for each of A, B, C)
     * lvl = ((Y - 2000) & 127) << 4 | M  (7 bits for Y, 4 bits for M)
     * os_version = ver << 11 | lvl */
    uint32_t os_version;

    uint8_t name[boot::name_size]; /* asciiz product name */

    uint8_t cmdline[boot::args_size];

    byte id[32]; /* timestamp / checksum / sha1 / etc */

    /* Supplemental command line data; kept here to maintain
     * binary compatibility with older versions of mkbootimg */
    uint8_t extra_cmdline[boot::extra_args_size];
} __attribute__((packed));

struct boot_img_hdr_v1 : public boot_img_hdr_v0 {
    uint32_t recovery_dtbo_size;   /* size in bytes for recovery DTBO image */
    uint64_t recovery_dtbo_offset; /* physical load addr */
    uint32_t header_size;
} __attribute__((packed));