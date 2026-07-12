package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check of the real ONNX Runtime object-detection engine on iOS
 * (via the Objective-C `onnxruntime-objc` API). Asserts that a real YOLOv8n
 * inference on the bundled frame finds the single `person` ground-truth box
 * and scores mAP > 0.9 — mirroring the JVM [OnnxObjectDetectionEngineTest].
 */
class OnnxObjectDetectionEngineIosTest {

    @Test
    fun runsRealInferenceAndScoresPersonDetection() {
        val task = ObjectDetectionTask(inputWidth = 640, inputHeight = 640)
        val engine = OnnxObjectDetectionEngineIos(task)

        engine.initialize()
        val output = engine.infer(task.createInput())
        engine.close()

        assertEquals(640, output.width)
        assertEquals(640, output.height)

        val quality = output.quality as DetectionQualityMetrics
        assertEquals(1, quality.numDetections, "YOLOv8n should detect exactly one person")
        assertTrue(
            quality.meanConfidence in 0.6..0.8,
            "expected person confidence ~0.71, got ${quality.meanConfidence}",
        )
        assertTrue(
            quality.mAP > 0.9,
            "expected near-perfect mAP, got ${quality.mAP}",
        )
    }
}
