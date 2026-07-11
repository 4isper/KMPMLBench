package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.QualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeEngine(
    override val id: String = "fake",
    override val displayName: String = "Fake Engine",
) : MlEngine {
    var initCalls = 0
    var closed = false
    override fun initialize() { initCalls++ }
    override fun infer(input: BenchmarkInput) = BenchmarkOutput(
        input.width * 2,
        input.height * 2,
        ImageBuffer(input.width * 2, input.height * 2, IntArray((input.width * 2) * (input.height * 2))),
        QualityMetrics(30.0, 0.9),
    )
    override fun close() { closed = true }
}

private class FakeProvider(private val engine: MlEngine) : EngineProvider {
    override fun enginesFor(task: BenchmarkTask) = listOf(engine)
}

private class FakeUseCase : BenchmarkUseCase {
    var runCalls = 0
    var lastIterations = -1

    override suspend fun run(
        engine: MlEngine,
        task: BenchmarkTask,
        iterations: Int,
        warmup: Int,
        onProgress: (Int, Int) -> Unit,
    ): BenchmarkResult {
        runCalls++
        lastIterations = iterations
        repeat(iterations) { onProgress(it + 1, iterations) }
        return BenchmarkResult(
            engineId = engine.id,
            engineName = engine.displayName,
            task = task,
            input = BenchmarkInput(32, 32, "LR", ImageBuffer(32, 32, IntArray(1024))),
            output = BenchmarkOutput(
                64,
                64,
                ImageBuffer(64, 64, IntArray(4096)),
                QualityMetrics(28.0, 0.85),
            ),
            metrics = BenchmarkMetrics(
                initTimeMs = 10.0,
                warmupMs = 5.0,
                iterations = iterations,
                avgLatencyMs = 2.0,
                minLatencyMs = 1.0,
                maxLatencyMs = 3.0,
                p50LatencyMs = 2.0,
                p95LatencyMs = 3.0,
                throughputFps = 500.0,
            ),
            quality = QualityMetrics(28.0, 0.85),
        )
    }
}

class BenchmarkViewModelTest {
    @Test
    fun selectsEngineAndPopulatesStateOnInit() {
        val engine = FakeEngine()
        val vm = BenchmarkViewModel(FakeUseCase(), FakeProvider(engine))
        assertEquals(1, vm.state.value.engines.size)
        assertEquals("fake", vm.state.value.selectedEngineId)
        vm.clear()
    }

    @Test
    fun intentsUpdateState() {
        val vm = BenchmarkViewModel(FakeUseCase(), FakeProvider(FakeEngine()))
        vm.onScaleSelected(4)
        assertEquals(4, vm.state.value.scale)
        vm.onInputSizeSelected(256)
        assertEquals(256, vm.state.value.inputSize)
        vm.onIterationsChanged(0)
        assertEquals(1, vm.state.value.iterations) // coerced to minimum
        vm.onIterationsChanged(30)
        assertEquals(30, vm.state.value.iterations)
        vm.clear()
    }

    @Test
    fun runClickedInvokesUseCaseAndMapsResult() = runBlocking {
        val engine = FakeEngine()
        val useCase = FakeUseCase()
        val vm = BenchmarkViewModel(useCase, FakeProvider(engine))

        vm.onIterationsChanged(15)
        vm.onRunClicked()

        withTimeout(5000) {
            while (vm.state.value.isRunning) delay(10)
        }

        assertEquals(1, useCase.runCalls)
        assertEquals(15, useCase.lastIterations)
        val result = checkNotNull(vm.state.value.result) { "result present" }
        assertEquals("Super-Resolution ×2", result.taskName)
        assertEquals(64, result.outputWidth)
        assertEquals(32, result.inputImage.width)
        assertEquals(28.0, result.psnr, 0.0)
        assertEquals(0.85, result.ssim, 0.0)
        assertEquals(3.0, result.maxLatencyMs, 0.0)
        vm.clear()
    }
}
