package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Desktop (JVM) registry must offer the real ONNX engine ahead of the mock. */
class EngineCatalogJvmTest {
    @Test
    fun desktopOffersOnnxEngineFirst() {
        val engines = EngineCatalog.enginesFor(SuperResolutionTask(2, 8, 8))
        assertEquals("onnx-sr", engines.first().id)
        assertTrue(engines.any { it.id == "mock-sr" })
    }
}
