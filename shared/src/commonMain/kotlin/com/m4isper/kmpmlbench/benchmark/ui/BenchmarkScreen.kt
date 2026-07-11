package com.m4isper.kmpmlbench.benchmark.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.m4isper.kmpmlbench.Greeting
import com.m4isper.kmpmlbench.benchmark.BenchmarkRunner
import com.m4isper.kmpmlbench.benchmark.engine.EngineCatalog
import com.m4isper.kmpmlbench.benchmark.engine.MlEngine
import com.m4isper.kmpmlbench.benchmark.metrics.BenchmarkResult
import com.m4isper.kmpmlbench.benchmark.task.SuperResolutionTask
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import com.m4isper.kmpmlbench.generated.resources.Res
import com.m4isper.kmpmlbench.generated.resources.compose_multiplatform

@Composable
fun BenchmarkScreen() {
    val runner = remember { BenchmarkRunner() }

    var scale by remember { mutableStateOf(2) }
    var inputSize by remember { mutableStateOf(128) }
    var iterationsStr by remember { mutableStateOf("50") }
    var selectedEngineId by remember { mutableStateOf<String?>(null) }

    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<BenchmarkResult?>(null) }

    val task = SuperResolutionTask(scale, inputSize, inputSize)
    val engines = remember(task) { EngineCatalog.enginesFor(task) }
    LaunchedEffect(engines) {
        if (selectedEngineId == null || engines.none { it.id == selectedEngineId }) {
            selectedEngineId = engines.firstOrNull()?.id
        }
    }
    val iterations = iterationsStr.toIntOrNull()?.coerceAtLeast(1) ?: 50

    val scope = rememberCoroutineScope()

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
                            selected = scale,
                            onSelect = { scale = it },
                            label = { "×$it" },
                        )
                    }
                    LabeledRow("Input size") {
                        SegmentedChoice(
                            options = listOf(64, 128, 256),
                            selected = inputSize,
                            onSelect = { inputSize = it },
                            label = { "${it}px" },
                        )
                    }
                    OutlinedTextField(
                        value = iterationsStr,
                        onValueChange = { iterationsStr = it.filter { c -> c.isDigit() } },
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
                    engines.forEach { engine ->
                        EngineRow(engine, engine.id == selectedEngineId) {
                            selectedEngineId = engine.id
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        progress = 0
                        result = null
                        val engine: MlEngine = engines.first { it.id == selectedEngineId }
                        result = runner.run(engine, task, iterations, warmup = 3) { done, _ ->
                            progress = done
                        }
                        isRunning = false
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRunning) "Running…" else "Run benchmark")
            }

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { if (iterations > 0) progress.toFloat() / iterations else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            result?.let { ResultCard(it) }

            PreviewCard(scale)
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
private fun EngineRow(engine: MlEngine, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(engine.displayName)
    }
}

@Composable
private fun ResultCard(result: BenchmarkResult) {
    val m = result.metrics
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${result.engineName} · ${result.task.displayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            MetricRow("Init time", "%.1f ms".format(m.initTimeMs))
            MetricRow("Avg latency", "%.2f ms".format(m.avgLatencyMs))
            MetricRow("Min / Max", "%.2f / %.2f ms".format(m.minLatencyMs, m.maxLatencyMs))
            MetricRow("p50 latency", "%.2f ms".format(m.p50LatencyMs))
            MetricRow("p95 latency", "%.2f ms".format(m.p95LatencyMs))
            MetricRow("Throughput", "%.1f fps".format(m.throughputFps))
            MetricRow("Iterations", "${m.iterations}")
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
private fun PreviewCard(scale: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Preview (visual only)", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                    Text("LR input", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val out = (64 * scale).dp
                    Image(
                        painter = painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = null,
                        modifier = Modifier.size(out.coerceAtMost(256.dp)),
                    )
                    Text("SR output ×$scale", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
