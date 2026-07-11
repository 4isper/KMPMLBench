package com.m4isper.kmpmlbench.benchmark.domain.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput

/**
 * Port (abstraction) over an ML inference engine (ONNX Runtime, TFLite, NCNN, ...).
 * The benchmark use case depends only on this contract, so a real engine can be
 * dropped in later without touching the runner or the UI. Implementations live
 * in the `data` layer.
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
