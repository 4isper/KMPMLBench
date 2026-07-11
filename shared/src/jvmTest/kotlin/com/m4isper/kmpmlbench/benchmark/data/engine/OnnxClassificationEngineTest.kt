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
        val task = ClassificationTask(inputWidth = 64, inputHeight = 64, numClasses = 10)
        val engine = OnnxClassificationEngine(task)
        val input = task.createInput()

        engine.initialize()
        val output = engine.infer(input)
        engine.close()

        assertEquals(input.width, output.width)
        assertEquals(input.height, output.height)

        val q = output.quality as ClassificationQualityMetrics
        assertTrue(q.predictedClass.isNotEmpty(), "predicted class must be named")
        assertTrue(q.confidence > 0.0 && q.confidence <= 1.0, "confidence in (0,1]")
        assertEquals(5, q.topK.size)
        for (i in 1 until q.topK.size) {
            assertTrue(q.topK[i - 1].second >= q.topK[i].second, "topK sorted descending")
        }
    }
}
