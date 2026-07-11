package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider

/**
 * Resolves the available [com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine]s
 * for a task. Declared as `expect` so each platform can register the engines it
 * supports: Desktop (JVM) wires the real ONNX Runtime engine, while Android/iOS
 * currently fall back to the mock.
 */
expect object EngineCatalog : EngineProvider
