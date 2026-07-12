package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.data.engine.OnnxObjectDetectionEngineIos
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * iOS engine registry. Object Detection now runs on the real ONNX Runtime
 * (Objective-C API, `onnxruntime-objc`) on-device; Super-Resolution and
 * Classification remain mock-only until their native engines are ported.
 */
actual fun platformEnginesFor(
    task: BenchmarkTask,
    customModelPath: String?,
    customLabelsPath: String?,
): List<MlEngine> {
    // Custom user models are not wired into the iOS engines yet (iOS build is
    // currently deferred); the parameters are accepted to keep the expect/actual
    // contract consistent across targets.
    return when (task) {
        is SuperResolutionTask -> listOf(MockSuperResolutionEngine(task))
        is ClassificationTask -> listOf(MockClassificationEngine(task))
        is ObjectDetectionTask -> listOf(
            OnnxObjectDetectionEngineIos(task),
            MockObjectDetectionEngine(task),
        )
        is LlmTask -> listOf(MockLlmEngine(task))
        else -> emptyList()
    }
}
