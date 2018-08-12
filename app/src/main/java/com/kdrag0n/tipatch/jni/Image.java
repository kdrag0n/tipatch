package com.kdrag0n.tipatch.jni;

import java.io.InputStream;

public class Image {
    private long handle;

    public Image(InputStream fis) {
        handle = init(fis);
    }

    public void finalize() {
        free(handle);
    }

    public long getHandle() {
        return handle;
    }

    private native long init(InputStream fis);
    private native void free(long handle);
}
