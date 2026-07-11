package com.m4isper.kmpmlbench.benchmark.data.platform

actual fun loadModelBytes(path: String): ByteArray {
    throw UnsupportedOperationException("ONNX resources are not hosted on iOS (mock engines only)")
}
