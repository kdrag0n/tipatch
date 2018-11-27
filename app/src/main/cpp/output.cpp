#include "java.h"
#include "image.h"
#include "io.h"
#include "const.h"
#include "bootimg.h"
#include <vector>

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image_nvWrite(JNIEnv *env, jobject, jlong handle, jobject fos) {
    try {
        Image *image = (Image*) handle;

        // update header information
        image->hdr.kernel_size = (uint32_t) image->kernel->len;
        image->hdr.ramdisk_size = (uint32_t) image->ramdisk->len;
        image->hdr.second_size = (uint32_t) image->second->len;
        if (image->hdr.v0v1.dt_size > boot::max_hdr_version) {
            image->hdr.v0v1.dt_size = (uint32_t) image->device_tree->len;
        } else if (image->hdr.v0v1.header_version >= 1) {
            image->hdr.recovery_dtbo_size = (uint32_t) image->recovery_dtbo->len;
        }

        // checksum
        auto hash = image->hash();
        memcpy(image->hdr.id, &hash, sizeof(hash));

        // write it all out
        auto hdr_data = (byte *) &image->hdr;
        write_bytes(env, fos, hdr_data, sizeof(boot_img_hdr_v1));
        write_padding(env, fos, sizeof(boot_img_hdr_v1), image->hdr.page_size);

        write_bytes(env, fos, image->kernel->data, image->kernel->len);
        write_padding(env, fos, image->kernel->len, image->hdr.page_size);

        write_bytes(env, fos, image->ramdisk->data, image->ramdisk->len);
        write_padding(env, fos, image->ramdisk->len, image->hdr.page_size);

        if (image->second->len > 0) {
            write_bytes(env, fos, image->second->data, image->second->len);
            write_padding(env, fos, image->second->len, image->hdr.page_size);
        }

        if (image->hdr.v0v1.header_version > boot::max_hdr_version && image->device_tree->len > 0) {
            write_bytes(env, fos, image->device_tree->data, image->device_tree->len);
            write_padding(env, fos, image->device_tree->len, image->hdr.page_size);
        } else if (image->hdr.v0v1.header_version >= 1 && image->recovery_dtbo->len > 0) {
            write_bytes(env, fos, image->recovery_dtbo->data, image->recovery_dtbo->len);
            write_padding(env, fos, image->recovery_dtbo->len, image->hdr.page_size);
        }
    } catch (...) {
        rethrow(env);
    }
}