package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.DetectionQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.LlmQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.QualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask

/** Maps a domain [BenchmarkResult] into the presentation model. */
fun BenchmarkResult.toUi(): BenchmarkResultUi = BenchmarkResultUi(
    engineName = engineName,
    taskName = task.displayName,
    taskId = task.id,
    outputWidth = output.width,
    outputHeight = output.height,
    inputImage = input.image,
    outputImage = if (task is ClassificationTask || task is LlmTask) null else output.image,
    quality = quality.toUi(),
    initTimeMs = metrics.initTimeMs,
    avgLatencyMs = metrics.avgLatencyMs,
    minLatencyMs = metrics.minLatencyMs,
    maxLatencyMs = metrics.maxLatencyMs,
    p50LatencyMs = metrics.p50LatencyMs,
    p95LatencyMs = metrics.p95LatencyMs,
    throughputFps = metrics.throughputFps,
    peakMemoryMb = metrics.peakMemoryMb,
    iterations = metrics.iterations,
    isCustom = isCustom,
)

/** Maps the sealed domain quality into the sealed UI quality. */
fun QualityMetrics.toUi(): QualityUi = when (this) {
    is SrQualityMetrics -> SrQualityUi(psnr, ssim)
    is ClassificationQualityMetrics -> ClassificationQualityUi(
        predictedClass = predictedClass,
        confidence = confidence,
        topK = topK,
        accuracy = accuracy,
    )
    is DetectionQualityMetrics -> DetectionQualityUi(
        numDetections = numDetections,
        meanConfidence = meanConfidence,
        mAP = mAP,
    )
    is LlmQualityMetrics -> LlmQualityUi(
        generatedText = generatedText,
        tokensPerSecond = tokensPerSecond,
        firstTokenLatencyMs = firstTokenLatencyMs,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
    )
}
