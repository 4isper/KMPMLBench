package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput

/**
 * A benchmarkable workload (Super-Resolution, Classification, Detection, ...).
 * Knows how to build its own input and describe itself for the UI.
 */
interface BenchmarkTask {
    val id: String
    val displayName: String

    /** Build the input sample used for every inference of this task. */
    fun createInput(): BenchmarkInput
}
