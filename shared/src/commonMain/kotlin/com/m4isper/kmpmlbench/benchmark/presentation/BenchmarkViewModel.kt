package com.m4isper.kmpmlbench.benchmark.presentation

import com.m4isper.kmpmlbench.benchmark.domain.engine.EngineProvider
import com.m4isper.kmpmlbench.benchmark.domain.task.SuperResolutionTask
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkUseCase
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

    fun onEngineSelected(engineId: String) {
        _state.update { it.copy(selectedEngineId = engineId) }
    }

    fun onRunClicked() {
        val current = _state.value
        if (current.isRunning || current.selectedEngineId == null) return

        val task = SuperResolutionTask(current.scale, current.inputSize, current.inputSize)
        val engine = engineProvider.enginesFor(task)
            .first { it.id == current.selectedEngineId }

        _state.update { it.copy(isRunning = true, progress = 0f, result = null) }

        scope.launch {
            val result = runBenchmark.run(
                engine = engine,
                task = task,
                iterations = current.iterations,
                warmup = 3,
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

    /** Cancels the view model's coroutines; call when the UI is disposed. */
    fun clear() {
        scope.cancel()
    }

    private fun refreshEngines() {
        val task = SuperResolutionTask(_state.value.scale, _state.value.inputSize, _state.value.inputSize)
        val engines = engineProvider.enginesFor(task)
            .map { EngineItem(it.id, it.displayName) }
        _state.update {
            it.copy(
                engines = engines,
                selectedEngineId = engines.firstOrNull()?.id ?: it.selectedEngineId,
            )
        }
    }
}
