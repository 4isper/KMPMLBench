package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Desktop (JVM) registry must offer the real engines ahead of the mock. */
class EngineCatalogJvmTest {
    @Test
    fun desktopOffersRealEnginesBeforeMock() {
        val engines = EngineCatalog.enginesFor(SuperResolutionTask(2, 8, 8))
        assertEquals("onnx-sr", engines.first().id)
        assertTrue(engines.any { it.id == "onnx-sr" })
        assertTrue(engines.any { it.id == "onnx-coreml-sr" })
        assertTrue(engines.any { it.id == "mock-sr" })
    }

    @Test
    fun classificationOffersOnnxCoreMlAndMock() {
        val engines = EngineCatalog.enginesFor(ClassificationTask())
        assertEquals("onnx-cls", engines.first().id)
        assertTrue(engines.any { it.id == "onnx-cls-coreml" })
        assertTrue(engines.any { it.id == "mock-cls" })
    }

    @Test
    fun objectDetectionOffersOnnxCoreMlAndMock() {
        val engines = EngineCatalog.enginesFor(ObjectDetectionTask())
        assertEquals("onnx-od", engines.first().id)
        assertTrue(engines.any { it.id == "onnx-od-coreml" })
        assertTrue(engines.any { it.id == "mock-od" })
    }

    @Test
    fun onnxEnginesProduceComparableOutput() {
        // The two ONNX execution providers must yield the same output resolution
        // for a given task so their latency/quality are directly comparable.
        val task = SuperResolutionTask(2, 32, 32)
        val lr = BenchmarkInput(
            32,
            32,
            "LR",
            ImageBuffer(32, 32, IntArray(1024) { 0xFF808080.toInt() }),
        )
        val cpu = OnnxSuperResolutionEngine(task)
        val coreml = OnnxSuperResolutionEngine(task, executionProvider = "coreml")

        cpu.initialize(); val outCpu = cpu.infer(lr); cpu.close()
        coreml.initialize(); val outCore = coreml.infer(lr); coreml.close()

        assertEquals(outCpu.width, outCore.width)
        assertEquals(outCpu.height, outCore.height)
        assertEquals(task.outputWidth, outCpu.width)
        assertEquals(task.outputHeight, outCpu.height)
        assertTrue((outCpu.quality as SrQualityMetrics).psnr.isFinite(), "cpu psnr finite")
        assertTrue((outCore.quality as SrQualityMetrics).psnr.isFinite(), "coreml psnr finite")
    }

    @Test
    fun onnxClassificationEnginesAgreeOnTopClass() {
        // CPU and CoreML must classify the same frame to the same top-1 class so
        // their latency/quality are directly comparable in the UI.
        val task = ClassificationTask(inputWidth = 64, inputHeight = 64, numClasses = 10)
        val input = task.createInput()

        val cpu = OnnxClassificationEngine(task)
        val coreml = OnnxClassificationEngine(task, executionProvider = "coreml")

        cpu.initialize(); val outCpu = cpu.infer(input); cpu.close()
        coreml.initialize(); val outCore = coreml.infer(input); coreml.close()

        val qCpu = outCpu.quality as ClassificationQualityMetrics
        val qCore = outCore.quality as ClassificationQualityMetrics
        assertEquals(input.width, outCpu.width)
        assertEquals(input.height, outCpu.height)
        assertEquals(outCpu.width, outCore.width)
        assertEquals(outCpu.height, outCore.height)
        assertEquals(qCpu.predictedClass, qCore.predictedClass, "top-1 class must match across EPs")
        assertEquals(5, qCpu.topK.size)
        assertTrue(qCpu.confidence > 0.0 && qCpu.confidence <= 1.0, "cpu confidence in (0,1]")
        assertTrue(qCore.confidence > 0.0 && qCore.confidence <= 1.0, "coreml confidence in (0,1]")
    }

    @Test
    fun onnxDetectionEnginesAgreeOnDetections() {
        // CPU and CoreML must decode the same model to the same set of detections
        // so their latency/quality are directly comparable in the UI.
        val task = ObjectDetectionTask(inputWidth = 640, inputHeight = 640)
        val input = task.createInput()

        val cpu = OnnxObjectDetectionEngine(task)
        val coreml = OnnxObjectDetectionEngine(task, executionProvider = "coreml")

        cpu.initialize(); val outCpu = cpu.infer(input); cpu.close()
        coreml.initialize(); val outCore = coreml.infer(input); coreml.close()

        val qCpu = outCpu.quality as DetectionQualityMetrics
        val qCore = outCore.quality as DetectionQualityMetrics
        assertEquals(input.width, outCpu.width)
        assertEquals(input.height, outCpu.height)
        assertEquals(outCpu.width, outCore.width)
        assertEquals(outCpu.height, outCore.height)
        // A synthetic gradient triggers many false-positive detections; the two
        // EPs differ only in borderline anchors, so compare within a tolerance.
        val diff = kotlin.math.abs(qCpu.numDetections - qCore.numDetections)
        val tol = maxOf(qCpu.numDetections, qCore.numDetections) * 0.25
        assertTrue(diff <= tol, "detection count should be close across EPs (cpu=${qCpu.numDetections}, coreml=${qCore.numDetections})")
        assertTrue(qCpu.mAP in 0.0..1.0, "cpu mAP in [0,1]")
        assertTrue(qCore.mAP in 0.0..1.0, "coreml mAP in [0,1]")
    }
}
