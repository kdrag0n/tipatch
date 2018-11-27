#include "const.h"
#include "image.h"
#include "java.h"
#include "util.h"

// false = failed
bool do_replace(byte_obj input, std::string &from, const std::string &to, unsigned int num, bool required) {
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

    // no occurrences found and not already patched
    if (patched == 0 && memmem(input->data, input->len, to.data(), to.length()) == nullptr) {
        // filter null bytes from the "from" string because we're exposing this to the user below
        size_t pos = 0;
        std::string null = std::string("\x00", 1);
        while ((pos = from.find(null, pos)) != std::string::npos) {
            from.replace(pos, null.length(), "");
            pos += null.length();
        }

        if (required) {
            throw img_exception("Patch #" + std::to_string(num) + " failed: could not find '" + from + "' in image. This is probably not a TWRP image.");
        } else {
            return false;
        }
    }

    return true;
}

bool repl_dir(byte_obj input, std::string from, std::string to, unsigned int num, char direction, bool required) {
    switch (direction) {
        case repl::normal:
            return do_replace(input, from, to, num, required);
        case repl::reverse:
            return do_replace(input, to, from, num, required);
        default:
            throw std::runtime_error("Invalid replacement direction " + std::to_string(direction));
    }
}

int Image::patch_ramdisk(char dir) {
#ifndef NDEBUG
    Timer tmr;
#endif
    unsigned int num = 0;
    int failed = 0;

    // Preserve /data/media by ignoring /data/.twrp instead
    if (!repl_dir(ramdisk,
             std::string("\x00/media\x00", 8),
             std::string("\x00/.twrp\x00", 8),
             ++num, dir, true))
        failed++;

    // Change text in Backup screen for English
    if (!repl_dir(ramdisk,
             "Data (excl. storage)",
             "Data (incl. storage)",
             ++num, dir, false))
        failed++;

    // Change orange warning text when backing up for English
    if (!repl_dir(ramdisk,
             "Backups of {1} do not include any files in internal storage such as pictures or downloads.",
             "Backups of {1} include files in internal storage such as pictures and downloads.          ",
             ++num, dir, false))
        failed++;

    // Change text shown when wiping "Data"
    if (!repl_dir(ramdisk,
             "Wiping data without wiping /data/media ...",
             "Wiping data and internal storage...       ",
             ++num, dir, false))
        failed++;

    // Change text shown on Wipe screen
    if (!repl_dir(ramdisk,
             "(not including internal storage)",
             "(including internal storage)    ",
             ++num, dir, false))
        failed++;

#ifndef NDEBUG
    dbg("Ramdisk patches took %f ms", tmr.elapsed());
#endif

    // how many non-essential patches failed?
    return failed;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_kdrag0n_tipatch_jni_Image_nvPatchRamdisk(JNIEnv *env, jobject, jlong handle, jbyte direction) {
    try {
        Image *image = (Image*) handle;
        return (jint) image->patch_ramdisk(direction);
    } catch (...) {
        rethrow(env);
        return (jint) 0;
    }
}
