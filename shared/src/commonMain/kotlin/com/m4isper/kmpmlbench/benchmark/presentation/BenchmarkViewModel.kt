package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.model.BenchmarkInput
import com.m4isper.kmpmlbench.benchmark.domain.task.BenchmarkTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ClassificationTask
import com.m4isper.kmpmlbench.benchmark.domain.task.LlmTask
import com.m4isper.kmpmlbench.benchmark.domain.task.ObjectDetectionTask
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkUseCase
import com.m4isper.kmpmlbench.benchmark.data.platform.loadImageFile
import com.m4isper.kmpmlbench.benchmark.data.platform.pickImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation state holder. Owns the UI state, translates user intents into
 * use-case invocations, and maps the resulting domain model into a UI model.
 *
 * Depends only on the [BenchmarkUseCase] and [EngineProvider] abstractions, so
 * it is fully unit-testable without Compose (see BenchmarkViewModelTest).
 */
class BenchmarkViewModel(
    private val runBenchmark: BenchmarkUseCase,
    private val engineProvider: EngineProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(BenchmarkUiState())
    val state: StateFlow<BenchmarkUiState> get() = _state

    init {
        refreshEngines()
    }

    fun onTaskSelected(taskId: String) {
        if (taskId == _state.value.selectedTaskId) return
        _state.update { it.copy(selectedTaskId = taskId) }
        refreshEngines()
    }

    fun onScaleSelected(scale: Int) {
        _state.update { it.copy(scale = scale) }
        refreshEngines()
    }

    fun onInputSizeSelected(size: Int) {
        _state.update { it.copy(inputSize = size) }
        refreshEngines()
    }

    fun onIterationsChanged(iterations: Int) {
        _state.update { it.copy(iterations = iterations.coerceAtLeast(1)) }
    }

    fun onPromptChanged(prompt: String) {
        _state.update { it.copy(prompt = prompt) }
    }

    fun onMaxNewTokensSelected(maxNewTokens: Int) {
        _state.update { it.copy(maxNewTokens = maxNewTokens) }
    }

    fun onEngineSelected(engineId: String) {
        _state.update { it.copy(selectedEngineId = engineId) }
    }

    /**
     * Opens the platform image picker (desktop/Android) and decodes the chosen
     * file into an [ImageBuffer] for use as a custom benchmark input. Image-only
     * tasks (classification, detection, super-resolution) honor the custom image;
     * language tasks ignore it. iOS currently returns null (picker not wired yet).
     */
    fun onPickImage() {
        val current = _state.value
        if (current.isRunning) return
        if (current.selectedTaskId == LlmTask().id) return

        scope.launch {
            val path = pickImage() ?: return@launch
            val decoded = runCatching { loadImageFile(path) }.getOrNull() ?: return@launch
            _state.update { it.copy(customImagePath = path, customImage = decoded) }
        }
    }

    /** Clears a previously loaded custom image, reverting to the bundled input. */
    fun onClearImage() {
        _state.update { it.copy(customImagePath = null, customImage = null) }
    }

    fun onRunClicked() {
        val current = _state.value
        if (current.isRunning || current.selectedEngineId == null) return

        val task = buildTask(current)
        val engine = engineProvider.enginesFor(task)
            .first { it.id == current.selectedEngineId }

        _state.update { it.copy(isRunning = true, progress = 0f, result = null) }

        scope.launch {
            val result = runBenchmark.run(
                engine = engine,
                task = task,
                iterations = current.iterations,
                warmup = 3,
                customInput = customInputOrNull(task),
            ) { done, total ->
                _state.update { it.copy(progress = if (total > 0) done.toFloat() / total else 0f) }
            }
            _state.update {
                it.copy(
                    isRunning = false,
                    progress = 1f,
                    result = result.toUi(),
                )
            }
        }
    }

    /**
     * Runs every registered engine for the current task and collects their
     * results into [BenchmarkUiState.comparison] so the UI can compare them
     * side by side (latency, throughput, peak memory, quality).
     */
    fun onCompareClicked() {
        val current = _state.value
        if (current.isRunning) return

        val task = buildTask(current)
        val engines = engineProvider.enginesFor(task)
        if (engines.isEmpty()) return

        _state.update {
            it.copy(isRunning = true, progress = 0f, comparison = null, comparisonProgress = 0f)
        }

        scope.launch {
            val results = engines.mapIndexed { index, engine ->
                val result = runBenchmark.run(
                    engine = engine,
                    task = task,
                    iterations = current.iterations,
                    warmup = 3,
                    customInput = customInputOrNull(task),
                ) { _, _ -> }
                _state.update { it.copy(comparisonProgress = (index + 1).toFloat() / engines.size) }
                result.toUi()
            }
            _state.update {
                it.copy(
                    isRunning = false,
                    progress = 1f,
                    comparison = results,
                )
            }
        }
    }

    /** Cancels the view model's coroutines; call when the UI is disposed. */
    fun clear() {
        scope.cancel()
    }

    /** Builds the domain task matching the current UI selection. */
    private fun buildTask(state: BenchmarkUiState): BenchmarkTask = when (state.selectedTaskId) {
        ClassificationTask().id -> ClassificationTask()
        ObjectDetectionTask().id -> ObjectDetectionTask()
        LlmTask().id -> LlmTask(
            prompt = state.prompt.ifBlank { LlmTask().prompt },
            maxNewTokens = state.maxNewTokens,
        )
        else -> SuperResolutionTask(state.scale, state.inputSize, state.inputSize)
    }

    /**
     * Builds a custom [BenchmarkInput] from the user-supplied image when one is
     * loaded and the current task is image-based. Language tasks never receive a
     * custom image. The image is passed at its native size; each engine resizes
     * it to its model input internally.
     */
    private fun customInputOrNull(task: BenchmarkTask): BenchmarkInput? {
        val img = _state.value.customImage ?: return null
        if (task is LlmTask) return null
        return BenchmarkInput(
            width = img.width,
            height = img.height,
            label = "(custom)",
            image = img,
            isCustom = true,
        )
    }

    private fun refreshEngines() {
        val task = buildTask(_state.value)
        val engines = engineProvider.enginesFor(task)
            .map { EngineItem(it.id, it.displayName) }
        _state.update {
            it.copy(
                engines = engines,
                selectedEngineId = engines.firstOrNull()?.id ?: it.selectedEngineId,
                comparison = null,
                comparisonProgress = 0f,
            )
        }
    }
}
