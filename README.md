# KMPMLBench (Kotlin Multiplatform ML Benchmark)

A powerful, cross-platform benchmarking suite designed to evaluate the performance of various Deep Learning inference engines across **Mobile (Android & iOS)** and **Desktop (Windows, macOS, Linux)** using **Kotlin Multiplatform**.

## 🎯 Motivation
The ML landscape is fragmented. Developers often struggle to decide which engine to use: Should I stick with TFLite for ease of use, or integrate NCNN for raw performance?

**KMPMLBench** provides a unified "Lab" to test these scenarios with identical models and logic, minimizing the "overhead" of platform-specific implementations.

## 🚀 Supported Platforms & Engines

| Engine | Android | iOS | Desktop (JVM/Native) | Acceleration | Status |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **ONNX Runtime** | 🖥️ | 📅 | ✅ | CoreML, DirectML, XNNPACK | Desktop (JVM) ready — *offered with both the default CPU and CoreML execution providers (on macOS) for comparison* |
| **TensorFlow Lite** | 📅 | 📅 | 📅 | NNAPI, CoreML, GPU | Planned — *no desktop JVM native is published (Android `.aar` only)* |
| **NCNN** | 📅 | 📅 | 📅 | Vulkan, Metal | Planned |
| **MNN** | 📅 | 📅 | 📅 | OpenCL, Vulkan, Metal | Planned |
| **ExecuTorch** | 📅 | 📅 | 📅 | XNNPACK, CoreML | Planned |

## 🧠 Benchmarking Tasks
While the project started with **Super-Resolution**, it is designed to be modular:

- [x] **Super-Resolution:** ESPCN, FSRCNN, Real-ESRGAN — *working harness with a mock engine plus two real Desktop/JVM engines for side-by-side comparison: **ONNX Runtime** (Sub-Pixel CNN ×3, `super-resolution-10.onnx`) offered twice — once on the default CPU execution provider and once on the **CoreML** execution provider (Apple Neural Engine / GPU, on macOS) — so the UI can compare engine/latency trade-offs on the same model. The ×3 model output is rescaled to the selected task resolution so both providers stay directly comparable*.
- [x] **Image Classification:** MobileNetV2 — *working harness with a mock engine plus a real Desktop/JVM engine: **ONNX Runtime** (MobileNetV2-12, `mobilenetv2-12.onnx`) runs actual inference on a synthetic ImageNet-style input, softmaxes the 1000-class logits into a top-5 + confidence, and scores accuracy against the synthetic label — offered alongside the `MockClassificationEngine` so the UI can compare real vs mock quality*.
- [x] **Object Detection:** YOLOv8-Nano — *working harness with a mock engine plus a real Desktop/JVM engine: **ONNX Runtime** (YOLOv8n, `yolov8n.onnx`) runs actual inference on a synthetic frame, decodes the `[1, 84, 8400]` output (sigmoid + argmax + NMS), and scores **mAP@0.5** vs the synthetic ground truth — offered alongside the `MockObjectDetectionEngine` so the UI can compare real vs mock quality*.
- [ ] **On-device LLM:** Gemma 2B / Phi-2 (Experimental via ExecuTorch).

## 🏗️ Architecture
The benchmark module (`shared/src/commonMain/.../benchmark`) follows Clean Architecture and is split into three layers with no framework dependencies leaking across them:

