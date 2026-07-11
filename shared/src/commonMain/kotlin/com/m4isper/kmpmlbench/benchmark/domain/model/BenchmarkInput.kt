package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Input handed to an engine for one inference. For Super-Resolution this is the
 * low-resolution source frame together with its pixel buffer.
 */
data class BenchmarkInput(
    val width: Int,
    val height: Int,
    val label: String,
    val image: ImageBuffer,
)
