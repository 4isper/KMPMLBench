package com.m4isper.kmpmlbench.benchmark.domain.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

actual fun loadModelBytes(path: String): ByteArray {
    val app = currentApplication()
        ?: throw IllegalStateException("No Application context available to load resource: $path")
    return app.assets.open(path).use { it.readBytes() }
}

actual fun loadImageBuffer(path: String): ImageBuffer {
    val app = currentApplication()
        ?: throw IllegalStateException("No Application context available to load resource: $path")
    val bytes = app.assets.open(path).use { it.readBytes() }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Failed to decode image resource: $path")
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    return ImageBuffer(bitmap.width, bitmap.height, pixels)
}

actual fun loadImageFile(path: String): ImageBuffer {
    val ctx = currentApplication()
        ?: throw IllegalStateException("No context available to load image: $path")
    val bitmap = if (path.startsWith("content://") || path.startsWith("file://")) {
        ctx.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    } else {
        android.graphics.BitmapFactory.decodeFile(path)
    } ?: throw IllegalStateException("Failed to decode image file: $path")
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    bitmap.recycle()
    return ImageBuffer(bitmap.width, bitmap.height, pixels)
}

actual fun loadModelFile(path: String): ByteArray {
    val ctx = currentApplication()
        ?: throw IllegalStateException("No context available to load model file: $path")
    return if (path.startsWith("content://") || path.startsWith("file://")) {
        ctx.contentResolver.openInputStream(android.net.Uri.parse(path))?.use { it.readBytes() }
    } else {
        java.io.File(path).readBytes()
    } ?: throw IllegalStateException("Failed to read model/label file: $path")
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
