package com.m4isper.kmpmlbench.benchmark.domain.usecase

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask

/**
 * Application business rule: drive an [MlEngine] through cold start, warm-up,
 * and a measured loop, then aggregate timings into a [BenchmarkResult].
 *
 * Declared as an interface so the presentation layer depends on the abstraction
 * and tests can supply a fake. The concrete implementation is [BenchmarkRunner].
 */
interface BenchmarkUseCase {
    suspend fun run(
        engine: MlEngine,
        task: BenchmarkTask,
        iterations: Int,
        warmup: Int = 3,
        customInput: BenchmarkInput? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BenchmarkResult
}
