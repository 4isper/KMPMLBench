package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.LlmQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockLlmEngineTest {
    @Test
    fun inferReturnsLlmQuality() {
        val task = LlmTask(prompt = "What is Kotlin?")
        val engine = MockLlmEngine(task)
        val input = task.createInput()

        val output = engine.infer(input)
        val q = output.quality as LlmQualityMetrics

        assertEquals(1, output.width)
        assertEquals(1, output.height)
        assertFalse(q.generatedText.isEmpty())
        assertTrue(q.completionTokens > 0, "completion token count must be positive")
        assertTrue(q.promptTokens > 0, "prompt token count must be positive")
        assertTrue(q.tokensPerSecond > 0.0, "decode speed must be positive")
        assertEquals(60L, q.firstTokenLatencyMs)
    }

    @Test
    fun fallsBackToTaskPromptWhenInputPromptIsNull() {
        val task = LlmTask(prompt = "Fallback prompt")
        val engine = MockLlmEngine(task)
        val input = BenchmarkInput(1, 1, "x", ImageBuffer(1, 1, IntArray(1)), prompt = null)

        val output = engine.infer(input)
        val q = output.quality as LlmQualityMetrics

        assertTrue(q.completionTokens > 0)
        assertFalse(q.generatedText.isEmpty())
    }

    @Test
    fun initializeAndCloseAreSafe() {
        val task = LlmTask()
        val engine = MockLlmEngine(task)
        engine.initialize()
        engine.infer(task.createInput())
        engine.close()
    }
}
