package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineCatalogTest {
    @Test
    fun superResolutionReturnsAtLeastTheMockEngine() {
        val engines = EngineCatalog.enginesFor(SuperResolutionTask(2, 8, 8))
        assertTrue(engines.isNotEmpty())
        // The mock is always registered; real engines may be offered too (e.g. ONNX on Desktop).
        assertTrue(engines.any { it.id == "mock-sr" })
    }

    @Test
    fun unknownTaskReturnsNoEngines() {
        val engines = EngineCatalog.enginesFor(object : BenchmarkTask {
            override val id: String = "fake-task"
            override val displayName: String = "Fake Task"
            override fun createInput(): BenchmarkInput =
                BenchmarkInput(1, 1, "x", ImageBuffer(1, 1, IntArray(1)))
        })
        assertTrue(engines.isEmpty())
    }
}
