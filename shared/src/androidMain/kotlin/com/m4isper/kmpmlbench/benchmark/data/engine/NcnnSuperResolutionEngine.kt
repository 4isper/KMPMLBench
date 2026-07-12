package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.domain.processing.computePsnr
import com.m4isper.kmpmlbench.benchmark.domain.processing.computeSsim
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Real Super-Resolution engine backed by NCNN (Android only), mirroring the
 * ONNX Runtime SR engine's pixel pipeline so the two stay directly comparable.
 *
 * Loads the Sub-Pixel CNN (ESPCN-style) model converted to NCNN format
 * (`super-resolution-10.param` + `.bin`) from the bundled assets via the
 * [NcnnNet] JNI bridge, runs token-free single-blob inference on the luma
 * channel, and reports PSNR/SSIM versus the same ground-truth frame.
 *
 * NCNN has no published JVM/Android artifact with a Kotlin wrapper, so the
 * native `libncnn_jni.so` (ncnn core + this project's JNI shim) is vendored
 * under `androidMain/jniLibs` and loaded by [NcnnNet].
 */
class NcnnSuperResolutionEngine(
    private val task: SuperResolutionTask,
    private val paramPath: String = "models/super-resolution-10.param",
    private val binPath: String = "models/super-resolution-10.bin",
    private val scale: Int = 3,
) : MlEngine {
    override val id: String = "ncnn-sr"
    override val displayName: String = "NCNN SR (Sub-Pixel CNN ×$scale)"

    private var net: NcnnNet? = null

    private companion object {
        const val MODEL_INPUT_SIZE = 224
    }

    override fun initialize() {
        val paramBytes = loadModelBytes(paramPath)
        val modelBytes = loadModelBytes(binPath)
        net = NcnnNet().also { it.load(paramBytes, modelBytes) }
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val n = checkNotNull(net) { "NCNN engine not initialized" }
        val inW = MODEL_INPUT_SIZE
        val inH = MODEL_INPUT_SIZE
        val modelOutW = inW * scale
        val modelOutH = inH * scale

        // Fixed 224x224 input: bilinearly resize the (arbitrary) LR frame first.
        val lr = srResizeBilinear(input.image, inW, inH)
        val (y, cb, cr) = srToYCbCr(lr)
        val inputData = FloatArray(inW * inH) { y[it] / 255f }

        val out = n.run(inputData, inW, inH, 1)
        val yOut = FloatArray(modelOutW * modelOutH) { (out[it] * 255f).coerceIn(0f, 255f) }

        val cbUp = srUpscaleChannel(cb, inW, inH, scale)
        val crUp = srUpscaleChannel(cr, inW, inH, scale)
        val modelOut = srYcbcrToArgb(yOut, cbUp, crUp, modelOutW, modelOutH)

        val outW = task.outputWidth
        val outH = task.outputHeight
        val reconstructed = if (modelOutW == outW && modelOutH == outH) {
            modelOut
        } else {
            srResizeBilinear(modelOut, outW, outH)
        }

        val quality = if (input.isCustom) {
            SrQualityMetrics(psnr = -1.0, ssim = -1.0)
        } else {
            val gt = task.groundTruth()
            SrQualityMetrics(
                psnr = computePsnr(gt, reconstructed),
                ssim = computeSsim(gt, reconstructed),
            )
        }
        return BenchmarkOutput(outW, outH, reconstructed, quality)
    }

    override fun close() {
        net?.close()
        net = null
    }
}
