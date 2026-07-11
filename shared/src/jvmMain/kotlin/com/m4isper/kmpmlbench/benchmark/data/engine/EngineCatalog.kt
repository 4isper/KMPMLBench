package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Desktop (JVM) engine registry: the real ONNX Runtime engine is offered with
 * two execution providers (default CPU and CoreML) for an apples-to-apples
 * latency comparison, plus the mock as a fallback so the harness keeps working
 * if the model is missing or fails to load.
 */
actual object EngineCatalog : EngineProvider {
    override fun enginesFor(task: BenchmarkTask): List<MlEngine> = when (task) {
        is SuperResolutionTask -> listOf(
            OnnxSuperResolutionEngine(task),
            OnnxSuperResolutionEngine(task, executionProvider = "coreml"),
            MockSuperResolutionEngine(task),
        )
        is ClassificationTask -> listOf(
            OnnxClassificationEngine(task),
            OnnxClassificationEngine(task, executionProvider = "coreml"),
            MockClassificationEngine(task),
        )
        is ObjectDetectionTask -> listOf(
            OnnxObjectDetectionEngine(task),
            MockObjectDetectionEngine(task),
        )
        else -> emptyList()
    }
}
