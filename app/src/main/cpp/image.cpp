#include "java.h"
#include "image.h"
#include "io.h"
#include "const.h"
#include "util.h"
#include <zlib.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_kdrag0n_tipatch_jni_Image_init(JNIEnv *env, jobject, jobject fis) {
    try {
        Image *image = new Image();

        // header
        {
            auto hdr_bytes = read_bytes(env, fis, sizeof(boot_img_hdr_v1));
            auto hdr = ((boot_img_hdr_v1 *) hdr_bytes.bytes());

            if (memcmp(hdr->magic, boot::magic, boot::magic_size) != 0)
                throw img_hdr_exception("");

            image->hdr = *hdr;
        }
        read_padding(env, fis, sizeof(boot_img_hdr_v1), image->hdr.page_size);

        // kernel
        {
            auto kernel = read_bytes(env, fis, image->hdr.kernel_size);
            image->kernel = byte_array::ref(kernel.copy_bytes(), kernel.len);
        }
        read_padding(env, fis, image->hdr.kernel_size, image->hdr.page_size);

        // ramdisk
        {
            auto ramdisk = read_bytes(env, fis, image->hdr.ramdisk_size);
            image->ramdisk = byte_array::ref(ramdisk.copy_bytes(), ramdisk.len);
        }
        read_padding(env, fis, image->hdr.ramdisk_size, image->hdr.page_size);

        // second-stage loader
        if (image->hdr.second_size > 0) {
            auto second = read_bytes(env, fis, image->hdr.second_size);
            image->second = byte_array::ref(second.copy_bytes(), second.len);
            read_padding(env, fis, image->hdr.second_size, image->hdr.page_size);
        } else {
            image->second = byte_array::ref(nullptr, 0);
        }

        // device tree or recovery dtbo (v0/v1 header)
        if (image->hdr.v0v1.dt_size > boot::max_hdr_version) {
            auto dt = read_bytes(env, fis, image->hdr.v0v1.dt_size);
            image->device_tree = byte_array::ref(dt.copy_bytes(), dt.len);
            image->recovery_dtbo = byte_array::ref(nullptr, 0);

            read_padding(env, fis, image->hdr.v0v1.dt_size, image->hdr.page_size);
        } else if (image->hdr.v0v1.header_version >= 1 && image->hdr.recovery_dtbo_size > 0) {
            auto recovery_dtbo = read_bytes(env, fis, image->hdr.recovery_dtbo_size);
            image->recovery_dtbo = byte_array::ref(recovery_dtbo.copy_bytes(), recovery_dtbo.len);
            image->device_tree = byte_array::ref(nullptr, 0);

            read_padding(env, fis, image->hdr.recovery_dtbo_size, image->hdr.page_size);
        } else {
            image->device_tree = byte_array::ref(nullptr, 0);
            image->recovery_dtbo = byte_array::ref(nullptr, 0);
        }

        return (jlong) image;
    } catch (...) {
        rethrow(env);
        return (jlong) 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image_free(JNIEnv, jobject, jlong handle) {
    Image *ptr = (Image*) handle;
    delete ptr;
}

extern "C" JNIEXPORT jbyte JNICALL
Java_com_kdrag0n_tipatch_jni_Image_nvDetectCompressor(JNIEnv, jobject, jlong handle) {
    Image *image = (Image*) handle;
    auto data = image->ramdisk->data;
    int b1 = data[0];
    int b2 = data[1];
    int b3 = data[2];
    int b4 = data[3];
    int b5 = data[4];

    if (b1 == 0x42 && b2 == 0x5a) {
        return comp::bzip2;
    } else if (b1 == 0x1f && (b2 == 0x8b || b2 == 0x9e)) {
        return comp::gzip;
    } else if (b1 == 0x04 && b2 == 0x22) {
        return comp::lz4;
    } else if (b1 == 0x89 && b2 == 0x4c) {
        return comp::lzo;
    } else if (b1 == 0x5d && b2 == 0x00) {
        return comp::lzma;
    } else if (b1 == 0xfd && b2 == 0x37) {
        return comp::xz;
    } else if (b1 == 0x07 && b2 == 0x07 && b3 == 0x07) { // legacy cpio: old kernels?
        return comp::none;
    } else if (b1 == 0x30 && b2 == 0x37 && b3 == 0x30 && b4 == 0x37 && b5 == 0x30) {
        // modern cpio: string "07070[X]" being 070701, 070702, or 070707
        return comp::none;
    } else {
        return comp::unknown;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_kdrag0n_tipatch_jni_Image_nvGetRamdisk(JNIEnv *env, jobject, jlong handle) {
    Image *image = (Image*) handle;

    jbyteArray buffer = env->NewByteArray((jsize) image->ramdisk->len);
    check_exp();

    env->SetByteArrayRegion(buffer, 0, (jsize) image->ramdisk->len, (jbyte *) image->ramdisk->data);

    return buffer;
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image_nvSetRamdisk(JNIEnv *env, jobject, jlong handle, jbyteArray data, jint jLen) {
    Image *image = (Image*) handle;

    jbyte *jBytes = env->GetByteArrayElements(data, nullptr);
    check_exp();

    image->ramdisk->resize((size_t) jLen);
    image->ramdisk->reset_pos();
    image->ramdisk->write(jBytes, (size_t) jLen);
    image->hdr.ramdisk_size = (uint32_t) jLen;

    env->ReleaseByteArrayElements(data, jBytes, JNI_ABORT);
    check_exp();
}

unsigned long Image::hash() {
    auto sum = adler32(0L, kernel->data, (uInt) kernel->len);
    sum = adler32(sum, ramdisk->data, (uInt) ramdisk->len);

    if (second->len > 0) {
        sum = adler32(sum, second->data, (uInt) second->len);
    }

    if (device_tree->len > 0) {
        sum = adler32(sum, device_tree->data, (uInt) device_tree->len);
    }

    if (recovery_dtbo->len > 0) {
        sum = adler32(sum, recovery_dtbo->data, (uInt) recovery_dtbo->len);
    }

    return sum;
}