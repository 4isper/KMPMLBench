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
    fun superResolutionReturnsMockEngine() {
        val engines = EngineCatalog.enginesFor(SuperResolutionTask(2, 8, 8))
        assertEquals(1, engines.size)
        assertEquals("mock-sr", engines[0].id)
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
