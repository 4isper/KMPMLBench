package com.m4isper.kmpmlbench.benchmark.domain.usecase

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the real [BenchmarkRunner] and checks the peak-memory metric. */
class BenchmarkRunnerMemoryTest {
    private class RecordingEngine : MlEngine {
        override val id = "rec"
        override val displayName = "Recording Engine"
        override fun initialize() {}
        override fun infer(input: BenchmarkInput) = BenchmarkOutput(
            input.width * 2,
            input.height * 2,
            ImageBuffer(input.width * 2, input.height * 2, IntArray((input.width * 2) * (input.height * 2))),
            SrQualityMetrics(30.0, 0.9),
        )
        override fun close() {}
    }

    @Test
    fun recordsPeakMemoryDuringRun() = runBlocking {
        val result = BenchmarkRunner().run(
            engine = RecordingEngine(),
            task = SuperResolutionTask(2, 16, 16),
            iterations = 20,
            warmup = 3,
        ) { _, _ -> }

        val peak = result.metrics.peakMemoryMb
        assertTrue(peak.isFinite(), "peak memory must be finite")
        assertTrue(peak >= 0.0, "peak memory must not be negative")
    }
}
