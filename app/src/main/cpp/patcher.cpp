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

    /*auto it = std::search(input->begin(), input->end(),
                          std::boyer_moore_searcher(from.begin(), from.end()));

    if (it != input->end()) {
        auto idx = it - input->begin();
        dbg("%s found at %d", from, idx);

        memcpy(input->data() + (idx * sizeof(char)), to.data(), to.length());
        dbg("replacement performed");
    } else { // not found
        dbg("replacement failed: %s not found", from.c_str());
        return;
    }*/

    void *addr = memmem(input->data(), input->length(), from.data(), from.length());
    if (addr != NULL) {
        memcpy(addr, to.data(), to.length());
    } else {
        return;
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
    Timer tmr;

    // Preserve /data/media
    repl_dir(ramdisk,
             "\x00/media\x00",
             "\x00/.twrp\x00",
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

    dbg("replacing took %f ms", tmr.elapsed());
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
