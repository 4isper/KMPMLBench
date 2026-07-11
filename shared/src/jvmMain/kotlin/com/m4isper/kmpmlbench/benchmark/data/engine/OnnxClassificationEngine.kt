package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import kotlin.math.exp
import java.nio.FloatBuffer

/**
 * Real image-classification engine backed by ONNX Runtime (Desktop/JVM only).
 *
 * Loads a MobileNetV2 model from resources and runs actual inference on the
 * task's input: the ARGB frame is bilinearly resized to 224x224, converted to
 * RGB and normalized with ImageNet statistics, fed to the model, and the
 * 1000-class logits are softmaxed into per-class probabilities. The top class
 * and its confidence are reported; accuracy is scored against the task's
 * (synthetic) input label so it stays comparable to the mock engine.
 *
 * The engine can optionally run on the CoreML execution provider (Apple
 * Neural Engine / GPU) instead of the default CPU provider, so the benchmark UI
 * can compare the two execution paths on the same model.
 */
class OnnxClassificationEngine(
    private val task: ClassificationTask,
    private val modelResourcePath: String = "models/mobilenetv2-12.onnx",
    private val labelsResourcePath: String = "models/imagenet_classes.txt",
    private val inputSize: Int = 224,
    private val executionProvider: String = "cpu",
) : MlEngine {
    override val id: String = if (executionProvider == "coreml") "onnx-cls-coreml" else "onnx-cls"
    override val displayName: String =
        "ONNX Classification (MobileNetV2)" + if (executionProvider == "coreml") " · CoreML" else ""

    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var labels: List<String> = emptyList()

    override fun initialize() {
        val modelBytes = checkNotNull(
            javaClass.classLoader.getResourceAsStream(modelResourcePath),
        ) { "ONNX model resource not found on classpath: $modelResourcePath" }.use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        if (executionProvider == "coreml") options.addCoreML()
        session = env.createSession(modelBytes, options)

        labels = javaClass.classLoader.getResourceAsStream(labelsResourcePath)
            ?.bufferedReader()
            ?.use { it.readLines() }
            ?: emptyList()
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val sess = checkNotNull(session) { "ONNX classification engine not initialized" }
        val resized = resizeBilinear(input.image, inputSize, inputSize)
        val data = FloatArray(3 * inputSize * inputSize)
        for (i in 0 until inputSize * inputSize) {
            val p = resized.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            data[i] = ((r / 255f) - 0.485f) / 0.229f
            data[inputSize * inputSize + i] = ((g / 255f) - 0.456f) / 0.224f
            data[2 * inputSize * inputSize + i] = ((b / 255f) - 0.406f) / 0.225f
        }
        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        val inputName = sess.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
        val outputName = sess.outputNames.first()
        val result = sess.run(java.util.Collections.singletonMap(inputName, inputTensor))
        try {
            @Suppress("UNCHECKED_CAST")
            val logits = (result.get(outputName).orElseThrow().value as Array<FloatArray>)[0]
            val max = logits.maxOrNull() ?: 0f
            val exps = logits.map { exp((it - max).toDouble()) }
            val total = exps.sum()
            val probs = exps.map { it / total }

            val ranked = probs.withIndex().sortedByDescending { it.value }.take(5)
            val top = ranked.first()
            val predicted = labels.getOrNull(top.index) ?: "class-${top.index}"
            val topK = ranked.map { (labels.getOrNull(it.index) ?: "class-${it.index}") to it.value }
            val accuracy = if (predicted == input.label) 1.0 else 0.0

            return BenchmarkOutput(
                width = input.width,
                height = input.height,
                image = input.image,
                quality = ClassificationQualityMetrics(
                    predictedClass = predicted,
                    confidence = top.value,
                    topK = topK,
                    accuracy = accuracy,
                ),
            )
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
