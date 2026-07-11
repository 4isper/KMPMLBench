package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.Box
import com.m4isper.kmpmlbench.benchmark.domain.model.Detection
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage

/**
 * Object Detection: locate and classify objects inside a fixed-size input image.
 *
 * The input is a deterministic synthetic image; the task also owns a set of
 * ground-truth boxes (derived from a seed) so an engine can score its detections
 * with mAP@0.5. The class vocabulary is synthetic (class-0..class-N) to mirror
 * the other mock-friendly tasks; a real model supplies its own labels (e.g. COCO).
 */
data class ObjectDetectionTask(
    val modelName: String = "mock-detector",
    val inputWidth: Int = DEFAULT_SIZE,
    val inputHeight: Int = DEFAULT_SIZE,
    val numClasses: Int = 80,
    val numGroundTruth: Int = 3,
) : BenchmarkTask {
    override val id: String get() = "object-detection"
    override val displayName: String get() = "Object Detection ($modelName)"

    /** Synthetic label vocabulary; a real model would provide its own names. */
    val classNames: List<String> get() = List(numClasses) { "class-$it" }

    /** Deterministic ground-truth boxes used as the quality reference. */
    fun groundTruthBoxes(): List<Detection> {
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
        val img = generateSyntheticImage(inputWidth, inputHeight, seed = 99)
        return BenchmarkInput(inputWidth, inputHeight, "OD ${inputWidth}×$inputHeight", img)
    }

    companion object {
        const val DEFAULT_SIZE = 640
    }
}
