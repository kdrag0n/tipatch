#pragma once

#include <string>

namespace comp {
    const char gzip       = 0;
    const char lz4        = 1;
    const char lzo        = 2;
    const char xz         = 3;
    const char bzip2      = 4;
    const char lzma       = 5;
    const char unknown    = 6;

    std::string name(const char mode);
}

namespace repl {
    const char normal   = 0;
    const char reverse  = 1;
}