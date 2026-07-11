package com.m4isper.kmpmlbench.benchmark.engine

import com.m4isper.kmpmlbench.benchmark.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.model.BenchmarkOutput

/**
 * Abstraction over an ML inference engine (ONNX Runtime, TFLite, NCNN, ...).
 * The benchmark harness only depends on this contract, so a real engine can be
 * dropped in later without touching the runner or the UI.
 */
interface MlEngine {
    /** Stable identifier, e.g. "onnx-runtime", "mock-sr". */
    val id: String

    /** Human-readable name shown in the UI. */
    val displayName: String

    /** Load model weights / allocate tensors. Timed by the runner. */
    fun initialize()

    /** Run a single inference for the given input. Returns the produced output. */
    fun infer(input: BenchmarkInput): BenchmarkOutput

    /** Release native/model resources. */
    fun close()
}
