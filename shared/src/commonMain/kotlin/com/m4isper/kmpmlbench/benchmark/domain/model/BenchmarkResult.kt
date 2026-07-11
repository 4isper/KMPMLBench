package com.m4isper.kmpmlbench.benchmark.domain.model

import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask

/** Full result of a benchmark run: which engine, which task, the output, and the metrics. */
data class BenchmarkResult(
    val engineId: String,
    val engineName: String,
    val task: BenchmarkTask,
    val output: BenchmarkOutput,
    val metrics: BenchmarkMetrics,
)
