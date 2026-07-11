package com.m4isper.kmpmlbench.benchmark

import com.m4isper.kmpmlbench.benchmark.engine.MockSuperResolutionEngine
import com.m4isper.kmpmlbench.benchmark.task.SuperResolutionTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class BenchmarkRunnerTest {
    @Test
    fun runsSuperResolutionAndProducesSaneMetrics() = runBlocking {
        val task = SuperResolutionTask(scale = 2, inputWidth = 64, inputHeight = 64)
        val engine = MockSuperResolutionEngine(task)
        val runner = BenchmarkRunner()

        val result = runner.run(engine, task, iterations = 20, warmup = 2)

        assertTrue(result.engineId == "mock-sr", "engine id")
        assertTrue(result.metrics.iterations == 20, "iteration count")
        assertTrue(result.metrics.initTimeMs >= 0.0, "init time non-negative")
        assertTrue(result.metrics.avgLatencyMs > 0.0, "avg latency positive")
        assertTrue(
            result.metrics.minLatencyMs <= result.metrics.avgLatencyMs,
            "min <= avg",
        )
        assertTrue(
            result.metrics.maxLatencyMs >= result.metrics.avgLatencyMs,
            "max >= avg",
        )
        assertTrue(
            result.metrics.p95LatencyMs >= result.metrics.p50LatencyMs,
            "p95 >= p50",
        )
        assertTrue(result.metrics.throughputFps > 0.0, "throughput positive")
    }
}
