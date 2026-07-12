package com.m4isper.kmpmlbench.benchmark.data.platform

/**
 * iOS image picker.
 *
 * NOTE: iOS is currently out of scope (not built/tested yet), so this is a
 * safe no-op that resumes with `null` without presenting a native picker.
 * Replace with a `UIDocumentPickerViewController`-backed implementation once
 * the iOS source set is compiled and exercised in tests.
 */
actual suspend fun pickImage(): String? {
    return null
}

actual suspend fun pickFile(extensions: List<String>): String? {
    return null
}

actual suspend fun pickDirectory(): String? {
    return null
}

actual val realLlmEngineSupported: Boolean = false
