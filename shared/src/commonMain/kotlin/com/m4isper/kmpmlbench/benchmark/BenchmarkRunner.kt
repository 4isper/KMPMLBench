package com.m4isper.kmpmlbench.benchmark

import com.m4isper.kmpmlbench.benchmark.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.metrics.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.metrics.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.task.BenchmarkTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drives an [MlEngine] through a cold start, warm-up, and a measured loop, then
 * aggregates the timings into [BenchmarkMetrics].
 *
 * All heavy work runs on [Dispatchers.Default] so the UI thread is never blocked.
 * The optional [onProgress] callback reports completion of each measured
 * iteration for live progress display.
 */
class BenchmarkRunner {
    suspend fun run(
        engine: MlEngine,
        task: BenchmarkTask,
        iterations: Int,
        warmup: Int = 3,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        val initTimeMs = measureMs { engine.initialize() }

        val input = task.createInput()

        var warmupMs: Double? = null
        repeat(warmup) {
            warmupMs = (warmupMs ?: 0.0) + measureMs { engine.infer(input) }
        }

        val latencies = DoubleArray(iterations) { i ->
            val ms = measureMs { engine.infer(input) }
            onProgress(i + 1, iterations)
            ms
        }

        val metrics = computeMetrics(initTimeMs, warmupMs, latencies)

        engine.close()

        BenchmarkResult(
            engineId = engine.id,
            engineName = engine.displayName,
            task = task,
            metrics = metrics,
        )
    }

    private fun computeMetrics(
        initTimeMs: Double,
        warmupMs: Double?,
        latencies: DoubleArray,
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
            p50LatencyMs = percentile(sorted, 50.0),
            p95LatencyMs = percentile(sorted, 95.0),
            throughputFps = if (avg > 0.0) 1000.0 / avg else 0.0,
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val rank = (p / 100.0) * (sorted.size - 1)
        val low = rank.toInt()
        val high = minOf(low + 1, sorted.lastIndex)
        val frac = rank - low
        return sorted[low] + (sorted[high] - sorted[low]) * frac
    }
}

private inline fun measureMs(block: () -> Unit): Double {
    val start = System.currentTimeMillis()
    block()
    return (System.currentTimeMillis() - start).toDouble()
}
