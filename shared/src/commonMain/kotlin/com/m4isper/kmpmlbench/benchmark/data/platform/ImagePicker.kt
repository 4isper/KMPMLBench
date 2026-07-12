package com.m4isper.kmpmlbench.benchmark.data.platform

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
