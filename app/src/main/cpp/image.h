#pragma once

#include <string>
#include "bootimg.h"
#include "util.h"

class img_exception : public std::runtime_error {
public:
    img_exception(const std::string &what) : std::runtime_error(what) {}
};

class Image {
public:
    void decompress_ramdisk(char comp_mode);
    void compress_ramdisk(char comp_mode);
    void patch_ramdisk(char direction);

    byte *hash();

    boot_img_hdr hdr;

    byte_obj kernel;
    byte_obj ramdisk;
    byte_obj second;
    byte_obj device_tree;

private:
    void decompress_ramdisk_gzip();
    void decompress_ramdisk_lzo();
    void decompress_ramdisk_xz();

    void compress_ramdisk_gzip();
    void compress_ramdisk_lzo();
    void compress_ramdisk_xz();
};