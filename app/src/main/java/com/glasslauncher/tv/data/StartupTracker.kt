package com.glasslauncher.tv.data

import android.os.SystemClock
import android.util.Log

object StartupTracker {
    private const val TAG = "AlfredoStartup"

    @Volatile var t0: Long = 0L; private set
    @Volatile var bgReadyMs: Long = -1L; private set
    @Volatile var uiReadyMs: Long = -1L; private set

    @Volatile private var bgLogged = false
    @Volatile private var uiLogged = false

    fun onActivityCreated() {
        t0 = SystemClock.elapsedRealtime()
        bgReadyMs = -1L
        uiReadyMs = -1L
        bgLogged = false
        uiLogged = false
        Log.i(TAG, "startup_activity_created: t=0 ms (epoch=$t0)")
    }

    fun onBackgroundReady() {
        if (bgLogged) return
        bgLogged = true
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (t0 > 0) now - t0 else 0L
        bgReadyMs = elapsed
        Log.i(TAG, "startup_background_ready: t=$elapsed ms")
    }

    fun onUiReady() {
        if (uiLogged) return
        uiLogged = true
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (t0 > 0) now - t0 else 0L
        uiReadyMs = elapsed
        Log.i(TAG, "startup_ui_ready: t=$elapsed ms")
    }

    fun reset() {
        t0 = 0L
        bgReadyMs = -1L
        uiReadyMs = -1L
        bgLogged = false
        uiLogged = false
    }
}
