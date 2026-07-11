package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.QualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.processing.computePsnr
import com.m4isper.kmpmlbench.benchmark.domain.processing.computeSsim
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import java.nio.FloatBuffer

/**
 * Real Super-Resolution engine backed by ONNX Runtime (Desktop/JVM only).
 *
 * Uses the ONNX Model Zoo "super-resolution" Sub-Pixel CNN (ESPCN-style), which
 * upscales the luma (Y) channel by a fixed factor of 3. The benchmark's ARGB
 * input is split into YCbCr; only Y is fed to the model, the chroma channels are
 * upscaled separately with bilinear interpolation, and the three are recombined
 * into the reconstructed RGB frame. Quality is scored against a synthetic
 * ground truth at the model's output resolution.
 *
 * The engine can optionally run on the CoreML execution provider (Apple
 * Neural Engine / GPU) instead of the default CPU provider, so the benchmark UI
 * can compare the two execution paths on the same model.
 */
class OnnxSuperResolutionEngine(
    private val task: SuperResolutionTask,
    private val modelResourcePath: String = "models/super-resolution-10.onnx",
    private val scale: Int = 3,
    private val executionProvider: String = "cpu",
) : MlEngine {
    override val id: String = if (executionProvider == "coreml") "onnx-coreml-sr" else "onnx-sr"
    override val displayName: String =
        "ONNX SR (Sub-Pixel CNN ×$scale)" + if (executionProvider == "coreml") " · CoreML" else ""

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    private companion object {
        // The ONNX Model Zoo super-resolution model takes a fixed 224x224 input
        // and outputs 224*scale x 224*scale (scale = 3).
        const val MODEL_INPUT_SIZE = 224
    }

    override fun initialize() {
        val bytes = checkNotNull(
            javaClass.classLoader.getResourceAsStream(modelResourcePath),
        ) { "ONNX model resource not found on classpath: $modelResourcePath" }.use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        if (executionProvider == "coreml") options.addCoreML()
        session = env.createSession(bytes, options)
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val sess = checkNotNull(session) { "ONNX engine not initialized" }
        val inW = MODEL_INPUT_SIZE
        val inH = MODEL_INPUT_SIZE
        val outW = inW * scale
        val outH = inH * scale
        // The model requires a fixed 224x224 input, so the (arbitrary) LR frame
        // is bilinearly resized to fit before preprocessing.
        val lr = resizeBilinear(input.image, inW, inH)

        val (y, cb, cr) = toYCbCr(lr)
        val inputData = FloatArray(inW * inH) { y[it] / 255f }
        val shape = longArrayOf(1, 1, inH.toLong(), inW.toLong())
        val inputName = sess.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
        val outputName = sess.outputNames.first()
        val result = sess.run(java.util.Collections.singletonMap(inputName, inputTensor))

        try {
            @Suppress("UNCHECKED_CAST")
            val out = (result.get(outputName).orElseThrow().value as Array<Array<Array<FloatArray>>>)[0][0]
            val yOut = FloatArray(outW * outH)
            for (j in 0 until outH) for (i in 0 until outW) {
                yOut[j * outW + i] = (out[j][i] * 255f).coerceIn(0f, 255f)
            }
            val cbUp = upscaleChannel(cb, inW, inH, scale)
            val crUp = upscaleChannel(cr, inW, inH, scale)
            val reconstructed = ycbcrToArgb(yOut, cbUp, crUp, outW, outH)

            val gt = generateSyntheticImage(outW, outH, seed = 42)
            val quality = QualityMetrics(
                psnr = computePsnr(gt, reconstructed),
                ssim = computeSsim(gt, reconstructed),
            )
            return BenchmarkOutput(outW, outH, reconstructed, quality)
        } finally {
            result.close()
            inputTensor.close()
        }
    }

    override fun close() {
        session?.close()
        session = null
        env.close()
    }

    private data class YCbCr(val y: FloatArray, val cb: FloatArray, val cr: FloatArray)

    private fun toYCbCr(buf: ImageBuffer): YCbCr {
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
        return YCbCr(y, cb, cr)
    }

    private fun ycbcrToArgb(y: FloatArray, cb: FloatArray, cr: FloatArray, w: Int, h: Int): ImageBuffer {
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

    private fun upscaleChannel(src: FloatArray, w: Int, h: Int, factor: Int): FloatArray {
        val ow = w * factor
        val oh = h * factor
        val out = FloatArray(ow * oh)
        for (y in 0 until oh) {
            val sy = y.toDouble() / factor
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
                out[y * ow + x] = (top + (bot - top) * fy).toFloat()
            }
        }
        return out
    }

    /** General ARGB bilinear resize to an arbitrary [outW]x[outH] target. */
    private fun resizeBilinear(src: ImageBuffer, outW: Int, outH: Int): ImageBuffer {
        val inW = src.width
        val inH = src.height
        val out = IntArray(outW * outH)
        for (y in 0 until outH) {
            val sy = (y.toDouble() + 0.5) * inH / outH - 0.5
            val y0 = sy.toInt().coerceIn(0, inH - 1)
            val y1 = (y0 + 1).coerceAtMost(inH - 1)
            val fy = sy - y0
            for (x in 0 until outW) {
                val sx = (x.toDouble() + 0.5) * inW / outW - 0.5
                val x0 = sx.toInt().coerceIn(0, inW - 1)
                val x1 = (x0 + 1).coerceAtMost(inW - 1)
                val fx = sx - x0
                out[y * outW + x] = lerpArgb(
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

    private fun lerpArgb(c00: Int, c10: Int, c01: Int, c11: Int, fx: Double, fy: Double): Int {
        val r = lerpCh((c00 shr 16) and 0xFF, (c10 shr 16) and 0xFF, (c01 shr 16) and 0xFF, (c11 shr 16) and 0xFF, fx, fy)
        val g = lerpCh((c00 shr 8) and 0xFF, (c10 shr 8) and 0xFF, (c01 shr 8) and 0xFF, (c11 shr 8) and 0xFF, fx, fy)
        val b = lerpCh(c00 and 0xFF, c10 and 0xFF, c01 and 0xFF, c11 and 0xFF, fx, fy)
        val a = lerpCh((c00 shr 24) and 0xFF, (c10 shr 24) and 0xFF, (c01 shr 24) and 0xFF, (c11 shr 24) and 0xFF, fx, fy)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpCh(a: Int, b: Int, c: Int, d: Int, fx: Double, fy: Double): Int {
        val top = a + (b - a) * fx
        val bot = c + (d - c) * fx
        return (top + (bot - top) * fy).toInt().coerceIn(0, 255)
    }
}
