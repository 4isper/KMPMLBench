package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the real ONNX classification engine. Loads the bundled
 * MobileNetV2 model and ImageNet labels and runs actual inference on the JVM.
 */
class OnnxClassificationEngineTest {
    @Test
    fun runsRealInferenceAndScoresClassification() {
        // Real bundled photo + its ground-truth ImageNet label, so the
        // score is meaningful (top-1 vs GT) instead of always 0.
        val task = ClassificationTask()
        val engine = OnnxClassificationEngine(task)
        val input = task.createInput()

        engine.initialize()
        val output = engine.infer(input)
        engine.close()

        assertEquals(input.width, output.width)
        assertEquals(input.height, output.height)

        val q = output.quality as ClassificationQualityMetrics
        assertEquals(task.sampleLabel, q.predictedClass, "top-1 must match the photo's ground-truth class")
        assertEquals(1.0, q.accuracy, 0.0, "top-1 must equal the ground-truth label")
        assertEquals(q.predictedClass, q.topK.first().first, "predicted class is the top-1 of topK")
        assertEquals(q.confidence, q.topK.first().second, 0.0, "confidence equals the top-1 probability")
        assertEquals(5, q.topK.size)
        for (i in 1 until q.topK.size) {
            assertTrue(q.topK[i - 1].second >= q.topK[i].second, "topK sorted descending")
        }
    }
}
