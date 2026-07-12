package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask

/** Platform-specific engine registry entries, provided by each target. */
expect fun platformEnginesFor(
    task: BenchmarkTask,
    customModelPath: String? = null,
    customLabelsPath: String? = null,
): List<MlEngine>

/**
 * Resolves the available [MlEngine]s for a task by delegating to the
 * platform-specific [platformEnginesFor]. Declared as a plain `object` (not an
 * `expect object`) so the metadata build stays simple; the per-platform
 * variation lives in [platformEnginesFor].
 */
object EngineCatalog : EngineProvider {
    override fun enginesFor(
        task: BenchmarkTask,
        customModelPath: String?,
        customLabelsPath: String?,
    ): List<MlEngine> = platformEnginesFor(task, customModelPath, customLabelsPath)
}
