package com.m4isper.kmpmlbench.benchmark.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmTaskTest {
    @Test
    fun idsAndDisplayNamesAreStable() {
        assertEquals("llm", LlmTask().id)
        assertTrue(LlmTask().displayName.startsWith("On-device LLM"))
    }

    @Test
    fun createInputCarriesPromptAndDummyImage() {
        val task = LlmTask(prompt = "Explain coroutines")
        val input = task.createInput()

        assertEquals(1, input.width)
        assertEquals(1, input.height)
        assertEquals("Explain coroutines", input.label)
        assertEquals("Explain coroutines", input.prompt)
        assertEquals(1, input.image.width)
        assertEquals(1, input.image.height)
    }

    @Test
    fun defaultPromptIsMeaningful() {
        assertTrue(LlmTask().prompt.isNotBlank())
    }
}
