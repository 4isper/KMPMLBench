package com.m4isper.kmpmlbench.benchmark.engine

import com.m4isper.kmpmlbench.benchmark.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.task.SuperResolutionTask

/**
 * Registry of engines available for a given task. Real engines (ONNX Runtime,
 * TFLite, ...) will be registered here as they are implemented.
 */
object EngineCatalog {
    fun enginesFor(task: BenchmarkTask): List<MlEngine> = when (task) {
        is SuperResolutionTask -> listOf(MockSuperResolutionEngine(task))
        // New task types (Classification, Detection, ...) register their engines here.
        else -> emptyList()
    }
}
