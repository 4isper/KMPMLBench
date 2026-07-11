package com.m4isper.kmpmlbench.benchmark.data.engine

import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the real ONNX Runtime engine. It loads the bundled
 * super-resolution model from resources and runs actual inference on the JVM,
 * exercising the native runtime end-to-end.
 */
class OnnxSuperResolutionEngineTest {
    @Test
    fun runsRealInferenceAndScoresQuality() {
        val task = SuperResolutionTask(scale = 2, inputWidth = 32, inputHeight = 32)
        val engine = OnnxSuperResolutionEngine(task)
        // The ONNX model is fixed at ×3 regardless of the task scale.
        val lr = BenchmarkInput(
            32,
            32,
            "LR",
            ImageBuffer(32, 32, IntArray(1024) { 0xFF808080.toInt() }),
        )

        engine.initialize()
        val output = engine.infer(lr)
        engine.close()

        // The ONNX model is fixed at 224x224 in, 672x672 (×3) out.
        assertEquals(672, output.width)
        assertEquals(672, output.height)
        assertTrue(output.quality.psnr.isFinite(), "psnr finite")
        assertTrue(output.quality.psnr > 0.0, "psnr positive")
        assertTrue(output.quality.psnr <= 100.0, "psnr capped")
        assertTrue(output.quality.ssim > 0.0, "ssim positive")
        assertTrue(output.quality.ssim <= 1.0, "ssim <= 1")
    }
}
