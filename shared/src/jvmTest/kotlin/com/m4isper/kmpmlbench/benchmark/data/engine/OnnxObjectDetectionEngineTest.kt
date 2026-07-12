package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the real ONNX object-detection engine. Loads the bundled
 * YOLOv8n model and COCO labels and runs actual inference on the JVM.
 */
class OnnxObjectDetectionEngineTest {
    @Test
    fun runsRealInferenceAndScoresDetection() {
        val task = ObjectDetectionTask(inputWidth = 640, inputHeight = 640)
        val engine = OnnxObjectDetectionEngine(task)
        val input = task.createInput()

        engine.initialize()
        val output = engine.infer(input)
        engine.close()

        // The engine returns the model-input-sized (640×640) frame with boxes drawn.
        assertEquals(640, output.width)
        assertEquals(640, output.height)

        val q = output.quality as DetectionQualityMetrics
        // The real engine must surface exactly the single correct `person`
        // detection on the bundled sample and score ~1.0 mAP against the GT box.
        assertEquals(1, q.numDetections)
        assertTrue(q.meanConfidence in 0.6..0.8, "expected the person detection at ~0.71, got ${q.meanConfidence}")
        assertTrue(q.mAP > 0.9, "real person detection should score ~1.0 mAP, got ${q.mAP}")
    }
}
