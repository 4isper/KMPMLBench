package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
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
    fun classificationOffersOnnxAndMock() {
        val engines = EngineCatalog.enginesFor(ClassificationTask())
        assertEquals("onnx-cls", engines.first().id)
        assertTrue(engines.any { it.id == "mock-cls" })
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
}
