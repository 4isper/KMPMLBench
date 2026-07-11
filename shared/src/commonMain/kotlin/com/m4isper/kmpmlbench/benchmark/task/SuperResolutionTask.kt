package com.m4isper.kmpmlbench.benchmark.task

import com.m4isper.kmpmlbench.benchmark.model.BenchmarkInput

/**
 * A benchmarkable workload (Super-Resolution, Classification, Detection, ...).
 * Knows how to build its own input and describe itself for the UI.
 */
interface BenchmarkTask {
    val id: String
    val displayName: String

    /** Build the input sample used for every inference of this task. */
    fun createInput(): BenchmarkInput
}

/**
 * Super-Resolution: upscales a [inputWidth]x[inputHeight] frame by [scale].
 */
data class SuperResolutionTask(
    val scale: Int,
    val inputWidth: Int,
    val inputHeight: Int,
) : BenchmarkTask {
    override val id: String get() = "super-resolution"
    override val displayName: String get() = "Super-Resolution ×$scale"

    override fun createInput(): BenchmarkInput =
        BenchmarkInput(
            width = inputWidth,
            height = inputHeight,
            label = "Input ${inputWidth}×$inputHeight",
        )

    val outputWidth: Int get() = inputWidth * scale
    val outputHeight: Int get() = inputHeight * scale
}
