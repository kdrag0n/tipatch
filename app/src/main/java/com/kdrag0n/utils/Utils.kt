package com.kdrag0n.utils

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import eu.chainfire.libsuperuser.Shell
import java.io.DataInputStream
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.ThreadLocalRandom

const val logTag = "Tipatch"

fun asyncExec(func: () -> Unit) {
    object : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) = func()
    }.execute()
}

fun getProp(prop: String): String? {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod("get", java.lang.String::class.java)
        val result = method.invoke(null, prop) as String

        if (result == "") {
            null
        } else {
            result
        }
    } catch (e: Exception) {
        Log.e(logTag, "Failed to get property via API", e)
        null
    }
}

fun FileDescriptor.raw(): Int {
    val field = FileDescriptor::class.java.getDeclaredField("descriptor")
    field.isAccessible = true

    return field.getInt(this)
}

fun Context.readRootFile(path: String): ByteArray {
    val fn = "tmp${ThreadLocalRandom.current().nextInt()}"
    Shell.SU.run("cat $path > $cacheDir/$fn")

    try {
        val file = File("$cacheDir/$fn").inputStream()
        val data = ByteArray(file.available())

        file.use {
            val fis = DataInputStream(it)
            fis.readFully(data)
        }

        return data
    } finally {
        Shell.SU.run("rm -f $cacheDir/$fn")
    }
}

fun Context.writeRootFile(path: String, data: ByteArray) {
    val fn = "tmp${ThreadLocalRandom.current().nextInt()}"
    val tmpFile = File("$cacheDir/$fn")

    try {
        val file = tmpFile.outputStream()
        file.use {
            file.write(data)
        }

        Shell.SU.run("cat $cacheDir/$fn > $path")
    } finally {
        tmpFile.delete()
    }
}

fun Uri.getFileName(ctx: Context): String? {
    if (scheme == "content") {
        ctx.contentResolver.query(this, null, null, null, null).use {
            if (it != null && it.moveToFirst()) {
                return it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
    }

    val sIdx = path.lastIndexOf('/')
    return when (sIdx) {
        -1 -> null
        else -> path.substring(sIdx + 1)
    }
}

fun findPartitionDirs(): List<File> {
    val results = mutableListOf<File>()

    fun recurse(path: String) {
        File(path).listFiles().forEach {
            if (it.isDirectory) {
                if (it.name == "by-name") {
                    results += it
                } else {
                    recurse(it.absolutePath)
                }
            }
        }
    }

    recurse("/dev/block/platform")
    return results
}