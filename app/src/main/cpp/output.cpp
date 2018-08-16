#include "java.h"
#include "image.h"
#include "io.h"
#include "const.h"
#include <vector>

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1write(JNIEnv *env, jobject, jlong handle, jobject fos) {
    try {
        Image *image = (Image*) handle;

        // update header information
        image->hdr.kernel_size = (uint32_t) image->kernel->len;
        image->hdr.ramdisk_size = (uint32_t) image->ramdisk->len;
        image->hdr.second_size = (uint32_t) image->second->len;
        image->hdr.dt_size = (uint32_t) image->device_tree->len;

        // checksum
        auto hash = image->hash();
        if (hash != nullptr)
            memcpy(image->hdr.id, hash, 8);

        // write it all out
        auto hdr_data = (byte *) &image->hdr;
        write_bytes(env, fos, hdr_data, sizeof(boot_img_hdr));
        write_padding(env, fos, sizeof(boot_img_hdr), image->hdr.page_size);

        write_bytes(env, fos, image->kernel->data, image->kernel->len);
        write_padding(env, fos, image->kernel->len, image->hdr.page_size);

        write_bytes(env, fos, image->ramdisk->data, image->ramdisk->len);
        write_padding(env, fos, image->ramdisk->len, image->hdr.page_size);

        if (image->second->len > 0) {
            write_bytes(env, fos, image->second->data, image->second->len);
            write_padding(env, fos, image->second->len, image->hdr.page_size);
        }

        if (image->device_tree->len > 0) {
            write_bytes(env, fos, image->device_tree->data, image->device_tree->len);
            write_padding(env, fos, image->device_tree->len, image->hdr.page_size);
        }
    } catch (...) {
        rethrow(env);
    }
}