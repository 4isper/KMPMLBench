package com.m4isper.kmpmlbench.benchmark.data.platform

actual fun loadModelBytes(path: String): ByteArray =
    checkNotNull(
        Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ClassLoader.getSystemResourceAsStream(path),
    ) { "Resource not found on classpath: $path" }.use { it.readBytes() }
