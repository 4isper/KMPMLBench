package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Collected timing statistics for a single benchmark run.
 *
 * These are the engine-agnostic "speed" metrics from the project roadmap
 * (initialization time, inference latency, throughput). Quality metrics such as
 * PSNR/SSIM are intentionally omitted here — they belong to real engines that
 * produce actual output pixels.
 */
data class BenchmarkMetrics(
    /** Cold-start model load time (ms). */
    val initTimeMs: Double,
    /** Total time spent in warm-up iterations, or null when warm-up was skipped. */
    val warmupMs: Double?,
    val iterations: Int,
    val avgLatencyMs: Double,
    val minLatencyMs: Double,
    val maxLatencyMs: Double,
    val p50LatencyMs: Double,
    val p95LatencyMs: Double,
    /** Inferences per second derived from the average latency. */
    val throughputFps: Double,
    /** Peak process memory observed during the run, in megabytes (best-effort). */
    val peakMemoryMb: Double,
)
