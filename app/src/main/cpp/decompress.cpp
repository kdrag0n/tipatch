#include "image.h"
#include "gzip.h"
#include "const.h"
#include "java.h"

void Image::decompress_ramdisk(char comp_mode) {
    switch (comp_mode) {
        case comp::gzip:
            return decompress_ramdisk_gzip();
        case comp::none:
            return;
        default:
            throw comp_exception("Method '" + comp::name(comp_mode) + "' is not supported.");
    }
}

void Image::decompress_ramdisk_gzip() {
    gzip::Decomp decomp;
    if (!decomp.IsSucc()) {
        throw comp_exception("Error preparing to decompress gzip ramdisk.");
    }

    gzip::Data comp_data(new gzip::DataBlock, [](gzip::DataBlock *p) {
        delete p;
    });

    comp_data->ptr = (char *) ramdisk->data;
    comp_data->size = ramdisk->len;

    bool success;
    gzip::DataList data_list;
    std::tie(success, data_list) = decomp.Process(comp_data);

    if (!success) {
        throw img_exception("Unable to decompress gzip ramdisk.");
    }

    size_t decomp_len = 0;
    for (auto &block : data_list) {
        decomp_len += block->size;
    }

    ramdisk->resize(decomp_len);
    ramdisk->reset_pos();
    for (auto &block : data_list) {
        ramdisk->write(block->ptr, block->size);
    }
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
