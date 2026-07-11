package com.m4isper.kmpmlbench.benchmark.data.platform

import kotlin.test.Test
import kotlin.test.assertTrue

/** The platform memory sampler must report a finite, non-negative value. */
class MemorySamplerTest {
    @Test
    fun reportsFiniteNonNegativeMemory() {
        val mb = currentMemoryUsageMb()
        assertTrue(mb.isFinite(), "memory must be finite")
        assertTrue(mb >= 0.0, "memory must not be negative")
    }
}
