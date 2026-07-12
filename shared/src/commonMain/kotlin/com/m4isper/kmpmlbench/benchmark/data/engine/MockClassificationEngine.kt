package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import kotlin.math.exp

/**
 * Stand-in engine (data-layer adapter) for the Classification task. It performs
 * a real but lightweight inference: deterministic pseudo-logits derived from the
 * input pixels are softmaxed into per-class probabilities, and the top class is
 * scored against the input's expected label. The pixel work also makes latency
 * scale with input size, exercising the full harness (timing, percentiles,
 * throughput, quality) before any native ML dependency is wired in.
 */
class MockClassificationEngine(
    private val task: ClassificationTask,
) : MlEngine {
    override val id: String = "mock-cls"
    override val displayName: String = "Mock Classification Engine"

    override fun initialize() {
        // No weights to load for the mock; nothing to do.
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val n = task.numClasses
        val pixels = input.image.pixels
        // Deterministic pseudo-logits: each class keys off a different pixel shift.
        val logits = DoubleArray(n) { c ->
            var sum = 0.0
            for (i in pixels.indices) {
                val shift = (c * 7) % 24
                sum += ((pixels[i] shr shift) and 0xFF).toDouble()
            }
            sum
        }
        val max = logits.maxOrNull() ?: 0.0
        val exps = logits.map { exp(it - max) }
        val total = exps.sum()
        val probs = exps.map { it / total }

        val ranked = probs.withIndex().sortedByDescending { it.value }.take(5)
        val top = ranked.first()
        val predicted = task.classNames[top.index]
        val topK = ranked.map { task.classNames[it.index] to it.value }
        val accuracy = if (topK.any { it.first == input.label }) 1.0 else 0.0

        val quality = ClassificationQualityMetrics(
            predictedClass = predicted,
            confidence = top.value,
            topK = topK,
            accuracy = accuracy,
        )
        return BenchmarkOutput(input.width, input.height, input.image, quality)
    }

    override fun close() {
        // Nothing to release for the mock.
    }
}
