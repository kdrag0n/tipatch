package com.crashlytics.android

class Crashlytics {
    companion object {
        fun log(msg: String) {}
        fun logException(exp: Throwable) {}
    }
}