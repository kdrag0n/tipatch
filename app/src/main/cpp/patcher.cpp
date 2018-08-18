#include "const.h"
#include "image.h"
#include "java.h"
#include "util.h"

void do_replace(byte_obj input, std::string from, std::string to, unsigned int num) {
    assert(from.length() == to.length());

    unsigned int patched = 0;
    void *addr = memmem(input->data, input->len, from.data(), from.length());
    while (addr != nullptr) {
        memcpy(addr, to.data(), to.length());
        patched++;

        auto start_addr = (byte *) addr + (to.length() - 1);
        auto new_len = ((input->data + input->len) - (byte *) addr);
        addr = memmem(start_addr, new_len, from.data(), from.length());
    }

    if (patched == 0) {
        size_t pos = 0;
        std::string null = std::string("\x00", 1);
        while ((pos = from.find(null, pos)) != std::string::npos) {
            from.replace(pos, null.length(), "");
            pos += null.length();
        }

        throw img_exception("Patch #" + std::to_string(num) + " failed: could not find '" + from + "' in image. This is probably not a TWRP image.");
    }
}

void repl_dir(byte_obj input, std::string from, std::string to, unsigned int num, char direction) {
    switch (direction) {
        case repl::normal:
            return do_replace(input, from, to, num);
        case repl::reverse:
            return do_replace(input, to, from, num);
        default:
            throw std::runtime_error("Invalid replacement direction " + std::to_string(direction));
    }
}

void Image::patch_ramdisk(char dir) {
#ifndef NDEBUG
    Timer tmr;
#endif

    // Preserve /data/media
    repl_dir(ramdisk,
             std::string("\x00/media\x00", 8),
             std::string("\x00/.twrp\x00", 8),
             1,
             dir);

    // Change text in Backup screen for English
    repl_dir(ramdisk,
             "Data (excl. storage)",
             "Data (incl. storage)",
             2,
             dir);

    // Change orange warning text when backing up for English
    repl_dir(ramdisk,
             "Backups of {1} do not include any files in internal storage such as pictures or downloads.",
             "Backups of {1} include files in internal storage such as pictures and downloads.          ",
             3,
             dir);

    // Change text shown when wiping "Data"
    repl_dir(ramdisk,
             "Wiping data without wiping /data/media ...",
             "Wiping data and internal storage...       ",
             4,
             dir);

#ifndef NDEBUG
    dbg("Ramdisk patches took %f ms", tmr.elapsed());
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_kdrag0n_tipatch_jni_Image__1patchRamdisk(JNIEnv *env, jobject, jlong handle, jbyte direction) {
    try {
        Image *image = (Image*) handle;
        image->patch_ramdisk(direction);
    } catch (...) {
        rethrow(env);
    }
}
