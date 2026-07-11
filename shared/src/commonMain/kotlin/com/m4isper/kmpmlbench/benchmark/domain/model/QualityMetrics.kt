package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Task-agnostic quality result produced by an engine. Sealed so each workload
 * can carry the metrics that are meaningful for it (PSNR/SSIM for
 * Super-Resolution, predicted class/confidence for Classification) without
 * coupling the domain model to a specific task.
 */
sealed interface QualityMetrics

/** Reconstruction fidelity for Super-Resolution (compared to ground truth). */
data class SrQualityMetrics(
    val psnr: Double,
    val ssim: Double,
) : QualityMetrics

/** Predicted class and confidence for Classification. */
data class ClassificationQualityMetrics(
    val predictedClass: String,
    val confidence: Double,
    val topK: List<Pair<String, Double>>,
    /** 1.0 when the predicted class matches the input's expected label, else 0.0. */
    val accuracy: Double,
) : QualityMetrics
