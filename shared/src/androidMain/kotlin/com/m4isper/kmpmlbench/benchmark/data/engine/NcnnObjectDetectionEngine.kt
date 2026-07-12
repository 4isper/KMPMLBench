package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.Box
import com.m4isper.kmpmlbench.benchmark.domain.model.Detection
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.data.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.data.platform.loadModelFile
import com.m4isper.kmpmlbench.benchmark.domain.processing.drawBoxes
import com.m4isper.kmpmlbench.benchmark.domain.processing.evaluateDetections
import com.m4isper.kmpmlbench.benchmark.domain.processing.iou
import com.m4isper.kmpmlbench.benchmark.domain.processing.resizeBilinear
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import kotlin.math.exp

/**
 * Real object-detection engine backed by NCNN (Android only), mirroring the
 * ONNX Runtime detection engine so the two stay directly comparable.
 *
 * Loads a YOLOv8-nano model converted to NCNN format (`yolov8n.param` + `.bin`,
 * the canonical ncnn export of `yolov8n.pt` — NOT the `onnx2ncnn` output, which
 * mis-converts YOLOv8's DFL head into all-NaN boxes) through the [NcnnNet] JNI
 * bridge, runs token-free single-blob inference on the RGB input (resized to
 * 640x640 and scaled to [0, 1], no ImageNet normalization), and decodes the
 * [144, 8400] output: the first 64 channels are the Distribution-Focal-Loss
 * regressors (4 coords x 16 bins, coord-major), softmaxed per coord and reduced
 * to an (left, top, right, bottom) offset in grid cells; the next 80 channels
 * are the per-class scores. Boxes are built from the anchor grid + stride and
 * the surviving detections are scored with mAP@0.5 against the task's
 * ground-truth boxes and drawn onto the output frame.
 *
 * The default confidence threshold is 0.7 (matching the ONNX engine, which
 * needs the raised threshold to surface its single correct `person` detection
 * on the bundled sample); a cleaner model would run at ~0.25-0.5.
 *
 * The native `libncnn_jni.so` (ncnn core + this project's JNI shim) is vendored
 * under `androidMain/jniLibs` and loaded by [NcnnNet].
 */
class NcnnObjectDetectionEngine(
    private val task: ObjectDetectionTask,
    private val paramPath: String = "models/yolov8n.param",
    private val binPath: String = "models/yolov8n.bin",
    private val labelsResourcePath: String = "models/coco_classes.txt",
    private val modelInputSize: Int = 640,
    private val confidenceThreshold: Float = 0.7f,
    private val nmsThreshold: Float = 0.45f,
    /** When set, load class names from this user-supplied file instead of [labelsResourcePath]. */
    val customLabelsPath: String? = null,
) : MlEngine {
    override val id: String = "ncnn-od"
    override val displayName: String = "NCNN Detection (YOLOv8n)"

    private var net: NcnnNet? = null
    private var labels: List<String> = emptyList()

    // YOLOv8n ncnn output is a flat [144][8400] block (ncnn mat w=144, h=8400):
    // 64 DFL regressor channels (4 coords x 16 bins, coord-major) + 80 class scores.
    // ncnn lays it out as out[anchor * 144 + channel].
    private val numAnchors = 8400
    private val regMax = 16
    private val numClasses = 80
    private val boxChannels = regMax * 4   // 64; class scores start at this offset

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
        val n = checkNotNull(net) { "NCNN detection engine not initialized" }
        val resized = resizeBilinear(input.image, modelInputSize, modelInputSize)
        val data = FloatArray(3 * modelInputSize * modelInputSize)
        for (i in 0 until modelInputSize * modelInputSize) {
            val p = resized.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // YOLOv8 preprocessing: scale to [0, 1], no mean/std normalization.
            data[i] = r / 255f
            data[modelInputSize * modelInputSize + i] = g / 255f
            data[2 * modelInputSize * modelInputSize + i] = b / 255f
        }
        // Output is a flat [144][8400] block (ncnn mat w=144, h=8400, c=1).
        val out = n.run(data, modelInputSize, modelInputSize, 3)

        val detections = mutableListOf<Detection>()
        for (a in 0 until numAnchors) {
            val (stride, gx, gy) = gridOf(a)
            // DFL: per coord, softmax the 16 bins and take the weighted bin index.
            val ltrb = FloatArray(4)
            for (k in 0 until 4) {
                var sum = 0f
                val e = FloatArray(regMax)
                for (i in 0 until regMax) {
                    e[i] = exp(out[a * 144 + k * regMax + i].toDouble()).toFloat()
                    sum += e[i]
                }
                var dis = 0f
                for (i in 0 until regMax) dis += i * e[i] / sum
                ltrb[k] = dis * stride
            }
            val cx = (gx + 0.5f) * stride
            val cy = (gy + 0.5f) * stride
            val x1 = cx - ltrb[0]
            val y1 = cy - ltrb[1]
            val x2 = cx + ltrb[2]
            val y2 = cy + ltrb[3]

            var bestClass = 0
            var bestScore = -1f
            for (c in 0 until numClasses) {
                val s = sigmoid(out[a * 144 + boxChannels + c])
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestScore < confidenceThreshold) continue
            // Coords are in model-input pixel space; normalize to [0, 1].
            val x = (x1 / modelInputSize).coerceIn(0f, 1f)
            val y = (y1 / modelInputSize).coerceIn(0f, 1f)
            val w = ((x2 - x1) / modelInputSize).coerceIn(0f, 1f)
            val h = ((y2 - y1) / modelInputSize).coerceIn(0f, 1f)
            val label = labels.getOrNull(bestClass) ?: "class-$bestClass"
            detections.add(Detection(Box(x, y, w, h), label, bestScore.toDouble()))
        }

        val kept = nms(detections, nmsThreshold)
        // A user-supplied image has no ground-truth boxes; mAP is undefined (-1).
        val mAP = if (input.isCustom) -1.0 else evaluateDetections(kept, task.groundTruthBoxes())
        val meanConfidence = if (kept.isEmpty()) 0.0 else kept.map { it.confidence }.average()
        val quality = DetectionQualityMetrics(
            numDetections = kept.size,
            meanConfidence = meanConfidence,
            mAP = mAP,
        )
        val outImg: ImageBuffer = drawBoxes(resized, kept)
        return BenchmarkOutput(resized.width, resized.height, outImg, quality)
    }

    override fun close() {
        net?.close()
        net = null
    }

    /** Maps a flattened anchor index to its (stride, gridX, gridY). */
    private fun gridOf(a: Int): Triple<Int, Int, Int> = when {
        a < 6400 -> Triple(8, a % 80, a / 80)
        a < 8000 -> {
            val i = a - 6400
            Triple(16, i % 40, i / 40)
        }
        else -> {
            val i = a - 8000
            Triple(32, i % 20, i % 20)
        }
    }

    /** Greedy non-maximum suppression: drop detections overlapping a higher-scoring one. */
    private fun nms(dets: List<Detection>, iouThr: Float): List<Detection> {
        val sorted = dets.sortedByDescending { it.confidence }
        val keep = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            keep.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(sorted[i].box, sorted[j].box) > iouThr) suppressed[j] = true
            }
        }
        return keep
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x.toDouble()))).toFloat()
}
