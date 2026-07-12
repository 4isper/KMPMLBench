package com.m4isper.kmpmlbench.benchmark.domain.platform

/**
 * Opens a native file picker restricted to images and returns the selected
 * file's path (or a content URI string on Android), or `null` if the user
 * cancels. The UI calls this so the user can benchmark their own photo
 * instead of the bundled sample.
 */
expect suspend fun pickImage(): String?

/**
 * Opens a native file picker restricted to the given [extensions] (e.g.
 * `["onnx"]` or `["txt"]`) and returns the selected file's path (or a content
 * URI string on Android), or `null` if the user cancels. Used to let the user
 * supply their own `.onnx` model or labels file.
 */
expect suspend fun pickFile(extensions: List<String>): String?

/**
 * Opens a native directory picker and returns the selected directory's path, or
 * `null` if the user cancels. Used to let the user supply an ONNX Runtime GenAI
 * model *folder* (which bundles `model.onnx` + `genai_config.json` + tokenizer),
 * since that engine loads a directory rather than a single file.
 */
expect suspend fun pickDirectory(): String?

/**
 * Whether a real (non-mock) LLM engine is available on the current platform.
 * False where only the mock is registered (Desktop/JVM, iOS); true where the
 * ORT GenAI engine runs on-device (Android).
 */
expect val realLlmEngineSupported: Boolean
