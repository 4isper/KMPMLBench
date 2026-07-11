# KMPMLBench (Kotlin Multiplatform ML Benchmark)

A powerful, cross-platform benchmarking suite designed to evaluate the performance of various Deep Learning inference engines across **Mobile (Android & iOS)** and **Desktop (Windows, macOS, Linux)** using **Kotlin Multiplatform**.

## 🎯 Motivation
The ML landscape is fragmented. Developers often struggle to decide which engine to use: Should I stick with TFLite for ease of use, or integrate NCNN for raw performance?

**KMPMLBench** provides a unified "Lab" to test these scenarios with identical models and logic, minimizing the "overhead" of platform-specific implementations.

## 🚀 Supported Platforms & Engines

| Engine | Android | iOS | Desktop (JVM/Native) | Acceleration | Status |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **ONNX Runtime** | 🏗️ | 🏗️ | 🏗️ | CoreML, DirectML, XNNPACK | In Progress |
| **TensorFlow Lite** | 📅 | 📅 | 📅 | NNAPI, CoreML, GPU | Planned |
| **NCNN** | 📅 | 📅 | 📅 | Vulkan, Metal | Planned |
| **MNN** | 📅 | 📅 | 📅 | OpenCL, Vulkan, Metal | Planned |
| **ExecuTorch** | 📅 | 📅 | 📅 | XNNPACK, CoreML | Planned |

## 🧠 Benchmarking Tasks
While the project started with **Super-Resolution**, it is designed to be modular:

- [x] **Super-Resolution:** ESPCN, FSRCNN, Real-ESRGAN — *working benchmark harness with a mock engine; real models planned*.
- [ ] **Image Classification:** MobileNetV3, EfficientNet (Planned).
- [ ] **Object Detection:** YOLOv8-Nano (Planned).
- [ ] **On-device LLM:** Gemma 2B / Phi-2 (Experimental via ExecuTorch).

## 🏗️ Architecture
The benchmark module (`shared/src/commonMain/.../benchmark`) follows Clean Architecture and is split into three layers with no framework dependencies leaking across them:

- **`domain`** — entities (`BenchmarkInput`/`Output`/`Metrics`/`Result`), the `MlEngine` and `EngineProvider` ports, the `SuperResolutionTask`, and the `BenchmarkUseCase` contract with its `BenchmarkRunner` implementation plus a pure `Stats` helper (percentiles).
- **`data`** — adapters that implement the ports: `MockSuperResolutionEngine` (synthetic CPU workload, no native deps) and `EngineCatalog` (resolves engines per task; real engines plug in here).
- **`presentation`** — `BenchmarkViewModel` (state holder that drives the use case and maps the result to UI models), `BenchmarkUiState`, and the Compose `BenchmarkScreen`.

The UI depends only on abstractions, so each layer is unit-tested independently (see `shared/src/commonTest`).

## ▶️ Running on Desktop
The product currently targets Desktop (JVM). From the repository root:

```bash
./gradlew :desktopApp:run
```

This launches the Compose Desktop app where you can pick an engine and the
Super-Resolution parameters, run the benchmark, and inspect the timing metrics
(init time, avg/min/max/p50/p95 latency, throughput).

## 📊 Performance Metrics
We measure more than just speed:
1. **Inference Latency:** Average time per execution, plus min/max, p50 and p95 (ms) — *implemented*.
2. **Initialization Time:** Cold-start model load (ms) — *implemented*.
3. **Throughput:** Inferences per second — *implemented*.
4. **Memory Peak:** Maximum RAM/VRAM usage (MB) — *planned*.
5. **Energy Consumption:** (Mobile only) Battery impact during prolonged tasks — *planned*.
6. **Quality (PSNR/SSIM):** *planned*; left out of the mock engine.
