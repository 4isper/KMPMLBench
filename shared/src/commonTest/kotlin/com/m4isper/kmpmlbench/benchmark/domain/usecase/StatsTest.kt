package com.m4isper.kmpmlbench.benchmark.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {
    @Test
    fun percentileBoundaries() {
        val data = listOf(10.0, 20.0, 30.0, 40.0)
        assertEquals(10.0, Stats.percentile(data, 0.0), 0.0)
        assertEquals(40.0, Stats.percentile(data, 100.0), 0.0)
    }

    @Test
    fun percentileInterpolates() {
        val data = listOf(10.0, 20.0, 30.0, 40.0)
        assertEquals(25.0, Stats.percentile(data, 50.0), 0.0001)
        assertEquals(37.0, Stats.percentile(data, 90.0), 0.0001)
    }

    @Test
    fun emptyListReturnsZero() {
        assertEquals(0.0, Stats.percentile(emptyList(), 50.0), 0.0)
    }
}
