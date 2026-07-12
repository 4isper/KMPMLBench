package com.m4isper.kmpmlbench.benchmark.domain.platform

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

/**
 * Decodes an arbitrary user-selected image **file** (not a bundled resource)
 * into an ARGB [ImageBuffer]. Used when the UI lets the user benchmark their
 * own photo. Decoding is platform-specific: BitmapFactory on Android, ImageIO
 * on the JVM, UIKit on iOS.
 */
expect fun loadImageFile(path: String): ImageBuffer

/**
 * Loads an arbitrary user-selected model/label **file** (not a bundled
 * resource) as raw bytes. Used when the UI lets the user benchmark their own
 * `.onnx` model or labels file. Decoding is platform-specific: a plain file
 * read on the JVM/Android, `fopen` on iOS.
 */
expect fun loadModelFile(path: String): ByteArray
