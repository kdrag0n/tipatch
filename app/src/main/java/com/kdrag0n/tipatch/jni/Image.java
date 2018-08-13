package com.kdrag0n.tipatch.jni;

import java.io.InputStream;

public class Image {
    public static final byte COMP_GZIP = 0;
    public static final byte COMP_LZ4 = 1;
    public static final byte COMP_LZO = 2;
    public static final byte COMP_XZ = 3;
    public static final byte COMP_BZIP2 = 4;
    public static final byte COMP_LZMA = 5;
    public static final byte COMP_UNKNOWN = 6;

    public static final byte REPL_NORMAL = 0;
    public static final byte REPL_REVERSE = 1;

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

    public void decompressRamdisk(byte compMode) {
        _decompressRamdisk(handle, compMode);
    }

    public void compressRamdisk(byte compMode) {
        _compressRamdisk(handle, compMode);
    }

    public void patchRamdisk(byte direction) {
        _patchRamdisk(handle, direction);
    }

    private native long init(InputStream fis);
    private native void free(long handle);
    private native byte _detectCompressor(long handle);
    private native void _decompressRamdisk(long handle, byte compMode);
    private native void _compressRamdisk(long handle, byte compMode);
    private native void _patchRamdisk(long handle, byte direction);
}