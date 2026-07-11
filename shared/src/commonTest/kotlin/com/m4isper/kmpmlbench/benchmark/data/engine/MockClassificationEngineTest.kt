package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockClassificationEngineTest {
    @Test
    fun inferReturnsClassificationQuality() {
        val task = ClassificationTask(inputWidth = 16, inputHeight = 16, numClasses = 10)
        val engine = MockClassificationEngine(task)
        val input = task.createInput()

        val output = engine.infer(input)
        val q = output.quality as ClassificationQualityMetrics

        assertEquals(input.width, output.width)
        assertEquals(input.height, output.height)
        assertTrue(q.predictedClass in task.classNames)
        assertTrue(q.confidence > 0.0 && q.confidence <= 1.0)
        assertEquals(5, q.topK.size)
        // Probabilities are sorted in descending order.
        for (i in 1 until q.topK.size) {
            assertTrue(q.topK[i - 1].second >= q.topK[i].second)
        }
        // Accuracy is binary: prediction either matches the labeled class or not.
        assertTrue(q.accuracy == 0.0 || q.accuracy == 1.0)
        assertEquals(q.accuracy, if (q.predictedClass == input.label) 1.0 else 0.0)
    }

    @Test
    fun initializeAndCloseAreSafe() {
        val task = ClassificationTask()
        val engine = MockClassificationEngine(task)
        engine.initialize()
        engine.infer(task.createInput())
        engine.close()
    }

    @Test
    fun topKNeverExceedsClassCount() {
        val task = ClassificationTask(numClasses = 3)
        val output = MockClassificationEngine(task).infer(task.createInput())
        val q = output.quality as ClassificationQualityMetrics
        assertEquals(3, q.topK.size)
    }

    private fun blackInput(w: Int, h: Int) =
        BenchmarkInput(w, h, "class-0", ImageBuffer(w, h, IntArray(w * h) { 0xFF000000.toInt() }))
}
