package com.m4isper.kmpmlbench.benchmark.data.platform

/**
 * Opens a native file picker restricted to images and returns the selected
 * file's path (or a content URI string on Android), or `null` if the user
 * cancels. The UI calls this so the user can benchmark their own photo
 * instead of the bundled sample.
 */
expect suspend fun pickImage(): String?
