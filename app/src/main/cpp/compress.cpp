#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"

void Image::compress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return compress_ramdisk_gzip();
        case comp::lzo:
            return compress_ramdisk_lzo();
        case comp::xz:
            return compress_ramdisk_xz();
        default:
            throw img_exception("Ramdisk compression mode '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::compress_ramdisk_gzip() {
    gzip::Comp comp(gzip::Comp::Level::Max, true);
    if (!comp.IsSucc()) {
        throw img_exception("Error preparing to compress gzip ramdisk.");
    }

    gzip::Data toCompData(new gzip::DataBlock, [](gzip::DataBlock *p) {
        delete p;
    });

    toCompData->ptr = ramdisk->data();
    toCompData->size = ramdisk->length();

    gzip::DataList data_list = comp.Process(toCompData, true);

    auto compData = gzip::ExpandDataList(data_list);
    ramdisk = std::make_shared<std::string>(compData->ptr, compData->size);
}

void Image::compress_ramdisk_lzo() {
    throw img_exception("Ramdisk compression mode 'lzo' is not supported.");
}

void Image::compress_ramdisk_xz() {
    throw img_exception("Ramdisk compression mode 'xz' is not supported.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1compressRamdisk(JNIEnv *env, jobject, jlong handle, jbyte comp_mode) {
    try {
        Image *image = (Image*) handle;
        image->compress_ramdisk(comp_mode);
    } catch (...) {
        rethrow(env);
    }
}
