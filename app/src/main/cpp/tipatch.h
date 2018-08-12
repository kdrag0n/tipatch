#pragma once

#include <string>
#include "image.h"

class Image {
public:
    std::string board;
    std::string cmdline;
    uint32_t os_version;

    uint32_t base;
    uint32_t kernel_offset;
    uint32_t ramdisk_offset;
    uint32_t second_offset;
    uint32_t tags_offset;
    uint32_t page_size;

    std::string kernel;
    std::string ramdisk;
    std::string second;
    std::string device_tree;
};