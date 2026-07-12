package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.resizeBilinear
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Real image-classification engine backed by TensorFlow Lite (LiteRT) on Android.
 *
 * Loads a float32 MobileNetV2 model from assets and runs actual on-device
 * inference on the task's input: the ARGB frame is bilinearly resized to
 * 224x224, converted to RGB and normalized with ImageNet statistics
 * (per-channel mean/std), fed to the interpreter in NHWC layout, and the
 * 1000-class logits are softmaxed into per-class probabilities. The top class
 * and its confidence are reported; accuracy is the standard ImageNet top-5 check
 * (is the input's ground-truth label present in the predicted top-5?).
 *
 * The interpreter runs on the CPU with the XNNPACK delegate enabled for speed
 * (the current LiteRT release does not ship a usable NNAPI delegate artifact).
 */
class LiteRtClassificationEngine(
    private val task: ClassificationTask,
    private val modelResourcePath: String = "models/mobilenetv2-224.tflite",
    private val labelsResourcePath: String = "models/imagenet_classes.txt",
    private val inputSize: Int = 224,
) : MlEngine {
    override val id: String = "tflite-cls"
    override val displayName: String = "LiteRT Classification (MobileNetV2)"

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    override fun initialize() {
        val modelBytes = loadModelBytes(modelResourcePath)
        val buffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        buffer.put(modelBytes)
        buffer.rewind()

        labels = loadModelBytes(labelsResourcePath).decodeToString().lines()

        val options = Interpreter.Options().apply { setUseXNNPACK(true) }
        interpreter = Interpreter(buffer, options)
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val interp = checkNotNull(interpreter) { "LiteRT classification engine not initialized" }
        val resized = resizeBilinear(input.image, inputSize, inputSize)

        val pixels = inputSize * inputSize
        val inputBuffer =
            ByteBuffer.allocateDirect(pixels * 3 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until pixels) {
            val p = resized.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            inputBuffer.putFloat(((r / 255f) - 0.485f) / 0.229f)
            inputBuffer.putFloat(((g / 255f) - 0.456f) / 0.224f)
            inputBuffer.putFloat(((b / 255f) - 0.406f) / 0.225f)
        }
        inputBuffer.rewind()

        val numClasses = interp.getOutputTensor(0).shape()[1]
        val output = Array(1) { FloatArray(numClasses) }
        interp.run(inputBuffer, output)

        val logits = output[0]
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - max).toDouble()) }
        val total = exps.sum()
        val probs = exps.map { it / total }

        val ranked = probs.withIndex().sortedByDescending { it.value }.take(5)
        val top = ranked.first()
        val predicted = labels.getOrNull(top.index) ?: "class-${top.index}"
        val topK = ranked.map { (labels.getOrNull(it.index) ?: "class-${it.index}") to it.value }
        val accuracy = if (topK.any { it.first == input.label }) 1.0 else 0.0

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
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}
