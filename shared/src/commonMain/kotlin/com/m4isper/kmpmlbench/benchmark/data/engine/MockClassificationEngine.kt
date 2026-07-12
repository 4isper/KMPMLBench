package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask

/**
 * Stand-in engine (data-layer adapter) for the Classification task. It exercises
 * the full harness (timing, percentiles, throughput, quality) without a native
 * model, so the benchmark UI can compare a real ONNX engine against a lightweight
 * reference on the same input.
 *
 * Unlike a real model it returns the input's ground-truth label as its top-1
 * prediction with a deterministic confidence, and fills the rest of the top-5
 * with genuine ImageNet class names. Predictions therefore use the same
 * vocabulary as the real ONNX classification engine, and accuracy is 1.0 for a
 * correctly labeled input — instead of the always-zero score a synthetic
 * `class-N` vocabulary would have produced.
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
        // Deterministic, input-dependent confidence in (0.7, 0.99] so latency
        // still scales with image size and results are reproducible.
        val pixels = input.image.pixels
        var acc = 0L
        for (i in pixels.indices) acc += (pixels[i] and 0xFF).toLong()
        val confidence = 0.7 + (acc % 1000) / 1000.0 * 0.29

        val predicted = input.label
        val others = REAL_CLASS_NAMES.filter { it != predicted }
        val k = minOf(5, task.numClasses)
        val remaining = (1.0 - confidence).coerceAtLeast(0.0)

        // Distribute the remaining probability over k-1 other real classes with
        // decreasing weights (1/2, 1/4, ...), normalized to the remaining mass.
        val topK = ArrayList<Pair<String, Double>>(k)
        topK.add(predicted to confidence)
        var weightSum = 0.0
        val weights = List(k - 1) { idx -> (1.0 / (1 shl (idx + 1))).also { weightSum += it } }
        for (i in 0 until k - 1) {
            val name = others[((acc + i * 7) % others.size).toInt()]
            topK.add(name to remaining * weights[i] / weightSum)
        }

        val accuracy = if (topK.any { it.first == input.label }) 1.0 else 0.0

        val quality = ClassificationQualityMetrics(
            predictedClass = predicted,
            confidence = confidence,
            topK = topK,
            accuracy = accuracy,
        )
        return BenchmarkOutput(input.width, input.height, input.image, quality)
    }

    override fun close() {
        // Nothing to release for the mock.
    }

    private companion object {
        // A curated subset of genuine ImageNet class names so the mock's top-5
        // shares the vocabulary of the real ONNX classification engine.
        val REAL_CLASS_NAMES = listOf(
            "military uniform", "suit", "bow tie", "Windsor tie", "mortarboard",
            "jersey", "lab coat", "crash helmet", "bulletproof vest",
            "pickelhaube", "bearskin", "cardigan", "gown", "pajama",
        )
    }
}
