// Minimal JNI bridge exposing a NCNN network over the ncnn C API.
//
// NCNN no longer ships a Java/Kotlin wrapper in its releases, so this small
// shim (compiled into libncnn_jni.so, statically linking the ncnn core) gives
// Kotlin a create / load / run / destroy surface. The model is loaded from
// in-memory bytes (text .param + binary .bin), avoiding any filesystem copy.
//
// Build (NDK, per ABI) — see README "NCNN" section for the full recipe:
//   cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
//         -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 \
//         -DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF \
//         -DNCNN_VULKAN=OFF -DNCNN_STRING=ON -DNCNN_STDIO=ON \
//         -S <ncnn src> -B build && cmake --build build -j
// then compile this file into a shared lib linking the produced libncnn.a.

#include <jni.h>
#include <string.h>
#include "c_api.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_m4isper_kmpmlbench_benchmark_data_engine_NcnnNet_nativeCreate(JNIEnv*, jobject) {
    return (jlong)ncnn_net_create();
}

JNIEXPORT void JNICALL
Java_com_m4isper_kmpmlbench_benchmark_data_engine_NcnnNet_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jbyteArray paramBytes, jbyteArray modelBytes) {
    ncnn_net_t net = (ncnn_net_t)handle;

    // Text .param (null-terminated).
    jsize pLen = env->GetArrayLength(paramBytes);
    jbyte* pSrc = env->GetByteArrayElements(paramBytes, nullptr);
    char* pBuf = new char[pLen + 1];
    memcpy(pBuf, pSrc, (size_t)pLen);
    pBuf[pLen] = '\0';
    ncnn_net_load_param_memory(net, pBuf);
    delete[] pBuf;
    env->ReleaseByteArrayElements(paramBytes, pSrc, JNI_ABORT);

    // Binary .bin weights.
    jsize mLen = env->GetArrayLength(modelBytes);
    jbyte* mSrc = env->GetByteArrayElements(modelBytes, nullptr);
    unsigned char* mBuf = new unsigned char[mLen];
    memcpy(mBuf, mSrc, (size_t)mLen);
    ncnn_net_load_model_memory(net, mBuf);
    delete[] mBuf;
    env->ReleaseByteArrayElements(modelBytes, mSrc, JNI_ABORT);
}

JNIEXPORT jfloatArray JNICALL
Java_com_m4isper_kmpmlbench_benchmark_data_engine_NcnnNet_nativeRun(
    JNIEnv* env, jobject, jlong handle, jfloatArray input, jint w, jint h, jint c) {
    ncnn_net_t net = (ncnn_net_t)handle;
    jfloat* in = env->GetFloatArrayElements(input, nullptr);
    jsize n = env->GetArrayLength(input);

    ncnn_mat_t inMat = ncnn_mat_create_3d(w, h, c, nullptr);
    float* dst = (float*)ncnn_mat_get_data(inMat);
    memcpy(dst, in, (size_t)n * sizeof(float));

    ncnn_extractor_t ex = ncnn_extractor_create(net);
    int inIdx = ncnn_net_get_input_index(net, 0);
    int outIdx = ncnn_net_get_output_index(net, 0);
    ncnn_extractor_input_index(ex, inIdx, inMat);

    // ncnn_extractor_extract_index allocates a new mat and writes it to *mat.
    ncnn_mat_t outMat = nullptr;
    ncnn_extractor_extract_index(ex, outIdx, &outMat);

    int ow = ncnn_mat_get_w(outMat);
    int oh = ncnn_mat_get_h(outMat);
    int oc = ncnn_mat_get_c(outMat);
    int outN = ow * oh * oc;
    float* src = (float*)ncnn_mat_get_data(outMat);

    jfloatArray result = env->NewFloatArray(outN);
    env->SetFloatArrayRegion(result, 0, outN, src);

    env->ReleaseFloatArrayElements(input, in, JNI_ABORT);
    ncnn_mat_destroy(inMat);
    ncnn_mat_destroy(outMat);
    ncnn_extractor_destroy(ex);
    return result;
}

JNIEXPORT void JNICALL
Java_com_m4isper_kmpmlbench_benchmark_data_engine_NcnnNet_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    ncnn_net_destroy((ncnn_net_t)handle);
}

}
