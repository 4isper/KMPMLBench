package com.m4isper.kmpmlbench.benchmark.model

/**
 * Output produced by an engine for one inference. For Super-Resolution this is
 * the upscaled frame; the mock engine reports the expected upscaled dimensions
 * without performing any pixel work.
 */
data class BenchmarkOutput(
    val width: Int,
    val height: Int,
)
