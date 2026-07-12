package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.data.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.data.platform.loadModelFile
import com.m4isper.kmpmlbench.benchmark.domain.processing.resizeBilinear
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import kotlin.math.exp

/**
 * Real image-classification engine backed by NCNN (Android only), mirroring the
 * ONNX Runtime classification engine so the two stay directly comparable.
 *
 * Loads a MobileNetV2 model converted to NCNN format (`mobilenetv2-12.param` +
 * `.bin`, produced from the same `mobilenetv2-12.onnx` via `onnx2ncnn`) through
 * the [NcnnNet] JNI bridge, runs token-free single-blob inference on the RGB
 * input (resized to 224x224 and normalized with ImageNet statistics), and
 * softmaxes the 1000-class logits into per-class probabilities. Top-1 accuracy
 * is scored against the task's ground-truth label on the same real bundled
 * photo as the ONNX engine.
 *
 * The native `libncnn_jni.so` (ncnn core + this project's JNI shim) is vendored
 * under `androidMain/jniLibs` and loaded by [NcnnNet].
 */
class NcnnClassificationEngine(
    private val task: ClassificationTask,
    private val paramPath: String = "models/mobilenetv2-12.param",
    private val binPath: String = "models/mobilenetv2-12.bin",
    private val labelsResourcePath: String = "models/imagenet_classes.txt",
    private val inputSize: Int = 224,
    /** When set, load the model from this user-supplied file instead of [paramPath]/[binPath]. */
    val customModelPath: String? = null,
    /** When set, load class names from this user-supplied file instead of [labelsResourcePath]. */
    val customLabelsPath: String? = null,
) : MlEngine {
    override val id: String = "ncnn-cls"
    override val displayName: String = "NCNN Classification (MobileNetV2 ×$inputSize)"

    private var net: NcnnNet? = null
    private var labels: List<String> = emptyList()

    override fun initialize() {
        val paramBytes = loadModelBytes(paramPath)
        val modelBytes = loadModelBytes(binPath)
        net = NcnnNet().also { it.load(paramBytes, modelBytes) }
        labels = if (customLabelsPath != null) {
            loadModelFile(customLabelsPath).decodeToString().lines()
        } else {
            loadModelBytes(labelsResourcePath).decodeToString().lines()
        }
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val n = checkNotNull(net) { "NCNN classification engine not initialized" }
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
        // Output is a flat 1000-logit vector (ncnn mat w=1000, h=1, c=1).
        val out = n.run(data, inputSize, inputSize, 3)
        val max = out.maxOrNull() ?: 0f
        val exps = out.map { exp((it - max).toDouble()) }
        val total = exps.sum()
        val probs = exps.map { it / total }

        val ranked = probs.withIndex().sortedByDescending { it.value }.take(5)
        val top = ranked.first()
        val predicted = labels.getOrNull(top.index) ?: "class-${top.index}"
        val topK = ranked.map { (labels.getOrNull(it.index) ?: "class-${it.index}") to it.value }
        // A user-supplied image has no ground-truth label, so accuracy is undefined (0 here).
        val accuracy = if (input.isCustom) 0.0 else if (topK.any { it.first == input.label }) 1.0 else 0.0

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
        net?.close()
        net = null
    }
}
