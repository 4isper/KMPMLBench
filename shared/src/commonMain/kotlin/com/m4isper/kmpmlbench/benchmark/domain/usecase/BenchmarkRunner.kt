package com.m4isper.kmpmlbench.benchmark.domain.usecase

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.data.platform.currentMemoryUsageMb
import kotlin.math.max
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete [BenchmarkUseCase]: drives an [MlEngine] through a cold start,
 * warm-up, and a measured loop, then aggregates the timings.
 *
 * All heavy work runs on [Dispatchers.Default] so the UI thread is never blocked.
 */
class BenchmarkRunner : BenchmarkUseCase {
    override suspend fun run(
        engine: MlEngine,
        task: BenchmarkTask,
        iterations: Int,
        warmup: Int,
        customInput: BenchmarkInput?,
        onProgress: (done: Int, total: Int) -> Unit,
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        val initTimeMs = measureMs { engine.initialize() }
        var peakMemoryMb = currentMemoryUsageMb()

        val input = customInput ?: task.createInput()

        var warmupMs: Double? = null
        repeat(warmup) {
            warmupMs = (warmupMs ?: 0.0) + measureMs { engine.infer(input) }
            peakMemoryMb = max(peakMemoryMb, currentMemoryUsageMb())
        }

        var sampleOutput: BenchmarkOutput? = null
        val latencies = DoubleArray(iterations.coerceAtLeast(1)) { i ->
            val ms = measureMs {
                val out = engine.infer(input)
                if (i == 0) sampleOutput = out
            }
            peakMemoryMb = max(peakMemoryMb, currentMemoryUsageMb())
            onProgress(i + 1, iterations)
            ms
        }

        val metrics = computeMetrics(initTimeMs, warmupMs, latencies, peakMemoryMb)
        val output = sampleOutput ?: engine.infer(input)
        val quality = output.quality

        engine.close()

        BenchmarkResult(
            engineId = engine.id,
            engineName = engine.displayName,
            task = task,
            input = input,
            output = output,
            metrics = metrics,
            quality = quality,
            isCustom = input.isCustom,
        )
    }

    private fun computeMetrics(
        initTimeMs: Double,
        warmupMs: Double?,
        latencies: DoubleArray,
        peakMemoryMb: Double,
    ): BenchmarkMetrics {
        val sorted = latencies.sorted()
        val sum = sorted.sum()
        val avg = sum / sorted.size
        return BenchmarkMetrics(
            initTimeMs = initTimeMs,
            warmupMs = warmupMs,
            iterations = sorted.size,
            avgLatencyMs = avg,
            minLatencyMs = sorted.first(),
            maxLatencyMs = sorted.last(),
            p50LatencyMs = Stats.percentile(sorted, 50.0),
            p95LatencyMs = Stats.percentile(sorted, 95.0),
            throughputFps = if (avg > 0.0) 1000.0 / avg else 0.0,
            peakMemoryMb = peakMemoryMb,
        )
    }
}

private inline fun measureMs(block: () -> Unit): Double {
    val start = TimeSource.Monotonic.markNow()
    block()
    return start.elapsedNow().inWholeMicroseconds / 1000.0
}
