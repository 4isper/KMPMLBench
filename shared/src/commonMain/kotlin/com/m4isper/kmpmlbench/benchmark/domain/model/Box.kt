package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * Axis-aligned bounding box in normalized [0, 1] image coordinates
 * (top-left corner + size).
 */
data class Box(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
) {
    init {
        require(x in 0f..1f && y in 0f..1f && w >= 0f && h >= 0f) { "box out of range" }
    }
}

/** A detected object: its [box], class [label], and [confidence] in [0, 1]. */
data class Detection(
    val box: Box,
    val label: String,
    val confidence: Double,
)
