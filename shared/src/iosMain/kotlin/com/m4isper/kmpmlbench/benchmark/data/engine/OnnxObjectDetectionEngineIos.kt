@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.m4isper.kmpmlbench.benchmark.data.engine

import cocoapods.onnxruntime_objc.ORTEnv
import cocoapods.onnxruntime_objc.ORTLoggingLevel
import cocoapods.onnxruntime_objc.ORTSession
import cocoapods.onnxruntime_objc.ORTSessionOptions
import cocoapods.onnxruntime_objc.ORTTensorElementDataType
import cocoapods.onnxruntime_objc.ORTValue
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
import com.m4isper.kmpmlbench.benchmark.domain.platform.loadModelBytes
import com.m4isper.kmpmlbench.benchmark.domain.platform.resolveResourcePath
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSMutableArray
import platform.Foundation.NSMutableData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.arrayWithCapacity
import platform.Foundation.dataWithLength
import platform.Foundation.numberWithInt
import platform.posix.memcpy
import kotlin.math.exp

/**
 * Real object-detection engine backed by ONNX Runtime on iOS, via the
 * Objective-C API (`onnxruntime-objc`). Mirrors the JVM/Android
 * [OnnxObjectDetectionEngine]: loads a YOLOv8-nano model, runs actual inference
 * on the task's input (bilinearly resized to 640x640, RGB scaled to [0,1]),
 * decodes the [1, 84, 8400] output (sigmoid + argmax + greedy NMS), scores the
 * surviving detections with mAP@0.5 against the task's ground-truth boxes, and
 * draws them onto the output frame.
 *
 * The default confidence threshold is 0.7: the bundled `yolov8n.onnx` is a noisy
 * export that emits many low-confidence spurious boxes, so a higher threshold is
 * needed to surface its single correct `person` detection.
 */
