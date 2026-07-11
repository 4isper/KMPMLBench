package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals

class MockSuperResolutionEngineTest {
    @Test
    fun inferReturnsUpscaledDimensions() {
        val task = SuperResolutionTask(scale = 4, inputWidth = 16, inputHeight = 16)
        val engine = MockSuperResolutionEngine(task)

        val output = engine.infer(BenchmarkInput(16, 16, "x"))

        assertEquals(64, output.width)
        assertEquals(64, output.height)
    }

    @Test
    fun initializeAndCloseAreSafe() {
        val engine = MockSuperResolutionEngine(SuperResolutionTask(2, 8, 8))
        engine.initialize()
        engine.infer(BenchmarkInput(8, 8, "x"))
        engine.close()
    }
}
