package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockObjectDetectionEngineTest {
    @Test
    fun producesDetectionMetricsAndDrawsBoxes() {
        val task = ObjectDetectionTask(inputWidth = 320, inputHeight = 320)
        val engine = MockObjectDetectionEngine(task)
        val input = task.createInput()

        engine.initialize()
        val output = engine.infer(input)
        engine.close()

        assertEquals(input.width, output.width)
        assertEquals(input.height, output.height)

        val q = output.quality as DetectionQualityMetrics
        assertTrue(q.numDetections > 0, "should emit detections")
        assertTrue(q.meanConfidence in 0.0..1.0, "confidence in [0,1]")
        assertTrue(q.mAP in 0.0..1.0, "mAP in [0,1]")
        assertTrue(q.mAP > 0.0, "mock should match its own ground truth")
    }
}
