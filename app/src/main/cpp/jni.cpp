#include <jni.h>
#include "tipatch.h"
#include "const.h"

#define dbg(...) __android_log_print(ANDROID_LOG_DEBUG, "TiCPP", __VA_ARGS__)
#define check_exp() if(env->ExceptionCheck()) throw jv_exception();

class jv_exception : public std::exception {};
class img_exception : public std::runtime_error {
public:
    img_exception(const std::string &what) : std::runtime_error(what) {}
};

class jv_bytes {
public:
    jv_bytes(JNIEnv *env, jbyteArray array, jbyte *jbytes, unsigned int len) {
        this->env = env;
        this->array = array;
        this->jbytes = jbytes;
        this->len = len;
    }

    ~jv_bytes() {
        env->ReleaseByteArrayElements(array, jbytes, JNI_ABORT);
    }

    char *bytes() {
        return (char *) jbytes;
    }

    std::string string() {
        std::string ret((char *) jbytes, len);
        env->ReleaseByteArrayElements(array, jbytes, JNI_ABORT);
        return ret;
    }

private:
    JNIEnv *env;
    jbyteArray array;
    jbyte *jbytes;
    unsigned int len;
};

jv_bytes read_bytes(JNIEnv *env, jobject fis, unsigned int count) {
    // create the buffer
    jbyteArray buffer = env->NewByteArray(count);
    check_exp();

    // method: int InputStream#read(byte[])
    jclass clazz = env->GetObjectClass(fis);
    check_exp();

    jmethodID reader = env->GetMethodID(clazz, "read", "([B)I");
    check_exp();

    jint bytesRead = env->CallIntMethod(fis, reader, buffer);
    check_exp();

    if (bytesRead != count) {
        throw std::runtime_error(std::to_string(count) + " bytes requested; " +
                                 std::to_string(bytesRead) + " bytes read");
    }

    // get the data
    jboolean isCopy;
    jbyte *jbytes = env->GetByteArrayElements(buffer, &isCopy);

    return jv_bytes(env, buffer, jbytes, count);
}

void read_padding(JNIEnv *env, jobject fis, unsigned int item_size, unsigned int page_size) {
    unsigned int page_mask = page_size - 1;
    if ((item_size & page_mask) == 0)
        return;

    unsigned int count = page_size - (item_size & page_mask);
    read_bytes(env, fis, count);
}

void rethrow(JNIEnv *env) {
    try {
        throw;
    } catch (const jv_exception &) {
    } catch (const img_exception &e) {
        jclass clazz = env->FindClass("com/kdrag0n/tipatch/jni/ImageException");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const std::bad_alloc &e) {
        jclass clazz = env->FindClass("java/lang/OutOfMemoryError");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    } catch (const std::exception &e) { // unknown
        jclass clazz = env->FindClass("java/lang/Error");
        if (clazz)
            env->ThrowNew(clazz, e.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kdrag0n_tipatch_jni_Image_init(JNIEnv *env, jobject, jobject fis) {
    try {
        Image *image = new Image();

        // header
        {
            auto hdr_bytes = read_bytes(env, fis, sizeof(boot_img_hdr));
            auto hdr = ((boot_img_hdr *) hdr_bytes.bytes());

            if (memcmp(hdr->magic, BOOT_MAGIC, BOOT_MAGIC_SIZE) != 0)
                throw img_exception("Unable to find header. Are you sure this is a TWRP image?");

            image->hdr = *hdr;
        }

        read_padding(env, fis, sizeof(boot_img_hdr), image->hdr.page_size);

        // kernel
        {
            auto kernel_bytes = read_bytes(env, fis, image->hdr.kernel_size);
            image->kernel = std::make_shared<std::string>(kernel_bytes.string());
        }

        read_padding(env, fis, image->hdr.kernel_size, image->hdr.page_size);

        // ramdisk
        {
            auto ramdisk_bytes = read_bytes(env, fis, image->hdr.ramdisk_size);
            image->ramdisk = std::make_shared<std::string>(ramdisk_bytes.string());
        }

        read_padding(env, fis, image->hdr.ramdisk_size, image->hdr.page_size);

        // second-stage loader
        if (image->hdr.second_size > 0) {
            auto second_bytes = read_bytes(env, fis, image->hdr.second_size);
            image->second = std::make_shared<std::string>(second_bytes.string());
        } else {
            image->second = std::make_shared<std::string>("");
        }

        read_padding(env, fis, image->hdr.second_size, image->hdr.page_size);

        // device tree
        if (image->hdr.dt_size > 0) {
            auto dt_bytes = read_bytes(env, fis, image->hdr.dt_size);
            image->device_tree = std::make_shared<std::string>(dt_bytes.string());
        } else {
            image->device_tree = std::make_shared<std::string>("");
        }

        read_padding(env, fis, image->hdr.dt_size, image->hdr.page_size);

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
Java_com_kdrag0n_tipatch_jni_Image__1detectCompressor(JNIEnv, jobject, jlong handle) {
    Image *image = (Image*) handle;
    auto data = image->ramdisk->data();
    int b1 = data[0];
    int b2 = data[1];

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
    } else {
        return comp::unknown;
    }
}