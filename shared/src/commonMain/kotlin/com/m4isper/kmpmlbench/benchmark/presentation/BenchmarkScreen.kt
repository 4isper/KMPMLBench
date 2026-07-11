package com.m4isper.kmpmlbench.benchmark.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.m4isper.kmpmlbench.Greeting
import com.m4isper.kmpmlbench.benchmark.data.engine.EngineCatalog
import com.m4isper.kmpmlbench.benchmark.domain.model.ImageBuffer
import com.m4isper.kmpmlbench.benchmark.domain.processing.downsample
import com.m4isper.kmpmlbench.benchmark.domain.usecase.BenchmarkRunner

@Composable
fun BenchmarkScreen() {
    val viewModel = remember {
        BenchmarkViewModel(
            runBenchmark = BenchmarkRunner(),
            engineProvider = EngineCatalog,
        )
    }
    val uiState by viewModel.state.collectAsState(initial = viewModel.state.value)

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeContentPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("KMPMLBench", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "ML inference benchmarking · ${Greeting().greet()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Task: Super-Resolution", style = MaterialTheme.typography.titleMedium)
                    LabeledRow("Upscale factor") {
                        SegmentedChoice(
                            options = listOf(2, 4),
                            selected = uiState.scale,
                            onSelect = viewModel::onScaleSelected,
                            label = { "×$it" },
                        )
                    }
                    LabeledRow("Input size") {
                        SegmentedChoice(
                            options = listOf(64, 128, 256),
                            selected = uiState.inputSize,
                            onSelect = viewModel::onInputSizeSelected,
                            label = { "${it}px" },
                        )
                    }
                    var iterationsStr by remember { mutableStateOf(uiState.iterations.toString()) }
                    OutlinedTextField(
                        value = iterationsStr,
                        onValueChange = {
                            iterationsStr = it.filter { c -> c.isDigit() }
                            viewModel.onIterationsChanged(iterationsStr.toIntOrNull() ?: 1)
                        },
                        label = { Text("Iterations") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Engine", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    uiState.engines.forEach { engine ->
                        EngineRow(
                            item = engine,
                            selected = engine.id == uiState.selectedEngineId,
                            onClick = { viewModel.onEngineSelected(engine.id) },
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::onRunClicked,
                enabled = !uiState.isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (uiState.isRunning) "Running…" else "Run benchmark")
            }

            if (uiState.isRunning) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            uiState.result?.let { result ->
                ResultCard(result)
                PreviewCard(
                    inputImage = result.inputImage,
                    outputImage = result.outputImage,
                    outputWidth = result.outputWidth,
                    outputHeight = result.outputHeight,
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        content()
    }
}

@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            OutlinedButton(
                onClick = { onSelect(option) },
                colors = if (option == selected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text(label(option))
            }
        }
    }
}

@Composable
private fun EngineRow(item: EngineItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(item.name)
    }
}

@Composable
private fun ResultCard(result: BenchmarkResultUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${result.engineName} · ${result.taskName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            MetricRow("Output", "${result.outputWidth}×${result.outputHeight}")
            MetricRow("Init time", "${result.initTimeMs.format(1)} ms")
            MetricRow("Avg latency", "${result.avgLatencyMs.format(2)} ms")
            MetricRow("Min / Max", "${result.minLatencyMs.format(2)} / ${result.maxLatencyMs.format(2)} ms")
            MetricRow("p50 latency", "${result.p50LatencyMs.format(2)} ms")
            MetricRow("p95 latency", "${result.p95LatencyMs.format(2)} ms")
            MetricRow("Throughput", "${result.throughputFps.format(1)} fps")
            MetricRow("PSNR", "${result.psnr.format(2)} dB")
            MetricRow("SSIM", result.ssim.format(4))
            MetricRow("Iterations", "${result.iterations}")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviewCard(
    inputImage: ImageBuffer,
    outputImage: ImageBuffer,
    outputWidth: Int,
    outputHeight: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Preview (reconstructed pixels)", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreviewImage(inputImage)
                PreviewImage(outputImage)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    "LR ${inputImage.width}×${inputImage.height}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "SR ${outputWidth}×$outputHeight",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Draws a downsampled [buffer] onto a [Canvas] (portable, no platform pixel API).
 * The buffer is shrunk to at most [maxDim] px per side so the rect count stays
 * cheap even for large super-resolved outputs.
 */
@Composable
private fun PreviewImage(buffer: ImageBuffer, maxDim: Int = 48) {
    val preview = remember(buffer) {
        val factor = (maxOf(buffer.width, buffer.height) + maxDim - 1) / maxDim
        if (factor <= 1) buffer else downsample(buffer, factor)
    }
    Canvas(modifier = Modifier.size(preview.width.dp)) {
        val cw = size.width / preview.width
        val ch = size.height / preview.height
        for (y in 0 until preview.height) {
            for (x in 0 until preview.width) {
                drawRect(
                    color = Color(preview.pixels[y * preview.width + x]),
                    topLeft = Offset(x * cw, y * ch),
                    size = Size(cw, ch),
                )
            }
        }
    }
}

/** Multiplatform replacement for `String.format` (unavailable in commonMain). */
private fun Double.format(decimals: Int): String {
    var scale = 1.0
    repeat(decimals) { scale *= 10.0 }
    val rounded = kotlin.math.round(this * scale) / scale
    val sign = if (rounded < 0.0) "-" else ""
    val abs = kotlin.math.abs(rounded)
    val intPart = abs.toLong()
    val fracPart = kotlin.math.round((abs - intPart) * scale).toLong()
    val (intDigits, fracDigits) = if (fracPart >= scale.toLong()) {
        (intPart + 1L) to 0L
    } else {
        intPart to fracPart
    }
    return buildString {
        append(sign).append(intDigits)
        if (decimals > 0) append('.').append(fracDigits.toString().padStart(decimals, '0'))
    }
}
