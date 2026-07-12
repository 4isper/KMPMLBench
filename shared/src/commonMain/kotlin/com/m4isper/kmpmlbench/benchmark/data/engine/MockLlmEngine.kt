package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.LlmQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Placeholder engine for the on-device LLM benchmark harness.
 *
 * The real backend — ONNX Runtime GenAI running a Gemma-2B / Phi-2 ORT model —
 * is available on Android (see [OnnxLlmEngine]); the Desktop target keeps this
 * mock because ORT GenAI has no published JVM dependency. Until a model folder
 * is supplied (out-of-band, like the classification `.tflite`), this mock
 * simulates token-by-token decoding so the entire UI/metrics pipeline (latency,
 * tokens/sec, first-token latency, prompt/completion token counts) is exercised
 * end-to-end.
 *
 * Decode speed is a fixed placeholder (~40 tok/s) so the harness renders
 * believable numbers; it is intentionally NOT derived from wall-clock and will
 * be replaced by real timings once the native engine lands.
 */
class MockLlmEngine(
    private val task: LlmTask,
) : MlEngine {
    override val id: String = "mock-llm"
    override val displayName: String = "Mock LLM (${task.modelName})"

    private val firstTokenLatencyMs = 60L
    private val decodeMsPerToken = 25L

    override fun initialize() {
        // No native model to load for the mock.
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val prompt = input.prompt ?: task.prompt
        val generatedText = generate(prompt)

        val promptTokens = max(1, estimateTokens(prompt))
        // Mock caps simulated/displayed generation so benchmark runs stay bounded.
        val completionTokens = minOf(max(1, estimateTokens(generatedText)), MAX_SIM_TOKENS)

        simulate(firstTokenLatencyMs)
        simulate(decodeMsPerToken * completionTokens)

        val decodeSeconds = (decodeMsPerToken * completionTokens) / 1000.0
        val tokensPerSecond = if (decodeSeconds > 0.0) completionTokens / decodeSeconds else 0.0

        return BenchmarkOutput(
            width = 1,
            height = 1,
            image = ImageBuffer(1, 1, intArrayOf(0xFF101010.toInt())),
            quality = LlmQualityMetrics(
                generatedText = generatedText,
                tokensPerSecond = tokensPerSecond,
                firstTokenLatencyMs = firstTokenLatencyMs,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            ),
        )
    }

    override fun close() {
        // Nothing to release for the mock.
    }

    private fun generate(prompt: String): String {
        val p = prompt.trim().ifEmpty { "the user" }
        return "$p — Kotlin Multiplatform lets you share business logic across Android, iOS, and desktop from a single Kotlin codebase."
    }

    private fun estimateTokens(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /** Busy-wait to approximate decode latency without a platform-specific sleep. */
    private fun simulate(ms: Long) {
        if (ms <= 0) return
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow().inWholeMilliseconds < ms) {
            spin = spin xor Random.nextLong()
        }
    }

    private var spin: Long = 0L

    private companion object {
        const val MAX_SIM_TOKENS = 32
    }
}
