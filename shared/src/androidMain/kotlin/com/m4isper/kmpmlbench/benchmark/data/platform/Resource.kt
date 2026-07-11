package com.m4isper.kmpmlbench.benchmark.data.platform

import android.content.Context

actual fun loadModelBytes(path: String): ByteArray {
    val app = currentApplication()
        ?: throw IllegalStateException("No Application context available to load resource: $path")
    return app.assets.open(path).use { it.readBytes() }
}

/**
 * Resolves the running [Context] without referencing the hidden
 * `android.app.ActivityThread` class at compile time. The class is always
 * present on device, so a single reflective call retrieves the Application.
 */
private fun currentApplication(): Context? = try {
    val thread = Class.forName("android.app.ActivityThread")
    val method = thread.getMethod("currentApplication")
    method.invoke(null) as? Context
} catch (_: Throwable) {
    null
}
