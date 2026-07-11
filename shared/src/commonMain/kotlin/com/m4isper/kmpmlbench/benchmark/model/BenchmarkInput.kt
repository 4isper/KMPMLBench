package com.m4isper.kmpmlbench.benchmark.model

/**
 * Input handed to an engine for one inference. For Super-Resolution this is the
 * low-resolution source frame; real engines will also carry the pixel buffer,
 * but the mock engine only needs the dimensions to size its synthetic workload.
 */
data class BenchmarkInput(
    val width: Int,
    val height: Int,
    val label: String,
)
