package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun blackInput(w: Int, h: Int) =
    BenchmarkInput(w, h, "x", ImageBuffer(w, h, IntArray(w * h) { 0xFF000000.toInt() }))

class MockSuperResolutionEngineTest {
    @Test
    fun inferReturnsUpscaledDimensionsAndQuality() {
        val task = SuperResolutionTask(scale = 4, inputWidth = 16, inputHeight = 16)
        val engine = MockSuperResolutionEngine(task)

        val output = engine.infer(blackInput(16, 16))

        assertEquals(64, output.width)
        assertEquals(64, output.height)
        val q = output.quality as SrQualityMetrics
        assertTrue(q.psnr.isFinite())
        assertTrue(q.psnr > 0.0)
        assertTrue(q.psnr < 100.0)
        assertTrue(q.ssim > 0.0)
        assertTrue(q.ssim <= 1.0)
    }

    @Test
    fun initializeAndCloseAreSafe() {
        val engine = MockSuperResolutionEngine(SuperResolutionTask(2, 8, 8))
        engine.initialize()
        engine.infer(blackInput(8, 8))
        engine.close()
    }
}
