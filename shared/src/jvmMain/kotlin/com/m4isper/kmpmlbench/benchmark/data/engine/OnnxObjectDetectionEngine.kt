package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.Box
import com.m4isper.kmpmlbench.benchmark.domain.model.Detection
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.drawBoxes
import com.m4isper.kmpmlbench.benchmark.domain.processing.evaluateDetections
import com.m4isper.kmpmlbench.benchmark.domain.processing.iou
import com.m4isper.kmpmlbench.benchmark.domain.processing.resizeBilinear
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Real object-detection engine backed by ONNX Runtime (Desktop/JVM only).
 *
 * Loads a YOLOv8-nano model from resources and runs actual inference on the
 * task's input: the ARGB frame is bilinearly resized to the model's input size,
 * converted to RGB and scaled to [0, 1] (YOLOv8 preprocessing, no ImageNet
 * normalization), fed to the model, and the [1, 84, 8400] output is decoded —
 * per-anchor class scores are sigmoided, the best class is taken, boxes below
 * the confidence threshold are dropped, and greedy NMS removes duplicates. The
 * surviving detections are scored with mAP@0.5 against the task's synthetic
 * ground-truth boxes and drawn onto the output frame.
 *
 * The engine can optionally run on the CoreML execution provider (Apple
 * Neural Engine / GPU) instead of the default CPU provider, so the benchmark UI
 * can compare the two execution paths on the same model.
 */
class OnnxObjectDetectionEngine(
    private val task: ObjectDetectionTask,
    private val modelResourcePath: String = "models/yolov8n.onnx",
    private val labelsResourcePath: String = "models/coco_classes.txt",
    private val modelInputSize: Int = 640,
    private val confidenceThreshold: Float = 0.25f,
    private val nmsThreshold: Float = 0.45f,
    private val executionProvider: String = "cpu",
) : MlEngine {
    override val id: String = if (executionProvider == "coreml") "onnx-od-coreml" else "onnx-od"
    override val displayName: String =
        "ONNX Detection (YOLOv8n)" + if (executionProvider == "coreml") " · CoreML" else ""

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
        val sess = checkNotNull(session) { "ONNX detection engine not initialized" }
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
        val shape = longArrayOf(1, 3, modelInputSize.toLong(), modelInputSize.toLong())
        val inputName = sess.inputNames.first()
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
        val outputName = sess.outputNames.first()
        val result = sess.run(java.util.Collections.singletonMap(inputName, inputTensor))
        try {
            @Suppress("UNCHECKED_CAST")
            val raw = (result.get(outputName).orElseThrow().value as Array<Array<FloatArray>>)[0]
            // raw: [84][8400] = [4 box coords + 80 class scores][anchors]
            val numAnchors = raw[0].size
            val numClasses = raw.size - 4

            val detections = mutableListOf<Detection>()
            for (a in 0 until numAnchors) {
                var bestClass = 0
                var bestScore = -1f
                for (c in 0 until numClasses) {
                    val s = sigmoid(raw[4 + c][a])
                    if (s > bestScore) {
                        bestScore = s
                        bestClass = c
                    }
                }
                if (bestScore < confidenceThreshold) continue
                val cx = raw[0][a]
                val cy = raw[1][a]
                val bw = raw[2][a]
                val bh = raw[3][a]
                // Coords are in model-input pixel space; normalize to [0, 1].
                val x = ((cx - bw / 2) / modelInputSize).coerceIn(0f, 1f)
                val y = ((cy - bh / 2) / modelInputSize).coerceIn(0f, 1f)
                val w = (bw / modelInputSize).coerceIn(0f, 1f)
                val h = (bh / modelInputSize).coerceIn(0f, 1f)
                val label = labels.getOrNull(bestClass) ?: "class-$bestClass"
                detections.add(Detection(Box(x, y, w, h), label, bestScore.toDouble()))
            }

            val kept = nms(detections, nmsThreshold)
            val mAP = evaluateDetections(kept, task.groundTruthBoxes())
            val meanConfidence = if (kept.isEmpty()) 0.0 else kept.map { it.confidence }.average()
            val quality = DetectionQualityMetrics(
                numDetections = kept.size,
                meanConfidence = meanConfidence,
                mAP = mAP,
            )
            val outImg: ImageBuffer = drawBoxes(resized, kept)
            return BenchmarkOutput(resized.width, resized.height, outImg, quality)
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
