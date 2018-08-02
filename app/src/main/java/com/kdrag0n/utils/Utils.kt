package com.kdrag0n.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.provider.OpenableColumns
import android.text.Html
import android.text.Spanned
import android.util.Log
import eu.chainfire.libsuperuser.Shell
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.ThreadLocalRandom

const val logTag = "Tipatch"

fun asyncExec(func: () -> Unit) {
    object : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) = func()
    }.execute()
}

@SuppressLint("PrivateApi")
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

fun findPartitionDirs(): List<String> {
    val res = Shell.SU.run("find /dev/block/platform -type d -name 'by-name'")

    if (res == null || res.size < 1) {
        return listOf()
    }

    return res[0].split('\n')
}

fun rootExists(path: String): Boolean {
    val res = Shell.SU.run("[ -e '$path' ] && echo EXISTS")

    if (res == null || res.size < 1) {
        return false
    }

    return "EXISTS" in res[0]
}

@SuppressLint("deprecation")
fun parseHtml(html: String): Spanned {
    return if (Build.VERSION.SDK_INT >= 24) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(html)
    }
}