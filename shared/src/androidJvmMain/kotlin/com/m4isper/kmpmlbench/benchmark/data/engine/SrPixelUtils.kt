package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

/**
 * Shared Super-Resolution pixel pipeline, used by every SR engine (ONNX
 * Runtime, NCNN, ...) so the reconstructed frame is produced identically and
 * the PSNR/SSIM comparison between engines stays meaningful.
 *
 * The ONNX Model Zoo "super-resolution" Sub-Pixel CNN (ESPCN-style) consumes
 * only the luma (Y) channel of a 224×224 input and emits the 224×scale output
 * luma; the chroma channels are upscaled separately with bilinear interpolation
 * and recombined. All helpers here are pure pixel math (no framework deps).
 */
data class SrYCbCr(val y: FloatArray, val cb: FloatArray, val cr: FloatArray)

fun srToYCbCr(buf: ImageBuffer): SrYCbCr {
    val n = buf.width * buf.height
    val y = FloatArray(n)
    val cb = FloatArray(n)
    val cr = FloatArray(n)
    for (i in 0 until n) {
        val p = buf.pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        y[i] = 0.299f * r + 0.587f * g + 0.114f * b
        cb[i] = -0.1687f * r - 0.3313f * g + 0.5f * b + 128f
        cr[i] = 0.5f * r - 0.4187f * g - 0.0813f * b + 128f
    }
    return SrYCbCr(y, cb, cr)
}

fun srYcbcrToArgb(y: FloatArray, cb: FloatArray, cr: FloatArray, w: Int, h: Int): ImageBuffer {
    val pixels = IntArray(w * h)
    for (i in 0 until w * h) {
        val cbb = cb[i] - 128f
        val crr = cr[i] - 128f
        val r = (y[i] + 1.402f * crr).coerceIn(0f, 255f)
        val g = (y[i] - 0.344136f * cbb - 0.714136f * crr).coerceIn(0f, 255f)
        val b = (y[i] + 1.772f * cbb).coerceIn(0f, 255f)
        pixels[i] = (255 shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }
    return ImageBuffer(w, h, pixels)
}

fun srUpscaleChannel(src: FloatArray, w: Int, h: Int, factor: Int): FloatArray {
    val ow = w * factor
    val oh = h * factor
    val out = FloatArray(ow * oh)
    for (yy in 0 until oh) {
        val sy = yy.toDouble() / factor
        val y0 = sy.toInt().coerceIn(0, h - 1)
        val y1 = (y0 + 1).coerceAtMost(h - 1)
        val fy = sy - y0
        for (x in 0 until ow) {
            val sx = x.toDouble() / factor
            val x0 = sx.toInt().coerceIn(0, w - 1)
            val x1 = (x0 + 1).coerceAtMost(w - 1)
            val fx = sx - x0
            val c00 = src[y0 * w + x0]
            val c10 = src[y0 * w + x1]
            val c01 = src[y1 * w + x0]
            val c11 = src[y1 * w + x1]
            val top = c00 + (c10 - c00) * fx
            val bot = c01 + (c11 - c01) * fx
            out[yy * ow + x] = (top + (bot - top) * fy).toFloat()
        }
    }
    return out
}

/** General ARGB bilinear resize to an arbitrary [outW]x[outH] target. */
fun srResizeBilinear(src: ImageBuffer, outW: Int, outH: Int): ImageBuffer {
    val inW = src.width
    val inH = src.height
    val out = IntArray(outW * outH)
    for (yy in 0 until outH) {
        val sy = (yy.toDouble() + 0.5) * inH / outH - 0.5
        val y0 = sy.toInt().coerceIn(0, inH - 1)
        val y1 = (y0 + 1).coerceAtMost(inH - 1)
        val fy = sy - y0
        for (x in 0 until outW) {
            val sx = (x.toDouble() + 0.5) * inW / outW - 0.5
            val x0 = sx.toInt().coerceIn(0, inW - 1)
            val x1 = (x0 + 1).coerceAtMost(inW - 1)
            val fx = sx - x0
            out[yy * outW + x] = srLerpArgb(
                src.pixels[y0 * inW + x0],
                src.pixels[y0 * inW + x1],
                src.pixels[y1 * inW + x0],
                src.pixels[y1 * inW + x1],
                fx,
                fy,
            )
        }
    }
    return ImageBuffer(outW, outH, out)
}

fun srLerpArgb(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
    val r = srLerpCh((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, fx, fy)
    val g = srLerpCh((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, fx, fy)
    val b = srLerpCh(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
    val a = srLerpCh((c00 shr 24) and 0xFF, (c10 shr 24) and 0xFF, (c01 shr 24) and 0xFF, (c11 shr 24) and 0xFF, fx, fy)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun srLerpCh(a: Int, b: Int, c: Int, d: Int, fx: Double, fy: Double): Int {
    val top = a + (b - a) * fx
    val bot = c + (d - c) * fx
    return (top + (bot - top) * fy).toInt().coerceIn(0, 255)
}
