package com.m4isper.kmpmlbench.benchmark.metrics

import com.m4isper.kmpmlbench.benchmark.task.BenchmarkTask

/** Full result of a benchmark run: which engine, which task, and the metrics. */
data class BenchmarkResult(
    val engineId: String,
    val engineName: String,
    val task: BenchmarkTask,
    val metrics: BenchmarkMetrics,
)
