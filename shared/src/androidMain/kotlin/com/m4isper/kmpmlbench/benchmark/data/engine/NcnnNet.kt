package com.m4isper.kmpmlbench.benchmark.data.engine

/**
 * Minimal JNI bridge to a NCNN network loaded via the ncnn C API.
 *
 * NCNN no longer ships a Java/Kotlin wrapper in its releases, so this project
 * provides a small native shim (`ncnn_jni_bridge.cpp`, built into
 * `libncnn_jni.so`) that exposes create / load / run / destroy over the C API.
 * The model is loaded directly from in-memory bytes (the converted `.param`
 * text and `.bin` weights), so no filesystem copy of the assets is required.
 *
 * The native library is loaded once; each [NcnnNet] instance owns one ncnn net.
 */
class NcnnNet {
    private var handle: Long = 0

    init {
        handle = nativeCreate()
    }

    /** Loads a model from its converted NCNN bytes: [paramBytes] (text .param) + [modelBytes] (binary .bin). */
    fun load(paramBytes: ByteArray, modelBytes: ByteArray) {
        nativeLoad(handle, paramBytes, modelBytes)
    }

    /** Runs inference on a [w]×[h]×[c] float blob and returns the flattened output. */
    fun run(input: FloatArray, w: Int, h: Int, c: Int): FloatArray = nativeRun(handle, input, w, h, c)

    fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeLoad(handle: Long, paramBytes: ByteArray, modelBytes: ByteArray)
    private external fun nativeRun(handle: Long, input: FloatArray, w: Int, h: Int, c: Int): FloatArray
    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("ncnn_jni")
        }
    }
}
