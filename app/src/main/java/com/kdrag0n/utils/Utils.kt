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
import com.topjohnwu.superuser.Shell
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
    val res = Shell.su("find /dev/block/platform -type d -name 'by-name'").exec()

    if (!res.isSuccess) {
        val err = res.err.getOrElse(0) { "Unknown error" }
        throw IllegalStateException("Root command failed (searching for partitions). \"$err\"")
    }

    return res.out[0].split('\n')
}

@SuppressLint("deprecation")
fun parseHtml(html: String): Spanned {
    return if (Build.VERSION.SDK_INT >= 24) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(html)
    }
}