package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult

/** Maps a domain [BenchmarkResult] into the presentation model. */
fun BenchmarkResult.toUi(): BenchmarkResultUi = BenchmarkResultUi(
    engineName = engineName,
    taskName = task.displayName,
    outputWidth = output.width,
    outputHeight = output.height,
    inputImage = input.image,
    outputImage = output.image,
    psnr = quality.psnr,
    ssim = quality.ssim,
    initTimeMs = metrics.initTimeMs,
    avgLatencyMs = metrics.avgLatencyMs,
    minLatencyMs = metrics.minLatencyMs,
    maxLatencyMs = metrics.maxLatencyMs,
    p50LatencyMs = metrics.p50LatencyMs,
    p95LatencyMs = metrics.p95LatencyMs,
    throughputFps = metrics.throughputFps,
    iterations = metrics.iterations,
)
