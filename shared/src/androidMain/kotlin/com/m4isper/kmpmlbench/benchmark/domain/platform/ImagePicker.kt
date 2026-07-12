package com.m4isper.kmpmlbench.benchmark.domain.platform

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentResolver
import android.provider.DocumentsContract
import android.util.ArrayMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File

/**
 * Android image picker: launches `ActivityResultContracts.GetContent` through
 * the running [ComponentActivity]'s result registry and resumes with the
 * selected content URI (or `null` on cancel). The activity is resolved
 * reflectively from the process, so no app-level wiring is required.
 */
actual suspend fun pickImage(): String? = suspendCancellableCoroutine { cont ->
    val activity = currentComponentActivity()
    if (activity == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val launcher = activity.activityResultRegistry.register(
        "kmpmlbench-image-picker-${System.nanoTime()}",
        ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? -> cont.resume(uri?.toString()) }
    cont.invokeOnCancellation { launcher.unregister() }
    launcher.launch("image/*")
}

actual suspend fun pickFile(extensions: List<String>): String? = suspendCancellableCoroutine { cont ->
    val activity = currentComponentActivity()
    if (activity == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val launcher = activity.activityResultRegistry.register(
        "kmpmlbench-file-picker-${System.nanoTime()}",
        ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? -> cont.resume(uri?.toString()) }
    cont.invokeOnCancellation { launcher.unregister() }
    launcher.launch("*/*")
}

/**
 * Android model-folder picker: launches `OpenDocumentTree` (the user selects a
 * folder, e.g. an ORT GenAI model directory), copies its contents into the
 * app's cache so ORT GenAI can load it from a real filesystem path, and resumes
 * with that cache directory path.
 */
actual suspend fun pickDirectory(): String? = suspendCancellableCoroutine { cont ->
    val activity = currentComponentActivity()
    if (activity == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val launcher = activity.activityResultRegistry.register(
        "kmpmlbench-dir-picker-${System.nanoTime()}",
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: android.net.Uri? ->
        if (uri == null) {
            cont.resume(null)
            return@register
        }
        cont.resume(copyTreeToCache(activity, uri)?.absolutePath)
    }
    cont.invokeOnCancellation { launcher.unregister() }
    launcher.launch(null)
}

/** Recursively copies a document tree (selected via [OpenDocumentTree]) into [ComponentActivity.cacheDir]. */
private fun copyTreeToCache(activity: ComponentActivity, treeUri: android.net.Uri): File? {
    val target = File(activity.cacheDir, "kmpmlbench_llm_model")
    target.deleteRecursively()
    if (!target.mkdirs()) return null
    val resolver = activity.contentResolver
    val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
    copyDocuments(resolver, treeUri, childrenUri, target)
    return target
}

private fun copyDocuments(
    resolver: ContentResolver,
    treeUri: android.net.Uri,
    childrenUri: android.net.Uri,
    destDir: File,
) {
    resolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (cursor.moveToNext()) {
            val docId = cursor.getString(idCol)
            val name = cursor.getString(nameCol) ?: docId
            val mime = cursor.getString(mimeCol)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val dest = File(destDir, name)
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                dest.mkdirs()
                val subChildren = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                copyDocuments(resolver, treeUri, subChildren, dest)
            } else {
                resolver.openInputStream(docUri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

/** Reflectively resolves the foreground [ComponentActivity] without a hidden API import. */
private fun currentComponentActivity(): ComponentActivity? = try {
    val threadClass = Class.forName("android.app.ActivityThread")
    val activityThread = threadClass.getMethod("currentActivityThread").invoke(null)
    val activitiesField = threadClass.getDeclaredField("mActivities").apply { isAccessible = true }
    val activities = activitiesField.get(activityThread) as? ArrayMap<*, *>
    activities?.values?.firstNotNullOfOrNull { entry ->
        val activityField = entry.javaClass.getDeclaredField("activity").apply { isAccessible = true }
        (activityField.get(entry) as? ComponentActivity)
    }
} catch (_: Throwable) {
    null
}

actual val realLlmEngineSupported: Boolean = true
