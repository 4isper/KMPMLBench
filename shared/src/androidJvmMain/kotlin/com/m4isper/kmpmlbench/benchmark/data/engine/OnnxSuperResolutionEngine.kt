package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.data.engine.configureProvider
import com.m4isper.kmpmlbench.benchmark.domain.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.domain.platform.loadModelFile
import com.m4isper.kmpmlbench.benchmark.domain.processing.computePsnr
import com.m4isper.kmpmlbench.benchmark.domain.processing.computeSsim
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import java.nio.FloatBuffer

/**
 * Real Super-Resolution engine backed by ONNX Runtime (Desktop/JVM + Android).
 *
 * Uses the ONNX Model Zoo "super-resolution" Sub-Pixel CNN (ESPCN-style), which
 * upscales the luma (Y) channel by a fixed factor of 3. The benchmark's ARGB
 * input is split into YCbCr; only Y is fed to the model, the chroma channels are
 * upscaled separately with bilinear interpolation, and the three are recombined
 * into the reconstructed RGB frame. Quality is scored against a real bundled
 * photo (the same frame the low-res input was downsampled from).
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
    /** When set, load the model from this user-supplied file instead of [modelResourcePath]. */
    val customModelPath: String? = null,
) : MlEngine {
    override val id: String = when (executionProvider) {
        "coreml" -> "onnx-coreml-sr"
        "nnapi" -> "onnx-sr-nnapi"
        else -> "onnx-sr"
    }
    override val displayName: String =
        "ONNX SR (Sub-Pixel CNN ×$scale)" + when (executionProvider) {
            "coreml" -> " · CoreML"
            "nnapi" -> " · NNAPI"
            else -> ""
        }

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    private companion object {
        // The ONNX Model Zoo super-resolution model takes a fixed 224x224 input
        // and outputs 224*scale x 224*scale (scale = 3).
        const val MODEL_INPUT_SIZE = 224
    }

    override fun initialize() {
        val bytes = if (customModelPath != null) loadModelFile(customModelPath) else loadModelBytes(modelResourcePath)
        val options = OrtSession.SessionOptions()
        configureProvider(options, executionProvider)
        session = env.createSession(bytes, options)
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val sess = checkNotNull(session) { "ONNX engine not initialized" }
        val inW = MODEL_INPUT_SIZE
        val inH = MODEL_INPUT_SIZE
        val modelOutW = inW * scale
        val modelOutH = inH * scale
        // The model requires a fixed 224x224 input, so the (arbitrary) LR frame
        // is bilinearly resized to fit before preprocessing.
        val lr = srResizeBilinear(input.image, inW, inH)

        val (y, cb, cr) = srToYCbCr(lr)
        val inputData = FloatArray(inW * inH) { y[it] / 255f }
        val shape = longArrayOf(1, 1, inH.toLong(), inW.toLong())
        val inputName = sess.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape)
        val outputName = sess.outputNames.first()
        val result = sess.run(java.util.Collections.singletonMap(inputName, inputTensor))

        try {
            @Suppress("UNCHECKED_CAST")
            val out = (result.get(outputName).orElseThrow().value as Array<Array<Array<FloatArray>>>)[0][0]
            val yOut = FloatArray(modelOutW * modelOutH)
            for (j in 0 until modelOutH) for (i in 0 until modelOutW) {
                yOut[j * modelOutW + i] = (out[j][i] * 255f).coerceIn(0f, 255f)
            }
            val cbUp = srUpscaleChannel(cb, inW, inH, scale)
            val crUp = srUpscaleChannel(cr, inW, inH, scale)
            val modelOut = srYcbcrToArgb(yOut, cbUp, crUp, modelOutW, modelOutH)

            // The model is fixed at 224x224 in / 672x672 (x3) out, but a benchmark
            // task may request a different resolution. Resize the model output to
            // the task's requested size so the result is directly comparable to
            // other engines and to the UI-selected task.
            val outW = task.outputWidth
            val outH = task.outputHeight
            val reconstructed = if (modelOutW == outW && modelOutH == outH) {
                modelOut
            } else {
                srResizeBilinear(modelOut, outW, outH)
            }

            // Quality is scored against the task's ground truth (the same HR image
            // the low-res input was downsampled from), so PSNR/SSIM reflect how well
            // the model reconstructs the actual frame rather than a synthetic pattern.
            // A user-supplied image has no ground truth, so PSNR/SSIM are undefined (-1).
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
}
