package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkOutput
import com.m4isper.kmpmlbench.benchmark.domain.model.ClassificationQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.model.SrQualityMetrics
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkUseCase
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        SrQualityMetrics(30.0, 0.9),
    )
    override fun close() { closed = true }
}

private class FakeProvider(private val engine: MlEngine) : EngineProvider {
    var lastCustomModelPath: String? = null
    var lastCustomLabelsPath: String? = null
    override fun enginesFor(
        task: BenchmarkTask,
        customModelPath: String?,
        customLabelsPath: String?,
    ): List<MlEngine> {
        lastCustomModelPath = customModelPath
        lastCustomLabelsPath = customLabelsPath
        return listOf(engine)
    }
}

private class TwoEngineProvider : EngineProvider {
    private val a = FakeEngine(id = "a", displayName = "Engine A")
    private val b = FakeEngine(id = "b", displayName = "Engine B")
    override fun enginesFor(
        task: BenchmarkTask,
        customModelPath: String?,
        customLabelsPath: String?,
    ) = listOf(a, b)
}

private class FakeUseCase : BenchmarkUseCase {
    var runCalls = 0
    var lastIterations = -1

    override suspend fun run(
        engine: MlEngine,
        task: BenchmarkTask,
        iterations: Int,
        warmup: Int,
        customInput: BenchmarkInput?,
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
                SrQualityMetrics(28.0, 0.85),
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
                peakMemoryMb = 25.0,
            ),
            quality = SrQualityMetrics(28.0, 0.85),
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
    fun taskSwitchUpdatesSelectedTask() {
        val vm = BenchmarkViewModel(FakeUseCase(), FakeProvider(FakeEngine()))
        assertEquals("super-resolution", vm.state.value.selectedTaskId)
        vm.onTaskSelected(ClassificationTask().id)
        assertEquals(ClassificationTask().id, vm.state.value.selectedTaskId)
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
        assertTrue(result.quality is SrQualityUi)
        assertEquals(28.0, result.quality.psnr, 0.0)
        assertEquals(0.85, result.quality.ssim, 0.0)
        assertEquals(3.0, result.maxLatencyMs, 0.0)
        vm.clear()
    }

    @Test
    fun compareRunsEveryEngineAndPopulatesComparison() = runBlocking {
        val vm = BenchmarkViewModel(FakeUseCase(), TwoEngineProvider())
        vm.onIterationsChanged(10)
        vm.onCompareClicked()

        withTimeout(5000) {
            while (vm.state.value.isRunning) delay(10)
        }

        val comparison = checkNotNull(vm.state.value.comparison) { "comparison present" }
        assertEquals(2, comparison.size)
        assertEquals("Engine A", comparison[0].engineName)
        assertEquals("Engine B", comparison[1].engineName)
        // Single-run result is left untouched by a comparison run.
        assertEquals(null, vm.state.value.result)
        vm.clear()
    }

    @Test
    fun taskSwitchClearsComparison() {
        val vm = BenchmarkViewModel(FakeUseCase(), FakeProvider(FakeEngine()))
        vm.onTaskSelected(ClassificationTask().id)
        assertEquals(null, vm.state.value.comparison)
        vm.clear()
    }

    @Test
    fun llmTaskUpdatesPromptAndMaxNewTokensState() {
        val vm = BenchmarkViewModel(FakeUseCase(), FakeProvider(FakeEngine()))
        vm.onTaskSelected(LlmTask().id)
        assertEquals(LlmTask().id, vm.state.value.selectedTaskId)
        vm.onPromptChanged("Translate to French")
        assertEquals("Translate to French", vm.state.value.prompt)
        vm.onMaxNewTokensSelected(96)
        assertEquals(96, vm.state.value.maxNewTokens)
        vm.clear()
    }
}

/** Verifies the runner honors a user-supplied (custom) input and flags the result as custom. */
private class InputRecordingEngine : MlEngine {
    var lastInput: BenchmarkInput? = null
    override val id = "rec"
    override val displayName = "Recorder"
    override fun initialize() {}
    override fun infer(input: BenchmarkInput): BenchmarkOutput {
        lastInput = input
        return BenchmarkOutput(
            input.width,
            input.height,
            input.image,
            ClassificationQualityMetrics("x", 0.9, listOf("x" to 0.9), 1.0),
        )
    }
    override fun close() {}
}

class BenchmarkRunnerCustomInputTest {
    @Test
    fun customInputOverridesTaskInputAndMarksResultCustom() = runBlocking {
        val runner = BenchmarkRunner()
        val engine = InputRecordingEngine()
        val task = ClassificationTask()
        val customImg = ImageBuffer(7, 7, IntArray(49) { 0xFF123456.toInt() })
        val custom = BenchmarkInput(7, 7, "(custom)", customImg, isCustom = true)

        val result = runner.run(engine, task, iterations = 3, warmup = 1, customInput = custom)

        assertTrue(result.isCustom)
        assertEquals(customImg, result.input.image)
        assertEquals(customImg, engine.lastInput?.image)
        assertEquals("(custom)", engine.lastInput?.label)
    }

    @Test
    fun nullCustomInputFallsBackToTaskInput() = runBlocking {
        val runner = BenchmarkRunner()
        val engine = InputRecordingEngine()
        val task = ClassificationTask()

        val result = runner.run(engine, task, iterations = 2, warmup = 1, customInput = null)

        assertTrue(!result.isCustom)
        assertEquals(task.createInput().image.width, engine.lastInput?.image?.width)
    }
}
