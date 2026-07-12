package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.platform.loadImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.downsample
import com.m4isper.kmpmlbench.benchmark.domain.processing.resizeBilinear

/**
 * Super-Resolution: upscales a [inputWidth]x[inputHeight] frame by [scale].
 *
 * The ground truth is a real bundled photo (`sampleImagePath`) resized to the
 * task's output resolution, so quality is scored against genuine image content
 * rather than a synthetic pattern. The low-res input is that same photo
 * downsampled by [scale], giving the engine a real frame to reconstruct.
 */
data class SuperResolutionTask(
    val scale: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val sampleImagePath: String = "models/sr_sample.jpg",
) : BenchmarkTask {
    override val id: String get() = "super-resolution"
    override val displayName: String get() = "Super-Resolution ×$scale"

    val outputWidth: Int get() = inputWidth * scale
    val outputHeight: Int get() = inputHeight * scale

    /** Real high-resolution photo (resized to the output resolution) used as the quality reference. */
    fun groundTruth(): ImageBuffer = resizeBilinear(loadImageBuffer(sampleImagePath), outputWidth, outputHeight)

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
