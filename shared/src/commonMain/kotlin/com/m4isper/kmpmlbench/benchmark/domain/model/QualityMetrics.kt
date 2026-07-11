package com.m4isper.kmpmlbench.benchmark.domain.model

/** Image-quality metrics of a reconstructed output compared to its ground truth. */
data class QualityMetrics(
    val psnr: Double,
    val ssim: Double,
)
