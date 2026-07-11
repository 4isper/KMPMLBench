package com.m4isper.kmpmlbench.benchmark.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals

class SuperResolutionTaskTest {
    @Test
    fun createInputUsesDimensions() {
        val task = SuperResolutionTask(scale = 4, inputWidth = 32, inputHeight = 24)
        val input = task.createInput()
        assertEquals(32, input.width)
        assertEquals(24, input.height)
        assertEquals("Input 32×24", input.label)
    }

    @Test
    fun outputDimensionsScaleByFactor() {
        val task = SuperResolutionTask(scale = 3, inputWidth = 50, inputHeight = 40)
        assertEquals(150, task.outputWidth)
        assertEquals(120, task.outputHeight)
    }

    @Test
    fun displayNameCarriesScale() {
        assertEquals("Super-Resolution ×2", SuperResolutionTask(2, 8, 8).displayName)
    }
}
