package com.m4isper.kmpmlbench.benchmark.data.platform

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.util.ArrayMap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
