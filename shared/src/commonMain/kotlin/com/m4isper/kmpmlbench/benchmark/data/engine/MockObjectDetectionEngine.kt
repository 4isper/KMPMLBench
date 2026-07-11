package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.Box
import com.m4isper.kmpmlbench.benchmark.domain.model.Detection
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.processing.drawBoxes
import com.m4isper.kmpmlbench.benchmark.domain.processing.evaluateDetections
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask

/**
 * Stand-in engine (data-layer adapter) for the Object-Detection task. It
 * performs a real but lightweight inference: deterministic pseudo-detections are
 * derived from the task's ground-truth boxes (with small jitter) so the
 * detection quality (mAP@0.5) is meaningful and reproducible. The boxes are also
 * drawn onto the output frame so the preview mirrors a real detector. This
 * exercises the full harness (timing, percentiles, throughput, quality) before
 * any native ML dependency is wired in.
 */
class MockObjectDetectionEngine(
    private val task: ObjectDetectionTask,
) : MlEngine {
    override val id: String = "mock-od"
    override val displayName: String = "Mock Detection Engine"

    override fun initialize() {
        // No weights to load for the mock; nothing to do.
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val gt = task.groundTruthBoxes()
        val detections = gt.mapIndexed { i, g ->
            val jitter = if (i % 2 == 0) 0.01f else -0.01f
            Detection(
                box = Box(
                    (g.box.x + jitter).coerceIn(0f, 1f),
                    (g.box.y + jitter).coerceIn(0f, 1f),
                    g.box.w,
                    g.box.h,
                ),
                label = g.label,
                confidence = (0.7 + (i * 0.05).coerceAtMost(0.29)).coerceIn(0.0, 1.0),
            )
        } + Detection(
            box = Box(0.05f, 0.05f, 0.06f, 0.06f),
            label = task.classNames[(gt.size * 17) % task.numClasses],
            confidence = 0.2,
        )

        val mAP = evaluateDetections(detections, gt)
        val meanConfidence = detections.map { it.confidence }.average()
        val quality = DetectionQualityMetrics(
            numDetections = detections.size,
            meanConfidence = meanConfidence,
            mAP = mAP,
        )
        val outImg = drawBoxes(input.image, detections)
        return BenchmarkOutput(input.width, input.height, outImg, quality)
    }

    override fun close() {
        // Nothing to release for the mock.
    }
}
