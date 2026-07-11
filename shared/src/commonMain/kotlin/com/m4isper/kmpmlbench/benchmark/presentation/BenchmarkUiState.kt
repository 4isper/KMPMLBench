package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

/** Presentation models — the view never sees domain entities directly. */

data class EngineItem(
    val id: String,
    val name: String,
)

data class BenchmarkResultUi(
    val engineName: String,
    val taskName: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val inputImage: ImageBuffer,
    val outputImage: ImageBuffer,
    val psnr: Double,
    val ssim: Double,
    val initTimeMs: Double,
    val avgLatencyMs: Double,
    val minLatencyMs: Double,
    val maxLatencyMs: Double,
    val p50LatencyMs: Double,
    val p95LatencyMs: Double,
    val throughputFps: Double,
    val peakMemoryMb: Double,
    val iterations: Int,
)

data class BenchmarkUiState(
    val scale: Int = 2,
    val inputSize: Int = 128,
    val iterations: Int = 50,
    val engines: List<EngineItem> = emptyList(),
    val selectedEngineId: String? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val result: BenchmarkResultUi? = null,
)
