package com.m4isper.kmpmlbench.benchmark.data.engine

import ai.onnxruntime.OrtSession

/**
 * Enables the requested ONNX execution provider on [options] for the current
 * platform. CoreML (Apple Neural Engine / GPU) and NNAPI (Android) are
 * platform-specific methods on [OrtSession.SessionOptions]; they are invoked
 * reflectively so the shared engine code compiles for every target and falls
 * back to the CPU provider when an accelerator is unavailable on the device.
 */
internal fun configureProvider(options: OrtSession.SessionOptions, provider: String) {
    if (provider == "cpu") return
    val methodName = when (provider) {
        "coreml" -> "addCoreML"
        "nnapi" -> "addNnapi"
        else -> return
    }
    try {
        OrtSession.SessionOptions::class.java.getMethod(methodName).invoke(options)
    } catch (_: Throwable) {
        // Accelerator not available on this platform; run on CPU.
    }
}
