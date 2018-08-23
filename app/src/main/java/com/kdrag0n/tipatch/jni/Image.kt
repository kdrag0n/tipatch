package com.kdrag0n.tipatch.jni

import com.kdrag0n.utils.ByteBuffer
import org.tukaani.xz.ArrayCache
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import java.io.*

class Image(fis: InputStream) {
    private val nativePtr = init(fis)
    private var comprSize: Int = 0

    fun finalize() {
        free(nativePtr)
    }

    fun detectCompressor(): Byte {
        return nvDetectCompressor(nativePtr)
    }

    fun decompressRamdisk(compMode: Byte) {
        when (compMode) {
            COMP_LZMA -> {
                val cmData = nvGetRamdisk(nativePtr)
                comprSize = cmData.size

                val stream = LZMAInputStream(ByteArrayInputStream(cmData))

                val dcData = stream.readBytes(cmData.size * 2)
                stream.close()

                nvSetRamdisk(nativePtr, dcData, dcData.size)
            }
            else -> nvDecompressRamdisk(nativePtr, compMode)
        }
    }

    fun compressRamdisk(compMode: Byte) {
        when (compMode) {
            COMP_LZMA -> {
                val dcData = nvGetRamdisk(nativePtr)
                val bos = ByteBuffer(comprSize + 4) // better safe than sorry (oom)
                val stream = LZMAOutputStream(bos, LZMA2Options(), -1, ArrayCache.getDefaultCache())
                stream.write(dcData)

                stream.close()

                // avoid copy as it's probably already copied once for JNI access
                nvSetRamdisk(nativePtr, bos.bytes, bos.size())
            }
            else -> nvCompressRamdisk(nativePtr, compMode)
        }
    }

    fun patchRamdisk(direction: Byte) {
        nvPatchRamdisk(nativePtr, direction)
    }

    fun write(fos: OutputStream) {
        nvWrite(nativePtr, fos)
    }

    private external fun init(fis: InputStream): Long
    private external fun free(pointer: Long)
    private external fun nvDetectCompressor(pointer: Long): Byte
    private external fun nvDecompressRamdisk(pointer: Long, compMode: Byte)
    private external fun nvCompressRamdisk(pointer: Long, compMode: Byte)
    private external fun nvPatchRamdisk(pointer: Long, direction: Byte)
    private external fun nvGetRamdisk(pointer: Long): ByteArray
    private external fun nvSetRamdisk(pointer: Long, data: ByteArray, size: Int)
    private external fun nvWrite(pointer: Long, fos: OutputStream)

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