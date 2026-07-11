package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BenchmarkResultMapperTest {
    @Test
    fun mapsSrDomainResultToUi() {
        val result = BenchmarkResult(
            engineId = "mock-sr",
            engineName = "Mock SR Engine",
            task = SuperResolutionTask(2, 16, 16),
            input = BenchmarkInput(16, 16, "LR", ImageBuffer(16, 16, IntArray(256))),
            output = BenchmarkOutput(
                32,
                32,
                ImageBuffer(32, 32, IntArray(1024)),
                SrQualityMetrics(30.0, 0.9),
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
            quality = SrQualityMetrics(30.0, 0.9),
        )

        val ui = result.toUi()

        assertEquals("Mock SR Engine", ui.engineName)
        assertEquals("Super-Resolution ×2", ui.taskName)
        assertEquals("super-resolution", ui.taskId)
        assertEquals(32, ui.outputWidth)
        assertEquals(32, ui.outputHeight)
        assertEquals(16, ui.inputImage.width)
        assertEquals(32, ui.outputImage?.width)
        assertTrue(ui.quality is SrQualityUi)
        assertEquals(30.0, ui.quality.psnr, 0.0)
        assertEquals(0.9, ui.quality.ssim, 0.0)
        assertEquals(2.5, ui.avgLatencyMs, 0.0)
        assertEquals(400.0, ui.throughputFps, 0.0)
        assertEquals(50.0, ui.peakMemoryMb, 0.0)
        assertEquals(10, ui.iterations)
    }

    @Test
    fun mapsClassificationResultWithoutOutputImage() {
        val result = BenchmarkResult(
            engineId = "mock-cls",
            engineName = "Mock Classification Engine",
            task = ClassificationTask(),
            input = BenchmarkInput(8, 8, "class-3", ImageBuffer(8, 8, IntArray(64))),
            output = BenchmarkOutput(
                8,
                8,
                ImageBuffer(8, 8, IntArray(64)),
                ClassificationQualityMetrics(
                    predictedClass = "class-3",
                    confidence = 0.42,
                    topK = listOf("class-3" to 0.42, "class-1" to 0.18),
                    accuracy = 1.0,
                ),
            ),
            metrics = BenchmarkMetrics(
                initTimeMs = 1.0,
                warmupMs = null,
                iterations = 5,
                avgLatencyMs = 1.0,
                minLatencyMs = 0.5,
                maxLatencyMs = 1.5,
                p50LatencyMs = 1.0,
                p95LatencyMs = 1.4,
                throughputFps = 1000.0,
                peakMemoryMb = 10.0,
            ),
            quality = ClassificationQualityMetrics(
                predictedClass = "class-3",
                confidence = 0.42,
                topK = listOf("class-3" to 0.42, "class-1" to 0.18),
                accuracy = 1.0,
            ),
        )

        val ui = result.toUi()

        assertEquals("classification", ui.taskId)
        // Classification reuses the input as its image, so no separate output frame.
        assertNull(ui.outputImage)
        assertTrue(ui.quality is ClassificationQualityUi)
        val q = ui.quality
        assertEquals("class-3", q.predictedClass)
        assertEquals(0.42, q.confidence, 0.0)
        assertEquals(1.0, q.accuracy, 0.0)
        assertEquals(2, q.topK.size)
    }
}
