package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.QualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals

class BenchmarkResultMapperTest {
    @Test
    fun mapsDomainResultToUi() {
        val result = BenchmarkResult(
            engineId = "mock-sr",
            engineName = "Mock SR Engine",
            task = SuperResolutionTask(2, 16, 16),
            input = BenchmarkInput(16, 16, "LR", ImageBuffer(16, 16, IntArray(256))),
            output = BenchmarkOutput(
                32,
                32,
                ImageBuffer(32, 32, IntArray(1024)),
                QualityMetrics(30.0, 0.9),
            ),
            metrics = BenchmarkMetrics(
                initTimeMs = 12.0,
                warmupMs = 5.0,
                iterations = 10,
                avgLatencyMs = 2.5,
                minLatencyMs = 1.0,
                maxLatencyMs = 4.0,
                p50LatencyMs = 2.0,
                p95LatencyMs = 3.8,
                throughputFps = 400.0,
                peakMemoryMb = 50.0,
            ),
            quality = QualityMetrics(30.0, 0.9),
        )

        val ui = result.toUi()

        assertEquals("Mock SR Engine", ui.engineName)
        assertEquals("Super-Resolution ×2", ui.taskName)
        assertEquals(32, ui.outputWidth)
        assertEquals(32, ui.outputHeight)
        assertEquals(16, ui.inputImage.width)
        assertEquals(32, ui.outputImage.width)
        assertEquals(30.0, ui.psnr, 0.0)
        assertEquals(0.9, ui.ssim, 0.0)
        assertEquals(2.5, ui.avgLatencyMs, 0.0)
        assertEquals(400.0, ui.throughputFps, 0.0)
        assertEquals(50.0, ui.peakMemoryMb, 0.0)
        assertEquals(10, ui.iterations)
    }
}
