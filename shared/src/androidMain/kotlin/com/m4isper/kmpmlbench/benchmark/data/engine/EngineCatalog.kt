package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * Android engine registry. The real ONNX Runtime engine runs on-device via the
 * `onnxruntime-android` native build (same `ai.onnxruntime` API as Desktop),
 * offered once on the default CPU provider and once on NNAPI so the UI can
 * compare the two execution paths on the same model. The LiteRT (TensorFlow
 * Lite) classification engine adds a second real backend on-device. All real
 * engines are registered ahead of the mock.
 */
actual fun platformEnginesFor(task: BenchmarkTask): List<MlEngine> = when (task) {
    is SuperResolutionTask -> listOf(
        OnnxSuperResolutionEngine(task),
        OnnxSuperResolutionEngine(task, executionProvider = "nnapi"),
        MockSuperResolutionEngine(task),
    )
    is ClassificationTask -> listOf(
        OnnxClassificationEngine(task),
        OnnxClassificationEngine(task, executionProvider = "nnapi"),
        LiteRtClassificationEngine(task),
        MockClassificationEngine(task),
    )
    is ObjectDetectionTask -> listOf(
        OnnxObjectDetectionEngine(task),
        OnnxObjectDetectionEngine(task, executionProvider = "nnapi"),
        MockObjectDetectionEngine(task),
    )
    is LlmTask -> listOf(MockLlmEngine(task))
    else -> emptyList()
}
