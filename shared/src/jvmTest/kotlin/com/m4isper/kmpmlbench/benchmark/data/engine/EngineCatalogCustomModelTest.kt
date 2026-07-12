package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that user-supplied model/labels paths flow from [EngineCatalog]
 * through [platformEnginesFor] into the concrete ONNX engines, so the UI can
 * benchmark a custom `.onnx` instead of the bundled resources.
 */
class EngineCatalogCustomModelTest {
    @Test
    fun classificationPassesCustomModelAndLabelsToOnnxEngine() {
        val engines = EngineCatalog.enginesFor(
            ClassificationTask(),
            customModelPath = "/models/mine.onnx",
            customLabelsPath = "/models/mine.txt",
        )
        val onnx = engines.filterIsInstance<OnnxClassificationEngine>()
        assertTrue(onnx.isNotEmpty(), "expected ONNX classification engine")
        onnx.forEach {
            assertEquals("/models/mine.onnx", it.customModelPath)
            assertEquals("/models/mine.txt", it.customLabelsPath)
        }
    }

    @Test
    fun detectionPassesCustomModelAndLabelsToOnnxEngine() {
        val engines = EngineCatalog.enginesFor(
            ObjectDetectionTask(),
            customModelPath = "/models/od.onnx",
            customLabelsPath = "/models/od.txt",
        )
        val onnx = engines.filterIsInstance<OnnxObjectDetectionEngine>()
        assertTrue(onnx.isNotEmpty())
        onnx.forEach {
            assertEquals("/models/od.onnx", it.customModelPath)
            assertEquals("/models/od.txt", it.customLabelsPath)
        }
    }

    @Test
    fun superResolutionPassesCustomModelOnly() {
        val engines = EngineCatalog.enginesFor(
            SuperResolutionTask(2, 128, 128),
            customModelPath = "/models/sr.onnx",
        )
        val onnx = engines.filterIsInstance<OnnxSuperResolutionEngine>()
        assertTrue(onnx.isNotEmpty())
        onnx.forEach { assertEquals("/models/sr.onnx", it.customModelPath) }
    }

    @Test
    fun noCustomPathsYieldsBundledEngines() {
        val engines = EngineCatalog.enginesFor(ClassificationTask())
        engines.filterIsInstance<OnnxClassificationEngine>().forEach {
            assertEquals(null, it.customModelPath)
            assertEquals(null, it.customLabelsPath)
        }
    }
}
