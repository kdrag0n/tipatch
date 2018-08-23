package com.kdrag0n.utils

import java.io.ByteArrayOutputStream

// Avoids a copy of a large buffer
class ByteBuffer(size: Int) : ByteArrayOutputStream(size) {
    val bytes: ByteArray
        get() = buf
}