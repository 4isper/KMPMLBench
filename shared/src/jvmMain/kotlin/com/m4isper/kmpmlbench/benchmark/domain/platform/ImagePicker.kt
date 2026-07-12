package com.m4isper.kmpmlbench.benchmark.domain.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop file picker backed by Swing's [JFileChooser], opened on the EDT and
 * resolved on an IO dispatcher so it never blocks the calling coroutine.
 */
actual suspend fun pickImage(): String? = withContext(Dispatchers.IO) {
    var path: String? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select an image"
            fileFilter = FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "bmp", "gif")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            path = chooser.selectedFile?.takeIf { it.exists() }?.absolutePath
        }
    }
    path
}

actual suspend fun pickFile(extensions: List<String>): String? = withContext(Dispatchers.IO) {
    var path: String? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select a file"
            fileFilter = FileNameExtensionFilter(extensions.joinToString(", ").uppercase(), *extensions.toTypedArray())
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            path = chooser.selectedFile?.takeIf { it.exists() }?.absolutePath
        }
    }
    path
}

actual suspend fun pickDirectory(): String? = withContext(Dispatchers.IO) {
    var path: String? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select a model folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            path = chooser.selectedFile?.takeIf { it.exists() && it.isDirectory }?.absolutePath
        }
    }
    path
}

actual val realLlmEngineSupported: Boolean = false
