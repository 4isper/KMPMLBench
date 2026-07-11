package com.m4isper.kmpmlbench.benchmark.data.platform

/**
 * Current process memory usage in megabytes. Best-effort and platform-specific:
 * the JVM targets report used heap, while platforms without a portable counter
 * report 0.0. Used by [com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkRunner]
 * to track peak memory across a benchmark run.
 */
expect fun currentMemoryUsageMb(): Double
