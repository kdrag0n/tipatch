#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"
#include "lzo.h"

void Image::decompress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return decompress_ramdisk_gzip();
        case comp::lzo:
            return decompress_ramdisk_lzo();
        case comp::xz:
            return decompress_ramdisk_xz();
        case comp::none:
            return;
        default:
            throw img_exception("Ramdisk compression mode '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::decompress_ramdisk_gzip() {
    gzip::Decomp decomp;
    if (!decomp.IsSucc()) {
        throw comp_exception("Error preparing to decompress gzip ramdisk.");
    }

    gzip::Data compData(new gzip::DataBlock, [](gzip::DataBlock *p) {
        delete p;
    });

    compData->ptr = ramdisk->data();
    compData->size = ramdisk->length();

    bool success;
    gzip::DataList data_list;
    std::tie(success, data_list) = decomp.Process(compData);

    if (!success) {
        throw img_exception("Unable to decompress gzip ramdisk.");
    }

    auto decompData = gzip::ExpandDataList(data_list);
    ramdisk = std::make_shared<std::string>(decompData->ptr, decompData->size);
}

void Image::decompress_ramdisk_lzo() {
    throw comp_exception("Ramdisk compression mode 'lzo' is not supported.");
}

void Image::decompress_ramdisk_xz() {
    throw comp_exception("Ramdisk compression mode 'xz' is not supported.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1decompressRamdisk(JNIEnv *env, jobject, jlong handle, jbyte comp_mode) {
    try {
        Image *image = (Image*) handle;
        image->decompress_ramdisk(comp_mode);
    } catch (...) {
        rethrow(env);
    }
}
