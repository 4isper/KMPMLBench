package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Concrete [EngineProvider] (data-layer adapter). Real engines (ONNX Runtime,
 * TFLite, NCNN, MNN, ExecuTorch) will be registered here as they are implemented.
 */
object EngineCatalog : EngineProvider {
    override fun enginesFor(task: BenchmarkTask): List<MlEngine> = when (task) {
        is SuperResolutionTask -> listOf(MockSuperResolutionEngine(task))
        // New task types (Classification, Detection, ...) register their engines here.
        else -> emptyList()
    }
}
