package com.kdrag0n.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import java.lang.reflect.Field
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
    val file = File("$cacheDir/tmp${ThreadLocalRandom.current().nextInt()}")
    file.outputStream().close()

    Shell.SU.run("cat $path > ${file.absolutePath}")

    try {
        val fis = file.inputStream()
        val data = ByteArray(fis.available())

        fis.use {
            val dis = DataInputStream(it)
            dis.readFully(data)
        }

        return data
    } finally {
        Shell.SU.run("rm -f ${file.absolutePath}")
    }
}

fun Context.writeRootFile(path: String, data: ByteArray) {
    val file = File("$cacheDir/tmp${ThreadLocalRandom.current().nextInt()}")

    try {
        val fos = file.outputStream()
        fos.use {
            fos.write(data)
        }

        Shell.SU.run("cat ${file.absolutePath} > $path")
    } finally {
        file.delete()
    }
}

fun Context.openUri(uri: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
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