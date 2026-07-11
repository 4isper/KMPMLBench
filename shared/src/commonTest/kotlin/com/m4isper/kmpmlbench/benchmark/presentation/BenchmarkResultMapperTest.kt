package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
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
            output = BenchmarkOutput(32, 32),
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
            ),
        )

        val ui = result.toUi()

        assertEquals("Mock SR Engine", ui.engineName)
        assertEquals("Super-Resolution ×2", ui.taskName)
        assertEquals(32, ui.outputWidth)
        assertEquals(32, ui.outputHeight)
        assertEquals(2.5, ui.avgLatencyMs, 0.0)
        assertEquals(400.0, ui.throughputFps, 0.0)
        assertEquals(10, ui.iterations)
    }
}
