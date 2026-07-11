package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.QualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.processing.computePsnr
import com.m4isper.kmpmlbench.benchmark.domain.processing.computeSsim
import com.m4isper.kmpmlbench.benchmark.domain.processing.upsampleBilinear
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Stand-in engine (data-layer adapter) for the Super-Resolution task. It
 * performs a real but lightweight reconstruction: a bilinear upscale of the
 * low-res input, then PSNR/SSIM against the task's ground truth. The pixel work
 * also makes latency scale realistically with input size and upscale factor.
 *
 * This exercises the full harness end-to-end on Desktop (timing, percentiles,
 * throughput, quality) before any native ML dependency is wired in.
 */
class MockSuperResolutionEngine(
    private val task: SuperResolutionTask,
) : MlEngine {
    override val id: String = "mock-sr"
    override val displayName: String = "Mock SR Engine"

    override fun initialize() {
        // No weights to load for the mock; nothing to do.
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val reconstructed = upsampleBilinear(input.image, task.scale)
        val gt = task.groundTruth()
        val quality = QualityMetrics(
            psnr = computePsnr(gt, reconstructed),
            ssim = computeSsim(gt, reconstructed),
        )
        return BenchmarkOutput(
            width = reconstructed.width,
            height = reconstructed.height,
            image = reconstructed,
            quality = quality,
        )
    }

    override fun close() {
        // Nothing to release for the mock.
    }
}
