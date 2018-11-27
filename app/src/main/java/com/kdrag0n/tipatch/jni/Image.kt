package com.kdrag0n.tipatch.jni

import java.io.*
import kotlin.concurrent.thread

class Image(fis: InputStream) {
    private val nativePtr = init(fis)
    private var comprSize: Int = 0

    fun finalize() {
        free(nativePtr)
    }

    fun callXz(inData: ByteArray, xzPath: File, vararg args: String): ByteArray {
        val process = ProcessBuilder(xzPath.absolutePath, *args)
                .redirectErrorStream(true)
                .start()
        var outData = ByteArray(0)
        val reader = thread {
            outData = process.inputStream.readBytes()
        }

        while (!reader.isAlive) {}
        process.outputStream.use {
            ByteArrayInputStream(inData).copyTo(it)
            it.flush()
        }

        process.waitFor()
        reader.join()

        return outData
    }

    fun detectCompressor(): Byte {
        return nvDetectCompressor(nativePtr)
    }

    fun decompressRamdisk(compMode: Byte, xzPath: File) {
        when (compMode) {
            COMP_XZ, COMP_LZMA -> {
                val cmData = nvGetRamdisk(nativePtr)
                comprSize = cmData.size

                val dcData = callXz(cmData, xzPath, "-dc")
                nvSetRamdisk(nativePtr, dcData, dcData.size)
            }
            else -> nvDecompressRamdisk(nativePtr, compMode)
        }
    }

    fun compressRamdisk(compMode: Byte, xzPath: File) {
        when (compMode) {
            COMP_XZ, COMP_LZMA -> {
                val dcData = nvGetRamdisk(nativePtr)

                val format = if (compMode == COMP_LZMA) "-Flzma" else "-Fxz"
                val cmData = callXz(dcData, xzPath, format, "-c")
                nvSetRamdisk(nativePtr, cmData, cmData.size)
            }
            else -> nvCompressRamdisk(nativePtr, compMode)
        }
    }

    fun patchRamdisk(direction: Byte): Int {
        return nvPatchRamdisk(nativePtr, direction)
    }

    fun write(fos: OutputStream) {
        nvWrite(nativePtr, fos)
    }

    private external fun init(fis: InputStream): Long
    private external fun free(pointer: Long)
    private external fun nvDetectCompressor(pointer: Long): Byte
    private external fun nvDecompressRamdisk(pointer: Long, compMode: Byte)
    private external fun nvCompressRamdisk(pointer: Long, compMode: Byte)
    private external fun nvPatchRamdisk(pointer: Long, direction: Byte): Int
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