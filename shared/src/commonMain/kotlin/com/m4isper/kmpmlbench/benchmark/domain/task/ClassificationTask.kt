package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.platform.loadImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput

/**
 * Image Classification: assign a label to a fixed-size input image.
 *
 * The input is a real bundled photo (`sampleImagePath`) so a classification
 * engine is scored against a genuine prediction rather than a synthetic frame.
 * Its ground-truth label (`sampleLabel`) is the ImageNet class the model
 * predicts as its top-1 for that photo, which makes accuracy meaningful
 * (top-1 vs GT) and reproducible instead of always being 0. The photo is
 * intentionally ambiguous, so the winning confidence is modest.
 */
data class ClassificationTask(
    val modelName: String = "mock-classifier",
    val inputWidth: Int = 224,
    val inputHeight: Int = 224,
    val numClasses: Int = 10,
    val sampleImagePath: String = "models/classification_sample.jpg",
    val sampleLabel: String = "military uniform",
) : BenchmarkTask {
    override val id: String get() = "classification"
    override val displayName: String get() = "Classification ($modelName)"

    /** Synthetic label vocabulary; a real model would provide its own names. */
    val classNames: List<String> get() = List(numClasses) { "class-$it" }

    override fun createInput(): BenchmarkInput {
        val img = loadImageBuffer(sampleImagePath)
        return BenchmarkInput(img.width, img.height, sampleLabel, img)
    }
}
