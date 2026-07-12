@file:OptIn(ExperimentalForeignApi::class)

package com.m4isper.kmpmlbench.benchmark.data.platform

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSBundle
import platform.UIKit.UIImage
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

/**
 * Resolves a bundled resource path (e.g. "models/yolov8n.onnx") to a file path
 * inside the app/test bundle. Returns null if not found there.
 */
fun resolveResourcePath(path: String): String? {
    val clean = path.removePrefix("models/")
    val name = clean.substringBeforeLast(".")
    val ext = clean.substringAfterLast(".", "")
    return NSBundle.mainBundle.pathForResource(name, if (ext.isEmpty()) null else ext)
}

actual fun loadModelBytes(path: String): ByteArray {
    val resolved = resolveResourcePath(path) ?: path
    return memScoped {
        val file = fopen(resolved, "rb")
            ?: throw RuntimeException("cannot open model resource: $resolved")
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            fseek(file, 0, SEEK_SET)
            val buffer = allocArray<ByteVar>(size)
            fread(buffer, 1UL, size.toULong(), file)
            ByteArray(size.toInt()) { buffer[it] }
        } finally {
            fclose(file)
        }
    }
}

actual fun loadImageBuffer(path: String): ImageBuffer {
    val resolved = resolveResourcePath(path)
    if (resolved != null) {
        decodeImageToArgb(resolved)?.let { return it }
    }
    // Fallback to a synthetic frame if the real photo is unavailable.
    return generateSyntheticImage(224, 224)
}

actual fun loadImageFile(path: String): ImageBuffer {
    return decodeImageToArgb(path) ?: generateSyntheticImage(224, 224)
}

actual fun loadModelFile(path: String): ByteArray {
    return memScoped {
        val file = fopen(path, "rb")
            ?: throw RuntimeException("cannot open model file: $path")
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            fseek(file, 0, SEEK_SET)
            val buffer = allocArray<ByteVar>(size)
            fread(buffer, 1UL, size.toULong(), file)
            ByteArray(size.toInt()) { buffer[it] }
        } finally {
            fclose(file)
        }
    }
}

/** Decodes a bundled JPEG/PNG file into an ARGB [ImageBuffer] via UIKit. */
internal fun decodeImageToArgb(path: String): ImageBuffer? {
    val uiImage = UIImage(contentsOfFile = path) ?: return null
    val cg = uiImage.CGImage ?: return null
    val w = CGImageGetWidth(cg).toInt()
    val h = CGImageGetHeight(cg).toInt()
    val bytesPerPixel = 4
    val bytesPerRow = w * bytesPerPixel
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    // kCGImageAlphaPremultipliedLast (== 1) | kCGBitmapByteOrder32Big.
    val bitmapInfo = kCGBitmapByteOrder32Big or 1u
    val ctx = CGBitmapContextCreate(
        null,
        w.toULong(),
        h.toULong(),
        8UL,
        bytesPerRow.toULong(),
        colorSpace,
        bitmapInfo,
    ) ?: return null
    CGContextDrawImage(ctx, CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()), cg)
    val data = CGBitmapContextGetData(ctx) ?: return null
    val ptr = data.reinterpret<UByteVar>()
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * bytesPerRow
        for (x in 0 until w) {
            val i = row + x * 4
            val r = ptr[i].toInt() and 0xFF
            val g = ptr[i + 1].toInt() and 0xFF
            val b = ptr[i + 2].toInt() and 0xFF
            val a = ptr[i + 3].toInt() and 0xFF
            pixels[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return ImageBuffer(w, h, pixels)
}
