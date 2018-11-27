#pragma once

#include <string>
#include "bootimg.h"
#include "util.h"

class img_exception : public std::runtime_error {
public:
    img_exception(const std::string &what) : std::runtime_error(what) {}
};

class img_hdr_exception : public std::runtime_error {
public:
    img_hdr_exception(const std::string &what) : std::runtime_error(what) {}
};

class Image {
public:
    void decompress_ramdisk(char comp_mode);
    void compress_ramdisk(char comp_mode);
    void patch_ramdisk(char direction);

    unsigned long hash();

    boot_img_hdr_v1 hdr;

    byte_obj kernel;
    byte_obj ramdisk;
    byte_obj second;
    byte_obj device_tree;
    byte_obj recovery_dtbo;

private:
    void decompress_ramdisk_gzip();

    void compress_ramdisk_gzip();
};