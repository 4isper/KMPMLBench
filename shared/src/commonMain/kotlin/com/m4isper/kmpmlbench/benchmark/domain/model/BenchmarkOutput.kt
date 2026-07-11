package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Output produced by an engine for one inference. For Super-Resolution this is
 * the upscaled frame together with its pixel buffer and quality metrics.
 */
data class BenchmarkOutput(
    val width: Int,
    val height: Int,
    val image: ImageBuffer,
    val quality: QualityMetrics,
)
