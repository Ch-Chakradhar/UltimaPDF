package com.example.ultimapdf

import android.app.Application
import android.util.Log

class UltimaPdfApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isPdfOcrFrameworkBug(throwable)) {
                Log.w(
                    "UltimaPdfApp",
                    "Suppressed Android 15 PdfPageComponentsIdManager framework NullPointerException on thread ${thread.name}",
                    throwable,
                )
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isPdfOcrFrameworkBug(throwable: Throwable?): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            val msg = cause.message ?: ""
            if (msg.contains("PdfPageComponentsIdManager") || msg.contains("getIdForIndex")) {
                return true
            }
            if (cause is NullPointerException && cause.stackTrace.any {
                it.className.contains("PdfPageComponentsIdManager") ||
                    it.methodName.contains("getIdForIndex")
            }) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
