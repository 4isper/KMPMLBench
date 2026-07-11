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

        assertEquals(input.width, output.width)
        assertEquals(input.height, output.height)

        val q = output.quality as DetectionQualityMetrics
        assertTrue(q.numDetections >= 0, "detections non-negative")
        assertTrue(q.meanConfidence in 0.0..1.0, "confidence in [0,1]")
        assertTrue(q.mAP in 0.0..1.0, "mAP in [0,1]")
    }
}
