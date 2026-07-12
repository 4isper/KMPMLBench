package com.m4isper.kmpmlbench.benchmark.domain.task

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

/**
 * On-device LLM: generate a continuation for a text prompt using a small
 * language model (e.g. Gemma-2B / Phi-2) running via ExecuTorch.
 *
 * The domain is image-centric, so the task still carries a (dummy) pixel buffer
 * to satisfy [BenchmarkInput], but the real signal is [prompt] and the engine's
 * generated text. The dummy image is hidden by the UI, mirroring how
 * Classification reuses its input frame.
 */
data class LlmTask(
    val modelName: String = "mock-llm",
    val prompt: String = "What is Kotlin Multiplatform?",
    val maxNewTokens: Int = 48,
) : BenchmarkTask {
    override val id: String get() = "llm"
    override val displayName: String get() = "On-device LLM ($modelName)"

    override fun createInput(): BenchmarkInput {
        // 1×1 placeholder; the LLM engine ignores the pixels and reads `prompt`.
        val dummy = ImageBuffer(1, 1, intArrayOf(0xFF101010.toInt()))
        return BenchmarkInput(1, 1, prompt, dummy, prompt = prompt)
    }
}
