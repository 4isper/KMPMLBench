package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.downsample
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage

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

    val outputWidth: Int get() = inputWidth * scale
    val outputHeight: Int get() = inputHeight * scale

    /** Deterministic high-resolution ground truth used as the quality reference. */
    fun groundTruth(): ImageBuffer = generateSyntheticImage(outputWidth, outputHeight, seed = 42)

    override fun createInput(): BenchmarkInput {
        val lr = downsample(groundTruth(), scale)
        return BenchmarkInput(
            width = inputWidth,
            height = inputHeight,
            label = "LR ${inputWidth}×$inputHeight",
            image = lr,
        )
    }
}
