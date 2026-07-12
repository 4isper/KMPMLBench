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
actual fun platformEnginesFor(
    task: BenchmarkTask,
    customModelPath: String?,
    customLabelsPath: String?,
): List<MlEngine> = when (task) {
    is SuperResolutionTask -> listOf(
        OnnxSuperResolutionEngine(task, customModelPath = customModelPath),
        OnnxSuperResolutionEngine(task, executionProvider = "nnapi", customModelPath = customModelPath),
        NcnnSuperResolutionEngine(task),
        MockSuperResolutionEngine(task),
    )
    is ClassificationTask -> listOf(
        OnnxClassificationEngine(task, customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        OnnxClassificationEngine(task, executionProvider = "nnapi", customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        LiteRtClassificationEngine(task),
        NcnnClassificationEngine(task),
        MockClassificationEngine(task),
    )
    is ObjectDetectionTask -> listOf(
        OnnxObjectDetectionEngine(task, customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        OnnxObjectDetectionEngine(task, executionProvider = "nnapi", customModelPath = customModelPath, customLabelsPath = customLabelsPath),
        MockObjectDetectionEngine(task),
    )
    is LlmTask -> buildList {
        // The real engine only registers once the user supplies an ORT GenAI
        // model folder (out-of-band); otherwise fall back to the mock.
        if (customModelPath != null) add(OnnxLlmEngine(task, customModelPath = customModelPath))
        add(MockLlmEngine(task))
    }
    else -> emptyList()
}
