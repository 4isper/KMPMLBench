package com.m4isper.kmpmlbench.benchmark.data.platform

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer

/**
 * Loads a bundled model/label resource as raw bytes.
 *
 * The Desktop/JVM build reads from the classpath (`jvmMain/resources`), while
 * the Android build reads from the app's packed `assets/` directory. Each
 * platform provides its own [actual] so the ONNX engines stay resource-agnostic.
 */
expect fun loadModelBytes(path: String): ByteArray

/**
 * Loads a bundled image resource and decodes it into an ARGB [ImageBuffer].
 *
 * Used by the classification task to feed a real photo (instead of a synthetic
 * frame) so accuracy is measured against a genuine prediction. Decoding is
 * platform-specific: BitmapFactory on Android, ImageIO on the JVM.
 */
expect fun loadImageBuffer(path: String): ImageBuffer
