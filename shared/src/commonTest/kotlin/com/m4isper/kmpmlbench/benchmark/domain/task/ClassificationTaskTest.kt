package com.m4isper.kmpmlbench.benchmark.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassificationTaskTest {
    @Test
    fun createInputMatchesDimensions() {
        val task = ClassificationTask(inputWidth = 64, inputHeight = 48, numClasses = 5)
        val input = task.createInput()
        assertEquals(64, input.width)
        assertEquals(48, input.height)
        assertEquals(64, input.image.width)
        assertEquals(48, input.image.height)
    }

    @Test
    fun expectedLabelBelongsToClassVocabulary() {
        val task = ClassificationTask(numClasses = 10)
        val input = task.createInput()
        assertTrue(input.label in task.classNames, "label ${input.label} not in ${task.classNames}")
    }

    @Test
    fun idsAndDisplayNamesAreStable() {
        assertEquals("classification", ClassificationTask().id)
        assertTrue(ClassificationTask().displayName.startsWith("Classification"))
        assertEquals(10, ClassificationTask().classNames.size)
    }
}