class OnnxObjectDetectionEngineIos(
    private val task: ObjectDetectionTask,
    private val modelResourcePath: String = "models/yolov8n.onnx",
    private val labelsResourcePath: String = "models/coco_classes.txt",
    private val modelInputSize: Int = 640,
    private val confidenceThreshold: Float = 0.7f,
    private val nmsThreshold: Float = 0.45f,
    private val executionProvider: String = "cpu",
) : MlEngine {
    override val id: String = when (executionProvider) {
        "coreml" -> "onnx-od-coreml"
        else -> "onnx-od"
    }
    override val displayName: String =
        "ONNX Detection (YOLOv8n)" + when (executionProvider) {
            "coreml" -> " · CoreML"
            else -> ""
        }

    private var env: ORTEnv? = null
    private var session: ORTSession? = null
    private var inputName: String = "images"
    private var outputName: String = "output0"
    private var labels: List<String> = emptyList()

    override fun initialize() = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        env = ORTEnv(loggingLevel = ORTLoggingLevel.ORTLoggingLevelWarning, error = error.ptr)
        val envErr = error.value
        if (envErr != null) throw RuntimeException("ORTEnv: ${envErr.localizedDescription}")

        val modelPath = resolveResourcePath(modelResourcePath)
            ?: throw RuntimeException("model not found in bundle: $modelResourcePath")

        val optionsErr = alloc<ObjCObjectVar<NSError?>>()
        val options = ORTSessionOptions(error = optionsErr.ptr)
        if (optionsErr.value != null) throw RuntimeException("ORTSessionOptions: ${optionsErr.value!!.localizedDescription}")
        if (executionProvider == "coreml") {
            val epErr = alloc<ObjCObjectVar<NSError?>>()
            options.appendExecutionProvider(
                "CoreMLExecutionProvider",
                providerOptions = emptyMap<Any?, Any?>(),
                error = epErr.ptr,
            )
            if (epErr.value != null) throw RuntimeException("CoreML EP: ${epErr.value!!.localizedDescription}")
        }

        val sessionErr = alloc<ObjCObjectVar<NSError?>>()
        session = ORTSession(
            env = env!!,
            modelPath = modelPath,
            sessionOptions = options,
            error = sessionErr.ptr,
        )
        if (sessionErr.value != null) throw RuntimeException("ORTSession: ${sessionErr.value!!.localizedDescription}")

        val namesErr = alloc<ObjCObjectVar<NSError?>>()
        val inputs = session!!.inputNamesWithError(namesErr.ptr)
        if (namesErr.value == null && inputs != null && inputs.count() > 0) {
            inputName = inputs[0] as? String ?: inputName
        }
        val outputs = session!!.outputNamesWithError(namesErr.ptr)
        if (namesErr.value == null && outputs != null && outputs.count() > 0) {
            outputName = outputs[0] as? String ?: outputName
        }

        val labelBytes = loadModelBytes(labelsResourcePath)
        labels = labelBytes.decodeToString().lines()
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val sess = checkNotNull(session) { "ONNX detection engine not initialized" }
        val sz = modelInputSize
        val resized = resizeBilinear(input.image, sz, sz)

        val data = FloatArray(3 * sz * sz)
        for (i in 0 until sz * sz) {
            val p = resized.pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            data[i] = r / 255f
            data[sz * sz + i] = g / 255f
            data[2 * sz * sz + i] = b / 255f
        }

        val floats = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val byteCount = (data.size * 4).toULong()
            val mutableData = NSMutableData.dataWithLength(byteCount)
                ?: throw RuntimeException("cannot allocate input tensor data")
            data.usePinned { pinned ->
                memcpy(mutableData.mutableBytes, pinned.addressOf(0), byteCount)
            }

            val shapeArray = NSMutableArray.arrayWithCapacity(4U)
            shapeArray.add(NSNumber.numberWithInt(1))
            shapeArray.add(NSNumber.numberWithInt(3))
            shapeArray.add(NSNumber.numberWithInt(sz))
            shapeArray.add(NSNumber.numberWithInt(sz))

            val inputValue = ORTValue(
                tensorData = mutableData,
                shape = shapeArray,
                elementType = ORTTensorElementDataType.ORTTensorElementDataTypeFloat,
                error = error.ptr,
            )

            val runErr = alloc<ObjCObjectVar<NSError?>>()
            val outputNamesArray = NSMutableArray.arrayWithCapacity(1U)
            outputNamesArray.add(outputName)
            val outputs = sess.runWithInputs(
                mapOf(inputName to inputValue),
                outputNames = outputNamesArray.toSet(),
                runOptions = null,
                error = runErr.ptr,
            )
            if (runErr.value != null) throw RuntimeException("ORT run: ${runErr.value!!.localizedDescription}")
            val outputValue = outputs?.get(outputName) as? ORTValue
                ?: throw RuntimeException("missing output $outputName")

            val dataErr = alloc<ObjCObjectVar<NSError?>>()
            val outData = outputValue.tensorDataWithError(dataErr.ptr)
                ?: throw RuntimeException("tensorData: ${dataErr.value?.localizedDescription}")
            val floatPtr = outData.bytes?.reinterpret<kotlinx.cinterop.FloatVar>()
            val size = (outData.length / 4u).toInt()
            FloatArray(size) { floatPtr?.get(it) ?: 0f }
        }

        val numAnchors = 8400
        val numClasses = 84
        val detections = mutableListOf<Detection>()
        for (a in 0 until numAnchors) {
            var bestClass = 0
            var bestScore = -1f
            for (c in 0 until numClasses) {
                val s = sigmoid(floats[c * numAnchors + a])
                if (s > bestScore) {
                    bestScore = s
                    bestClass = c
                }
            }
            if (bestScore < confidenceThreshold) continue
            val cx = floats[0 * numAnchors + a]
            val cy = floats[1 * numAnchors + a]
            val bw = floats[2 * numAnchors + a]
            val bh = floats[3 * numAnchors + a]
            val x = ((cx - bw / 2) / sz).coerceIn(0f, 1f)
            val y = ((cy - bh / 2) / sz).coerceIn(0f, 1f)
            val w = (bw / sz).coerceIn(0f, 1f)
            val h = (bh / sz).coerceIn(0f, 1f)
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
    }

    override fun close() {
        session = null
        env = null
    }

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
