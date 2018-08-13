#include "java.h"
#include "image.h"
#include "io.h"
#include "const.h"

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1write(JNIEnv *env, jobject, jlong handle, jobject fos) {
    try {
        Image *image = (Image*) handle;

        // update header information
        image->hdr.kernel_size = (uint32_t) image->kernel->length();
        image->hdr.ramdisk_size = (uint32_t) image->ramdisk->length();
        image->hdr.second_size = (uint32_t) image->second->length();
        image->hdr.dt_size = (uint32_t) image->device_tree->length();

        // checksum
        auto hash = image->hash();
        if (hash != NULL)
            memcpy(image->hdr.id, hash, 8);

        // write it all out
        char *hdr_data = (char *) &image->hdr;
        write_bytes(env, fos, hdr_data, sizeof(boot_img_hdr));
        write_padding(env, fos, sizeof(boot_img_hdr), image->hdr.page_size);

        write_bytes(env, fos, image->kernel->data(), image->kernel->length());
        write_padding(env, fos, image->kernel->length(), image->hdr.page_size);

        write_bytes(env, fos, image->ramdisk->data(), image->ramdisk->length());
        write_padding(env, fos, image->ramdisk->length(), image->hdr.page_size);

        if (image->second->length() > 0) {
            write_bytes(env, fos, image->second->data(), image->second->length());
            write_padding(env, fos, image->second->length(), image->hdr.page_size);
        }

        if (image->device_tree->length() > 0) {
            write_bytes(env, fos, image->device_tree->data(), image->device_tree->length());
            write_padding(env, fos, image->device_tree->length(), image->hdr.page_size);
        }
    } catch (...) {
        rethrow(env);
    }
}