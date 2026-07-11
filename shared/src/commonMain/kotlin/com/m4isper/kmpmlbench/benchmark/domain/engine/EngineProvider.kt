package com.m4isper.kmpmlbench.benchmark.domain.engine

import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask

/**
 * Resolves the concrete [MlEngine] implementations available for a given task.
 *
 * Declared in the domain layer so the presentation layer can depend on this
 * abstraction rather than the concrete [com.m4isper.kmpmlbench.benchmark.data.engine.EngineCatalog];
 * the concrete implementation lives in the `data` layer.
 */
interface EngineProvider {
    fun enginesFor(task: BenchmarkTask): List<MlEngine>
}
