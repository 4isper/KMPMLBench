package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.Box
import com.m4isper.kmpmlbench.benchmark.domain.model.Detection
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage
import com.m4isper.kmpmlbench.benchmark.domain.platform.loadImageBuffer

/**
 * Object Detection: locate and classify objects inside a fixed-size input image.
 *
 * By default the input is the bundled real photo (`classification_sample.jpg`,
 * a portrait of a person) and the quality reference is a real ground-truth
 * `person` box, so the real ONNX engine is scored against a genuine detection
 * instead of synthetic boxes. The class vocabulary is synthetic
 * (`class-0..class-N`) to mirror the other mock-friendly tasks; a real model
 * supplies its own labels (e.g. COCO).
 */
data class ObjectDetectionTask(
    val modelName: String = "mock-detector",
    val inputWidth: Int = DEFAULT_SIZE,
    val inputHeight: Int = DEFAULT_SIZE,
    val numClasses: Int = 80,
    val numGroundTruth: Int = 3,
    val sampleImagePath: String = "models/classification_sample.jpg",
    val sampleGroundTruth: List<Detection> = DEFAULT_GROUND_TRUTH,
) : BenchmarkTask {
    override val id: String get() = "object-detection"
    override val displayName: String get() = "Object Detection ($modelName)"

    /** Synthetic label vocabulary; a real model would provide its own names. */
    val classNames: List<String> get() = List(numClasses) { "class-$it" }

    /** Ground-truth boxes used as the quality reference (real sample when present). */
    fun groundTruthBoxes(): List<Detection> {
        if (sampleGroundTruth.isNotEmpty()) return sampleGroundTruth
        val rng = kotlin.random.Random(seed = 7)
        return List(numGroundTruth) { i ->
            val cx = 0.2f + rng.nextFloat() * 0.6f
            val cy = 0.2f + rng.nextFloat() * 0.6f
            val w = 0.08f + rng.nextFloat() * 0.12f
            val h = 0.08f + rng.nextFloat() * 0.12f
            Detection(
                box = Box((cx - w / 2), (cy - h / 2), w, h),
                label = classNames[(i * 13) % numClasses],
                confidence = 1.0,
            )
        }
    }

    override fun createInput(): BenchmarkInput {
        val path = sampleImagePath
        return try {
            val img = loadImageBuffer(path)
            BenchmarkInput(img.width, img.height, "OD ${img.width}×${img.height}", img)
        } catch (e: Exception) {
            val img = generateSyntheticImage(inputWidth, inputHeight, seed = 99)
            BenchmarkInput(inputWidth, inputHeight, "OD ${inputWidth}×$inputHeight", img)
        }
    }

    companion object {
        const val DEFAULT_SIZE = 640
        /** Real ground-truth `person` box for the bundled sample photo. */
        val DEFAULT_GROUND_TRUTH: List<Detection> = listOf(
            Detection(Box(0.002f, 0.035f, 0.996f, 0.964f), "person", 1.0),
        )
    }
}
