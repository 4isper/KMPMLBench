package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Input handed to an engine for one inference. For Super-Resolution this is the
 * low-resolution source frame together with its pixel buffer. `prompt` carries
 * the text input for language tasks (null for image-only workloads).
 */
data class BenchmarkInput(
    val width: Int,
    val height: Int,
    val label: String,
    val image: ImageBuffer,
    val prompt: String? = null,
    /** True when the image was supplied by the user (no ground-truth available). */
    val isCustom: Boolean = false,
)
