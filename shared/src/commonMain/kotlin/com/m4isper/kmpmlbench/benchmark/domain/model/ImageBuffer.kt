package com.m4isper.kmpmlbench.benchmark.domain.model

/**
 * A plain ARGB pixel buffer, independent of any UI or image-loading framework.
 * The benchmark domain speaks only in [ImageBuffer]s so engines stay portable.
 */
data class ImageBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(pixels.size == width * height) { "pixels size must equal width*height" }
    }
}
