package com.m4isper.kmpmlbench.benchmark.domain.processing

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import kotlin.math.ln

/** Luma (Y, Rec. 601) of an ARGB pixel in [0, 255]. */
private fun luma(argb: Int): Double {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return 0.299 * r + 0.587 * g + 0.114 * b
}

/**
 * Deterministic synthetic ground-truth image (gradient + tinted blocks).
 * Same [seed] always yields the same pixels, so quality metrics are reproducible.
 */
fun generateSyntheticImage(width: Int, height: Int, seed: Int = 42): ImageBuffer {
    val pixels = IntArray(width * height)
    val maxX = (width - 1).coerceAtLeast(1)
    val maxY = (height - 1).coerceAtLeast(1)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val nx = x.toDouble() / maxX
            val ny = y.toDouble() / maxY
            val r = (((nx * 255) + seed * 7) % 256).toInt().coerceIn(0, 255)
            val g = (((ny * 255) + seed * 3) % 256).toInt().coerceIn(0, 255)
            val b = (((nx * 255 * 0.5 + ny * 255 * 0.5) + seed) % 256).toInt().coerceIn(0, 255)
            pixels[y * width + x] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return ImageBuffer(width, height, pixels)
}

/** Box-average downsample by [factor] (integer), producing the low-res input. */
fun downsample(src: ImageBuffer, factor: Int): ImageBuffer {
    require(factor >= 1)
    if (factor == 1) return src
    val ow = src.width / factor
    val oh = src.height / factor
    val out = IntArray(ow * oh)
    for (y in 0 until oh) {
        for (x in 0 until ow) {
            var r = 0L; var g = 0L; var b = 0L
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val c = src.pixels[(y * factor + dy) * src.width + (x * factor + dx)]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            val n = factor * factor
            out[y * ow + x] = (255 shl 24) or
                (((r / n).toInt()) shl 16) or
                (((g / n).toInt()) shl 8) or
                (b / n).toInt()
        }
    }
    return ImageBuffer(ow, oh, out)
}

/** Bilinear upsample by [factor] (integer), simulating a reconstruction model. */
fun upsampleBilinear(src: ImageBuffer, factor: Int): ImageBuffer {
    require(factor >= 1)
    val ow = src.width * factor
    val oh = src.height * factor
    val out = IntArray(ow * oh)
    for (y in 0 until oh) {
        val sy = y.toDouble() / factor
        val y0 = sy.toInt().coerceIn(0, src.height - 1)
        val y1 = (y0 + 1).coerceAtMost(src.height - 1)
        val fy = sy - y0
        for (x in 0 until ow) {
            val sx = x.toDouble() / factor
            val x0 = sx.toInt().coerceIn(0, src.width - 1)
            val x1 = (x0 + 1).coerceAtMost(src.width - 1)
            val fx = sx - x0
            val c00 = src.pixels[y0 * src.width + x0]
            val c10 = src.pixels[y0 * src.width + x1]
            val c01 = src.pixels[y1 * src.width + x0]
            val c11 = src.pixels[y1 * src.width + x1]
            out[y * ow + x] = interpolate(c00, c10, c01, c11, fx, fy)
        }
    }
    return ImageBuffer(ow, oh, out)
}

private fun interpolate(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
    val r = lerp((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, fx, fy)
    val g = lerp((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, fx, fy)
    val b = lerp(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
    return (255 shl 24) or (r shl 16) or (g shl 8) or b
}

private fun lerp(a: Int, b: Int, c: Int, d: Int, fx: Double, fy: Double): Int {
    val top = a + (b - a) * fx
    val bot = c + (d - c) * fx
    return (top + (bot - top) * fy).toInt().coerceIn(0, 255)
}

/** Peak Signal-to-Noise Ratio (dB) of [reconstructed] vs [reference] on luma. */
fun computePsnr(reference: ImageBuffer, reconstructed: ImageBuffer): Double {
    require(reference.width == reconstructed.width && reference.height == reconstructed.height) {
        "buffers must have equal dimensions"
    }
    val n = reference.pixels.size
    var mse = 0.0
    for (i in 0 until n) {
        val d = luma(reference.pixels[i]) - luma(reconstructed.pixels[i])
        mse += d * d
    }
    mse /= n
    if (mse == 0.0) return 100.0
    return 10.0 * ln((255.0 * 255.0) / mse) / ln(10.0)
}

/**
 * Structural Similarity (global, single-scale) of [reconstructed] vs [reference]
 * on luma. Returns 1.0 for identical inputs.
 */
fun computeSsim(reference: ImageBuffer, reconstructed: ImageBuffer): Double {
    require(reference.width == reconstructed.width && reference.height == reconstructed.height) {
        "buffers must have equal dimensions"
    }
    val n = reference.pixels.size
    var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumX2 = 0.0; var sumY2 = 0.0
    for (i in 0 until n) {
        val x = luma(reference.pixels[i])
        val y = luma(reconstructed.pixels[i])
        sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x; sumY2 += y * y
    }
    val meanX = sumX / n
    val meanY = sumY / n
    val varX = sumX2 / n - meanX * meanX
    val varY = sumY2 / n - meanY * meanY
    val cov = sumXY / n - meanX * meanY
    val c1 = (0.01 * 255) * (0.01 * 255)
    val c2 = (0.03 * 255) * (0.03 * 255)
    val den = (meanX * meanX + meanY * meanY + c1) * (varX + varY + c2)
    return if (den == 0.0) 1.0 else (2 * meanX * meanY + c1) * (2 * cov + c2) / den
}
