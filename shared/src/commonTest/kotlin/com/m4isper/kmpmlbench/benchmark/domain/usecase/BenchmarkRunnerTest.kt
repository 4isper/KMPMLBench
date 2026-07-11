package com.m4isper.kmpmlbench.benchmark.domain.usecase

import com.m4isper.kmpmlbench.benchmark.data.engine.MockSuperResolutionEngine
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkRunnerTest {
    @Test
    fun runsAndProducesSaneMetrics() = runBlocking {
        val task = SuperResolutionTask(scale = 2, inputWidth = 64, inputHeight = 64)
        val engine = MockSuperResolutionEngine(task)
        val runner = BenchmarkRunner()

        val result = runner.run(engine, task, iterations = 20, warmup = 2)

        assertEquals("mock-sr", result.engineId)
        assertEquals(20, result.metrics.iterations)
        assertEquals(task.outputWidth, result.output.width)
        assertEquals(task.outputHeight, result.output.height)
        assertTrue(result.metrics.initTimeMs >= 0.0, "init time non-negative")
        assertTrue(result.metrics.avgLatencyMs > 0.0, "avg latency positive")
        assertTrue(result.metrics.minLatencyMs <= result.metrics.avgLatencyMs, "min <= avg")
        assertTrue(result.metrics.maxLatencyMs >= result.metrics.avgLatencyMs, "max >= avg")
        assertTrue(result.metrics.p95LatencyMs >= result.metrics.p50LatencyMs, "p95 >= p50")
        assertTrue(result.metrics.throughputFps > 0.0, "throughput positive")
        assertEquals(task.inputWidth, result.input.width)
        assertEquals(task.inputHeight, result.input.height)
        assertTrue(result.quality.psnr.isFinite(), "psnr finite")
        assertTrue(result.quality.psnr > 0.0, "psnr positive")
        assertTrue(result.quality.ssim > 0.0, "ssim positive")
        assertTrue(result.quality.ssim <= 1.0, "ssim <= 1")
    }
}