- **`domain`** — entities (`BenchmarkInput`/`Output`/`Metrics`/`Result`), the `MlEngine` and `EngineProvider` ports, the `SuperResolutionTask`, the `BenchmarkUseCase` contract with its `BenchmarkRunner` implementation, plus pure helpers: `Stats` (percentiles) and `processing` (synthetic image generation, box downsample, bilinear upsample, PSNR/SSIM — all framework-free and pixel-buffer based).
- **`data`** — adapters that implement the ports: `MockSuperResolutionEngine` (real lightweight upscale + quality scoring, no native deps), `MockClassificationEngine` (deterministic pseudo-logits → softmax → accuracy vs the synthetic label), `MockObjectDetectionEngine` (deterministic pseudo-detections near the ground-truth boxes, scored with mAP@0.5), and `EngineCatalog` (resolves engines per task; real engines plug in here). `EngineCatalog` is an `expect/actual` so each platform registers its own engines — the JVM build offers `OnnxSuperResolutionEngine` twice (ONNX Runtime: real Sub-Pixel CNN ×3 inference from the bundled `jvmMain/resources/models/super-resolution-10.onnx` model), once on the default CPU execution provider (`onnx-sr`) and once on the CoreML execution provider (`onnx-coreml-sr`); `OnnxClassificationEngine` (ONNX Runtime: real MobileNetV2-12 inference from the bundled `jvmMain/resources/models/mobilenetv2-12.onnx` model, `onnx-cls`, offered a second time on CoreML as `onnx-cls-coreml`); and `OnnxObjectDetectionEngine` (ONNX Runtime: real YOLOv8n inference from the bundled `jvmMain/resources/models/yolov8n.onnx` model, `onnx-od`) — all ahead of their mocks, while Android and iOS stay mock-only.
- **`presentation`** — `BenchmarkViewModel` (state holder that drives the use case and maps the result to UI models), `BenchmarkUiState`, and the Compose `BenchmarkScreen`.

The UI depends only on abstractions, so each layer is unit-tested independently (see `shared/src/commonTest`).

## 📦 Bundled Models
The Desktop/JVM engines load pre-trained ONNX models bundled as classpath resources under `shared/src/jvmMain/resources/models/` (read at runtime via `classLoader.getResourceAsStream("models/<file>")`):

| Model | Engine | Task | Size |
| :--- | :--- | :--- | :--- |
| `super-resolution-10.onnx` | `onnx-sr` / `onnx-coreml-sr` | Super-Resolution (Sub-Pixel CNN ×3) | ~234 KB |
| `mobilenetv2-12.onnx` | `onnx-cls` / `onnx-cls-coreml` | Image Classification (MobileNetV2-12, 1000 ImageNet classes) | ~13.3 MB |
| `imagenet_classes.txt` | `onnx-cls` | ImageNet label map (1000 lines) | ~10 KB |
| `yolov8n.onnx` | `onnx-od` | Object Detection (YOLOv8-nano, 80 COCO classes) | ~12.3 MB |
| `coco_classes.txt` | `onnx-od` | COCO label map (80 lines) | ~1 KB |

> **⚠️ Downloading models — the Git LFS trap.** The `onnx/models` repo stores the real `.onnx` binaries via Git LFS. `raw.githubusercontent.com/...` returns a 133-byte LFS *pointer* (starts with `version https://git-lfs.github.com/spec/v1`), and `github.com/onnx/models/raw/...` 404s. Bundling either produces the same runtime failure: `OrtException: ORT_INVALID_PROTOBUF - Failed to load model because protobuf parsing failed`. Always fetch via the LFS media proxy `https://media.githubusercontent.com/media/onnx/models/main/<path>`, resolve the current `<path>` from the GitHub tree API first (the repo is reorganized periodically), and verify the byte count (~13.3 MB for a full float32 MobileNetV2 — anything near ~1.8 MB is a truncated download) and the protobuf header (`0807 1207 7079 746f 7263 68 …`) before copying into `resources/models/`. Gradle copies binary resources byte-for-byte, so a parse failure means the *source* file is incomplete, not the packaging step.

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
4. **Memory Peak:** Maximum RAM/VRAM usage (MB) — *implemented* (peak process memory sampled across init, warm-up, and the measured loop via a portable `expect/actual` counter; VRAM is not separately tracked).
5. **Energy Consumption:** (Mobile only) Battery impact during prolonged tasks — *planned*.
6. **Quality:** *implemented* — for Super-Resolution the mock and ONNX engines upscale a synthetic image and score the reconstruction against the ground truth with **PSNR/SSIM**; for Classification the ONNX engine reports the **predicted class, confidence, top-5**, and **accuracy** versus the synthetic label (`ClassificationQualityMetrics`), while the mock scores a deterministic pseudo-logits accuracy; for Object Detection the ONNX engine reports the **number of detections, mean confidence, and mAP@0.5** versus the synthetic ground truth (`DetectionQualityMetrics`), while the mock emits deterministic pseudo-detections near the ground truth.
