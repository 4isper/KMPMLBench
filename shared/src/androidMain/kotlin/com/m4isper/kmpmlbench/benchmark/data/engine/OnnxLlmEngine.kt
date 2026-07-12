package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.genai.Generator
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Sequences
import ai.onnxruntime.genai.Tokenizer
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.LlmQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import kotlin.time.TimeSource

/**
 * Real on-device LLM engine backed by ONNX Runtime GenAI (Android only).
 *
 * Loads a Gemma-2B / Phi-2 (or compatible) model exported to the ORT GenAI
 * format — a folder containing `model.onnx`, `genai_config.json` and the
 * tokenizer — supplied by the user out-of-band (exactly like the classification
 * `.tflite`). The harness feeds the prompt, decodes token-by-token, and reports
 * genuine throughput (tokens/sec) and first-token latency measured from the
 * wall clock, replacing the [MockLlmEngine] placeholders.
 *
 * ORT GenAI has no published JVM dependency, so this engine is Android-only;
 * the Desktop target keeps the mock. The engine registers only when a model
 * folder is provided, so the UI falls back to the mock without a multi-GB
 * download.
 */
class OnnxLlmEngine(
    private val task: LlmTask,
    /** Directory of the ORT GenAI model folder; required (no bundled LLM). */
    val customModelPath: String? = null,
) : MlEngine {
    override val id: String = "onnx-llm"
    override val displayName: String = "ONNX Runtime GenAI (Gemma/Phi)"

    private var model: Model? = null
    private var tokenizer: Tokenizer? = null

    override fun initialize() {
        val modelDir = checkNotNull(customModelPath) {
            "No LLM model folder provided — use 'Load model folder' to select a Gemma/Phi ORT GenAI model."
        }
        model = Model(modelDir)
        tokenizer = Tokenizer(model!!)
    }

    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        val m = checkNotNull(model) { "ONNX LLM engine not initialized" }
        val tk = checkNotNull(tokenizer) { "ONNX LLM engine not initialized" }
        val prompt = input.prompt ?: task.prompt

        val promptSeq: Sequences = tk.encode(prompt)
        val promptTokens = promptSeq.getSequence(0).size
        val params = GeneratorParams(m)
        params.setSearchOption("max_length", (promptTokens + task.maxNewTokens).toDouble())

        val generator = Generator(m, params)
        try {
            generator.appendTokenSequences(promptSeq)

            val decodeStart = TimeSource.Monotonic.markNow()
            var firstTokenLatencyMs: Long? = null
            val sb = StringBuilder()
            while (!generator.isDone) {
                val tokenStart = TimeSource.Monotonic.markNow()
                generator.generateNextToken()
                if (firstTokenLatencyMs == null) {
                    firstTokenLatencyMs = tokenStart.elapsedNow().inWholeMilliseconds
                }
                val seq = generator.getSequence(0)
                if (seq.isNotEmpty()) {
                    sb.append(tk.decode(intArrayOf(seq[seq.lastIndex])))
                }
            }

            val totalDecodeMs = decodeStart.elapsedNow().inWholeMilliseconds
            val finalSeq = generator.getSequence(0)
            val completionTokens = (finalSeq.size - promptTokens).coerceAtLeast(0)
            val tokensPerSecond = if (totalDecodeMs > 0) completionTokens / (totalDecodeMs / 1000.0) else 0.0

            return BenchmarkOutput(
                width = 1,
                height = 1,
                image = ImageBuffer(1, 1, intArrayOf(0xFF101010.toInt())),
                quality = LlmQualityMetrics(
                    generatedText = sb.toString().trim(),
                    tokensPerSecond = tokensPerSecond,
                    firstTokenLatencyMs = firstTokenLatencyMs,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                ),
            )
        } finally {
            generator.close()
            promptSeq.close()
        }
    }

    override fun close() {
        tokenizer?.close()
        tokenizer = null
        model?.close()
        model = null
    }
}
