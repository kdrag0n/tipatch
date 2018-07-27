package com.kdrag0n.utils

import android.os.AsyncTask
import android.util.Log

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
        method.invoke(null, prop) as String
    } catch (e: Exception) {
        Log.e(logTag, "Failed to get property via API", e)
        null
    }
}