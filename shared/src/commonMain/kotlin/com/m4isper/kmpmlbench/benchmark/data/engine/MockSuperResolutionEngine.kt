package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Stand-in engine (data-layer adapter) for the Super-Resolution task. It
 * performs no real inference; instead it runs a synthetic convolution sized to
 * the *output* resolution so that measured latency scales realistically with
 * input size and upscale factor.
 *
 * This lets the benchmarking harness (timing, percentiles, throughput) be
 * exercised end-to-end on Desktop before any native ML dependency is wired in.
 */
class MockSuperResolutionEngine(
    private val task: SuperResolutionTask,
) : MlEngine {
    override val id: String = "mock-sr"
    override val displayName: String = "Mock SR Engine"

    // Reference kept only to prevent the optimizer from discarding the workload.
    private var sink: Float = 0f

    override fun initialize() {
        // Simulate model-weight loading / tensor allocation.
        val weights = FloatArray(1_000_000) { (it and 0xFF).toFloat() }
        var acc = 0f
        for (i in 0 until 500_000) acc += weights[i]
        sink = acc
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val outPixels = (task.outputWidth.toLong() * task.outputHeight.toLong())
            .coerceAtMost(2_000_000L)
            .toInt()
        val buffer = FloatArray(outPixels) { (it % 255).toFloat() }
        // A handful of separable passes; cost grows with the output resolution.
        val passes = maxOf(1, (outPixels / 60_000).coerceAtMost(40))
        var acc = 0f
        repeat(passes) {
            for (i in 1 until outPixels - 1) {
                buffer[i] = (buffer[i - 1] + buffer[i] + buffer[i + 1]) / 3f
                acc += buffer[i]
            }
        }
        sink += acc
        return BenchmarkOutput(task.outputWidth, task.outputHeight)
    }

    override fun close() {
        sink = 0f
    }
}
