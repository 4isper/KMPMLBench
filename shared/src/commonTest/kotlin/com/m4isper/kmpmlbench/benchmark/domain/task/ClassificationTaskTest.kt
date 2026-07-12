package com.m4isper.kmpmlbench.benchmark.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassificationTaskTest {
    @Test
    fun createInputMatchesDimensions() {
        // createInput loads the bundled sample photo at its native size, so the
        // BenchmarkInput must stay consistent with the loaded pixel buffer.
        val task = ClassificationTask(inputWidth = 64, inputHeight = 48, numClasses = 5)
        val input = task.createInput()
        assertEquals(input.image.width, input.width)
        assertEquals(input.image.height, input.height)
        assertTrue(input.width > 0)
        assertTrue(input.height > 0)
    }

    @Test
    fun createInputCarriesSampleLabel() {
        val task = ClassificationTask(numClasses = 10)
        val input = task.createInput()
        assertEquals(task.sampleLabel, input.label)
        assertTrue(input.label.isNotEmpty())
    }

    @Test
    fun idsAndDisplayNamesAreStable() {
        assertEquals("classification", ClassificationTask().id)
        assertTrue(ClassificationTask().displayName.startsWith("Classification"))
        assertEquals(10, ClassificationTask().classNames.size)
    }
}
