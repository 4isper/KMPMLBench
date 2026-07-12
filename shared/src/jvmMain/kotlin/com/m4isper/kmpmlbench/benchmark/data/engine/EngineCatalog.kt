package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Desktop (JVM) engine registry: the real ONNX Runtime engine is offered with
 * two execution providers (default CPU and CoreML) for an apples-to-apples
 * latency comparison, plus the mock as a fallback so the harness keeps working
 * if the model is missing or fails to load.
 */
actual fun platformEnginesFor(
    task: BenchmarkTask,
    customModelPath: String?,
    customLabelsPath: String?,
): List<MlEngine> = when (task) {
    is SuperResolutionTask -> listOf(
        OnnxSuperResolutionEngine(task, customModelPath = customModelPath),
        OnnxSuperResolutionEngine(task, executionProvider = "coreml", customModelPath = customModelPath),
        MockSuperResolutionEngine(task),
    )
    is ClassificationTask -> listOf(
        OnnxClassificationEngine(task, customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        OnnxClassificationEngine(task, executionProvider = "coreml", customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        MockClassificationEngine(task),
    )
    is ObjectDetectionTask -> listOf(
        OnnxObjectDetectionEngine(task, customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        OnnxObjectDetectionEngine(task, executionProvider = "coreml", customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        MockObjectDetectionEngine(task),
    )
    is LlmTask -> listOf(MockLlmEngine(task))
    else -> emptyList()
}
