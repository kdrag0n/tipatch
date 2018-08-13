package com.kdrag0n.tipatch.jni;

import java.io.InputStream;

public class Image {
    public static final byte CompGzip = 0;
    public static final byte CompLz4 = 1;
    public static final byte CompLzo = 2;
    public static final byte CompXz = 3;
    public static final byte CompBzip2 = 4;
    public static final byte CompLzma = 5;
    public static final byte CompUnknown = 6;

    private long handle;

    public Image(InputStream fis) {
        handle = init(fis);
    }

    public void finalize() {
        free(handle);
    }

    public byte detectCompressor() {
        return _detectCompressor(handle);
    }

    private native long init(InputStream fis);
    private native void free(long handle);
    private native byte _detectCompressor(long handle);
}