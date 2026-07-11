package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask

/**
 * iOS engine registry. The real ONNX Runtime engine is Desktop/Android-only for
 * now, so this target falls back to the mock until a mobile-native engine is added.
 */
actual fun platformEnginesFor(task: BenchmarkTask): List<MlEngine> = when (task) {
    is SuperResolutionTask -> listOf(MockSuperResolutionEngine(task))
    is ClassificationTask -> listOf(MockClassificationEngine(task))
    is ObjectDetectionTask -> listOf(MockObjectDetectionEngine(task))
    else -> emptyList()
}
