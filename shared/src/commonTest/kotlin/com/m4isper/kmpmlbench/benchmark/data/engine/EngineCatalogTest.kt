package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
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
    fun classificationRegistersMockEngine() {
        val engines = EngineCatalog.enginesFor(ClassificationTask())
        assertTrue(engines.any { it.id == "mock-cls" }, "mock classification engine must be registered")
    }

    @Test
    fun objectDetectionRegistersMockEngine() {
        val engines = EngineCatalog.enginesFor(ObjectDetectionTask())
        assertTrue(engines.any { it.id == "mock-od" }, "mock detection engine must be registered")
    }

    @Test
    fun llmRegistersMockEngine() {
        val engines = EngineCatalog.enginesFor(LlmTask())
        assertTrue(engines.any { it.id == "mock-llm" }, "mock llm engine must be registered")
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
