#include "const.h"

std::string comp::name(const char mode) {
    switch (mode) {
        case gzip:
            return "gzip";
        case lz4:
            return "lz4";
        case lzo:
            return "lzo";
        case xz:
            return "xz";
        case bzip2:
            return "bzip2";
        case lzma:
            return "lzma";
        default:
            return "unknown";
    }
}