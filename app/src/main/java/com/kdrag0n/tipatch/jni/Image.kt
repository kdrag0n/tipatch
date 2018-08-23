package com.kdrag0n.tipatch.jni

import java.io.InputStream
import java.io.OutputStream

class Image(fis: InputStream) {
    private val handle = init(fis)

    fun finalize() {
        free(handle)
    }

    fun detectCompressor(): Byte {
        return nvDetectCompressor(handle)
    }

    fun decompressRamdisk(compMode: Byte) {
        nvDecompressRamdisk(handle, compMode)
    }

    fun compressRamdisk(compMode: Byte) {
        nvCompressRamdisk(handle, compMode)
    }

    fun patchRamdisk(direction: Byte) {
        nvPatchRamdisk(handle, direction)
    }

    fun write(fos: OutputStream) {
        nvWrite(handle, fos)
    }

    private external fun init(fis: InputStream): Long
    private external fun free(handle: Long)
    private external fun nvDetectCompressor(handle: Long): Byte
    private external fun nvDecompressRamdisk(handle: Long, compMode: Byte)
    private external fun nvCompressRamdisk(handle: Long, compMode: Byte)
    private external fun nvPatchRamdisk(handle: Long, direction: Byte)
    private external fun nvWrite(handle: Long, fos: OutputStream)

    companion object {
        const val COMP_GZIP: Byte = 0
        const val COMP_LZ4: Byte = 1
        const val COMP_LZO: Byte = 2
        const val COMP_XZ: Byte = 3
        const val COMP_BZIP2: Byte = 4
        const val COMP_LZMA: Byte = 5
        const val COMP_NONE: Byte = 6
        const val COMP_UNKNOWN: Byte = 7

        const val REPL_NORMAL: Byte = 0
        const val REPL_REVERSE: Byte = 1

        fun compressorName(cMode: Byte): String {
            return when (cMode) {
                Image.COMP_GZIP -> "Gzip"
                Image.COMP_LZ4 -> "LZ4"
                Image.COMP_LZO -> "LZO"
                Image.COMP_XZ -> "XZ"
                Image.COMP_LZMA -> "LZMA"
                Image.COMP_BZIP2 -> "Bzip2"
                Image.COMP_NONE -> "None"
                else -> "Unknown"
            }
        }
    }
}