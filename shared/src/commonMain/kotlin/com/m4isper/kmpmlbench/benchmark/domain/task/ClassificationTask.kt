package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage

/**
 * Image Classification: assign a label to a fixed-size input image.
 *
 * The input is a deterministic synthetic image tagged with an expected class
 * (derived from its pixels), so a classification engine can score its accuracy
 * against that label. The class list is synthetic (class-0..class-N) for now;
 * a real model would supply its own labels.
 */
data class ClassificationTask(
    val modelName: String = "mock-classifier",
    val inputWidth: Int = 224,
    val inputHeight: Int = 224,
    val numClasses: Int = 10,
) : BenchmarkTask {
    override val id: String get() = "classification"
    override val displayName: String get() = "Classification ($modelName)"

    /** Synthetic label vocabulary; a real model would provide its own names. */
    val classNames: List<String> get() = List(numClasses) { "class-$it" }

    override fun createInput(): BenchmarkInput {
        val img = generateSyntheticImage(inputWidth, inputHeight, seed = 123)
        val expected = classNames[img.pixels.sum().rem(numClasses).let { if (it < 0) it + numClasses else it }]
        return BenchmarkInput(inputWidth, inputHeight, expected, img)
    }
}
