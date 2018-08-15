#include "const.h"
#include "image.h"
#include "java.h"
#include "util.h"

void do_replace(std::shared_ptr<std::string> input, std::string from, std::string to) {
    if (from.length() != to.length()) {
        throw std::runtime_error("Replacement '" + from + "' invalid: from length " +
                                         std::to_string(from.length()) + " != to length " +
                                         std::to_string(to.length()));
    }

    void *addr = memmem(input->data(), input->length(), from.data(), from.length());
    while (addr != NULL) {
        memcpy(addr, to.data(), to.length());

        auto start_addr = (char *) addr + (to.length() - 1);
        auto new_len = (input->end().base() - (char *) addr);
        addr = memmem(start_addr, new_len, from.data(), from.length());
    }
}

void repl_dir(std::shared_ptr<std::string> input, std::string from, std::string to, char direction) {
    switch (direction) {
        case repl::normal:
            return do_replace(input, from, to);
        case repl::reverse:
            return do_replace(input, to, from);
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
             dir);

    // Change text in Backup screen for English
    repl_dir(ramdisk,
             "Data (excl. storage)",
             "Data (incl. storage)",
             dir);

    // Change orange warning text when backing up for English
    repl_dir(ramdisk,
             "Backups of {1} do not include any files in internal storage such as pictures or downloads.",
             "Backups of {1} include files in internal storage such as pictures and downloads.          ",
             dir);

    // Change text shown when wiping "Data"
    repl_dir(ramdisk,
             "Wiping data without wiping /data/media ...",
             "Wiping data and internal storage...       ",
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
