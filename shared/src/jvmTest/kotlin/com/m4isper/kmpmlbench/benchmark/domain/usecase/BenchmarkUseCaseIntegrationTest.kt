package com.m4isper.kmpmlbench.benchmark.domain.usecase

import com.m4isper.kmpmlbench.benchmark.data.engine.EngineCatalog
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end benchmark test: drives each task's real ONNX engine through the
 * [BenchmarkRunner] use case (cold start, warm-up, measured loop, metrics
 * aggregation) instead of calling engines directly. This proves the catalog,
 * task, engine, and quality pipeline hang together with meaningful results,
 * where the per-task engine tests only exercise a single engine in isolation.
 *
 * Real ONNX engines are Desktop/JVM-only, so this lives in jvmTest; on iOS the
 * catalog is mock-only and would not satisfy the meaningful-accuracy asserts.
 */
class BenchmarkUseCaseIntegrationTest {

    @Test
    fun runsRealEnginesThroughUseCaseWithMeaningfulQuality() = runBlocking {
        val tasks = listOf(
            SuperResolutionTask(scale = 2, inputWidth = 32, inputHeight = 32),
            ClassificationTask(),
            ObjectDetectionTask(inputWidth = 640, inputHeight = 640),
        )
        val runner = BenchmarkRunner()

        for (task in tasks) {
            // The catalog must put a real ONNX engine ahead of the mock.
            val engines = EngineCatalog.enginesFor(task)
            assertTrue(
                engines.first().id.startsWith("onnx-"),
                "expected a real ONNX engine first for ${task.id}, got ${engines.first().id}",
            )
            val engine = engines.first()

            val result = runner.run(engine, task, iterations = 3, warmup = 1)

            // The runner reports the engine it ran and sane timing metrics.
            assertEquals(engine.id, result.engineId)
            assertEquals(3, result.metrics.iterations)
            assertTrue(result.metrics.initTimeMs >= 0.0, "init time non-negative")
            assertTrue(result.metrics.avgLatencyMs > 0.0, "avg latency positive")
            assertTrue(result.metrics.throughputFps > 0.0, "throughput positive")
            assertTrue(result.metrics.peakMemoryMb >= 0.0, "peak memory non-negative")

            // Each task yields the quality metric type and value that is
            // meaningful for it.
            when (val q = result.quality) {
                is SrQualityMetrics -> {
                    assertTrue(q.psnr.isFinite() && q.psnr > 0.0, "psnr positive")
                    assertTrue(q.psnr <= 100.0, "psnr capped")
                    assertTrue(q.ssim > 0.0 && q.ssim <= 1.0, "ssim in (0,1]")
                }
                is ClassificationQualityMetrics -> {
                    assertTrue(q.predictedClass.isNotEmpty(), "predicted class named")
                    assertTrue(q.confidence > 0.0 && q.confidence <= 1.0, "confidence in (0,1]")
                    assertEquals(5, q.topK.size, "top-5 reported")
                    assertEquals(
                        1.0,
                        q.accuracy,
                        "real MobileNetV2 must rank 'military uniform' in top-5 for the sample photo",
                    )
                }
                is DetectionQualityMetrics -> {
                    assertEquals(1, q.numDetections, "single correct person detection")
                    assertTrue(q.meanConfidence in 0.6..0.8, "person conf ~0.71, got ${q.meanConfidence}")
                    assertTrue(q.mAP > 0.9, "real person detection mAP ~1.0, got ${q.mAP}")
                }
                else -> error("unexpected quality type ${q::class} for ${task.id}")
            }
        }
    }
}
