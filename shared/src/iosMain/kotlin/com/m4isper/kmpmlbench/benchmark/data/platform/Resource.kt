package com.m4isper.kmpmlbench.benchmark.data.platform

import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.generateSyntheticImage

actual fun loadModelBytes(path: String): ByteArray {
    throw UnsupportedOperationException("ONNX resources are not hosted on iOS (mock engines only)")
}

actual fun loadImageBuffer(path: String): ImageBuffer {
    // iOS runs mock-only engines, so a real photo is unnecessary there.
    return generateSyntheticImage(224, 224)
}
