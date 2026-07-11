package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

/** Presentation models — the view never sees domain entities directly. */

data class EngineItem(
    val id: String,
    val name: String,
)

/** Task-agnostic quality for the UI, mirroring the domain's sealed [QualityMetrics]. */
sealed interface QualityUi
data class SrQualityUi(val psnr: Double, val ssim: Double) : QualityUi
data class ClassificationQualityUi(
    val predictedClass: String,
    val confidence: Double,
    val topK: List<Pair<String, Double>>,
    val accuracy: Double,
) : QualityUi

data class BenchmarkResultUi(
    val engineName: String,
    val taskName: String,
    val taskId: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val inputImage: ImageBuffer,
    /** The reconstructed/upscaled frame; null when the task has no output image (e.g. classification reuses the input). */
    val outputImage: ImageBuffer?,
    val quality: QualityUi,
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
    val selectedTaskId: String = "super-resolution",
    val scale: Int = 2,
    val inputSize: Int = 128,
    val iterations: Int = 50,
    val engines: List<EngineItem> = emptyList(),
    val selectedEngineId: String? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val result: BenchmarkResultUi? = null,
    /** Results of a multi-engine comparison run, or null if not compared yet. */
    val comparison: List<BenchmarkResultUi>? = null,
    val comparisonProgress: Float = 0f,
)
