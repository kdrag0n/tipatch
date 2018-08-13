#pragma once

#include <string>
#include "image.h"

class Image {
public:
    boot_img_hdr hdr;

    std::shared_ptr<std::string> kernel;
    std::shared_ptr<std::string> ramdisk;
    std::shared_ptr<std::string> second;
    std::shared_ptr<std::string> device_tree;
};