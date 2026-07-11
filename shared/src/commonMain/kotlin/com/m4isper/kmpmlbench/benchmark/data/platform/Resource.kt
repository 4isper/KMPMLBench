package com.m4isper.kmpmlbench.benchmark.data.platform

/**
 * Loads a bundled model/label resource as raw bytes.
 *
 * The Desktop/JVM build reads from the classpath (`jvmMain/resources`), while
 * the Android build reads from the app's packed `assets/` directory. Each
 * platform provides its own [actual] so the ONNX engines stay resource-agnostic.
 */
expect fun loadModelBytes(path: String): ByteArray
