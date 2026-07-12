# KMPMLBench (Kotlin Multiplatform ML Benchmark)

A powerful, cross-platform benchmarking suite designed to evaluate the performance of various Deep Learning inference engines across **Mobile (Android & iOS)** and **Desktop (Windows, macOS, Linux)** using **Kotlin Multiplatform**.

## 🎯 Motivation
The ML landscape is fragmented. Developers often struggle to decide which engine to use: Should I stick with TFLite for ease of use, or integrate NCNN for raw performance?

**KMPMLBench** provides a unified "Lab" to test these scenarios with identical models and logic, minimizing the "overhead" of platform-specific implementations.

## 🚀 Supported Platforms & Engines

| Engine | Android | iOS | Desktop (JVM/Native) | Acceleration | Status |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **ONNX Runtime** | ✅ | ✅ | ✅ | CoreML, NNAPI, DirectML, XNNPACK | Desktop (JVM) + Android ready — *Desktop offered with default CPU and CoreML (on macOS); Android offered with default CPU and NNAPI, for an on-device comparison*; iOS offers the real OD engine (`onnx-od`) via `onnxruntime-objc`, with CLS/SR/LLM still mock |
| **TensorFlow Lite** | ✅ | 📅 | 📅 | XNNPACK (CPU), NNAPI*, GPU* | Android Classification ready — *real MobileNetV2 inference via the `com.google.ai.edge.litert:litert` Android `.aar` (LiteRT 2.1.6), offered on CPU with the XNNPACK delegate (`tflite-cls`); no desktop JVM native is published, so Desktop/iOS stay out of scope; NNAPI is not yet shipped as a usable delegate artifact in the current LiteRT release* |
| **NCNN** | 📅 | 📅 | 📅 | Vulkan, Metal | Planned |
| **MNN** | 📅 | 📅 | 📅 | OpenCL, Vulkan, Metal | Planned |
| **ExecuTorch** | 📅 | 📅 | 📅 | XNNPACK, CoreML | Planned (LLM **mock** harness ready; `.pte` model deferred) |

## 🧠 Benchmarking Tasks
While the project started with **Super-Resolution**, it is designed to be modular:

- [x] **Super-Resolution:** ESPCN, FSRCNN, Real-ESRGAN — *working harness with a mock engine plus two real Desktop/JVM engines for side-by-side comparison: **ONNX Runtime** (Sub-Pixel CNN ×3, `super-resolution-10.onnx`) offered twice — once on the default CPU execution provider and once on the **CoreML** execution provider (Apple Neural Engine / GPU, on macOS) — so the UI can compare engine/latency trade-offs on the same model. The ×3 model output is rescaled to the selected task resolution so both providers stay directly comparable*.
- [x] **Image Classification:** MobileNetV2 — *working harness with a mock engine plus real engines: **ONNX Runtime** (MobileNetV2-12, `mobilenetv2-12.onnx`) runs actual inference on a **real bundled photo** (`classification_sample.jpg` — Grace Hopper, ImageNet class `military uniform`), softmaxes the 1000-class logits into a top-5 + confidence, and scores **standard ImageNet top-5 accuracy** (is the ground-truth label present in the predicted top-5?) — offered alongside the `MockClassificationEngine` so the UI can compare real vs mock quality. On Android a second real backend is available: **TensorFlow Lite** (LiteRT, float32 MobileNetV2, `mobilenetv2-224.tflite`) runs the same task via the `com.google.ai.edge.litert:litert` `Interpreter` with the XNNPACK delegate, offered on CPU (`tflite-cls`). The `.tflite` is converted from the same `mobilenetv2-12.onnx` (NHWC, ImageNet mean/std preprocessing) so both engines classify identically and agree on `military uniform` for the sample photo — letting the UI compare ONNX vs LiteRT on-device*.
- [x] **Object Detection:** YOLOv8-Nano — *working harness with a mock engine plus a real **ONNX Runtime** engine: on Desktop/JVM (YOLOv8n, `yolov8n.onnx`) it runs actual inference on a **real bundled photo** (`classification_sample.jpg`), decodes the `[1, 84, 8400]` output (sigmoid + argmax + NMS), and scores **mAP@0.5** vs the real ground-truth `person` box — offered alongside the `MockObjectDetectionEngine` so the UI can compare real vs mock quality. The same engines run on Android via `onnxruntime-android` (CPU and NNAPI).*
- [x] **On-device LLM:** Gemma 2B / Phi-2 (Experimental via ExecuTorch) — *working **mock** harness: `LlmTask` carries a prompt and `maxNewTokens`, and `MockLlmEngine` simulates token-by-token decode (first-token latency, tokens/sec, prompt/completion token counts) so the full UI/metrics pipeline is exercised end-to-end. The real ExecuTorch backend (a Gemma-2B / Phi-2 `.pte` model) is **deferred** until the model file is supplied out-of-band, exactly like the classification `.tflite`.*

## 🏗️ Architecture
The benchmark module (`shared/src/commonMain/.../benchmark`) follows Clean Architecture and is split into three layers with no framework dependencies leaking across them:

- **`domain`** — entities (`BenchmarkInput`/`Output`/`Metrics`/`Result`), the `MlEngine` and `EngineProvider` ports, the `SuperResolutionTask` and `LlmTask`, the `BenchmarkUseCase` contract with its `BenchmarkRunner` implementation, plus pure helpers: `Stats` (percentiles) and `processing` (synthetic image generation, box downsample, bilinear upsample, PSNR/SSIM — all framework-free and pixel-buffer based). `BenchmarkInput` additionally carries an optional `prompt` so language tasks can feed text into the same image-centric pipeline, and `LlmQualityMetrics` reports `generatedText`, `tokensPerSecond`, `firstTokenLatencyMs`, `promptTokens`, and `completionTokens`.
- **`data`** — adapters that implement the ports: `MockSuperResolutionEngine` (real lightweight upscale + quality scoring, no native deps), `MockClassificationEngine` (returns the ground-truth label as top-1 with genuine ImageNet class names for the top-5, so accuracy is 1.0 for a labeled input), `MockObjectDetectionEngine` (deterministic pseudo-detections near the ground-truth boxes, scored with mAP@0.5), and `EngineCatalog` (resolves engines per task; real engines plug in here). `EngineCatalog` is a plain object that delegates to a platform-specific `expect fun platformEnginesFor`, so each platform registers its own engines — the JVM build offers `OnnxSuperResolutionEngine` twice (ONNX Runtime: real Sub-Pixel CNN ×3 inference from the bundled `jvmMain/resources/models/super-resolution-10.onnx` model), once on the default CPU execution provider (`onnx-sr`) and once on the CoreML execution provider (`onnx-coreml-sr`); `OnnxClassificationEngine` (ONNX Runtime: real MobileNetV2-12 inference from the bundled `jvmMain/resources/models/mobilenetv2-12.onnx` model, `onnx-cls`, offered a second time on CoreML as `onnx-cls-coreml`); and `OnnxObjectDetectionEngine` (ONNX Runtime: real YOLOv8n inference from the bundled `jvmMain/resources/models/yolov8n.onnx` model, `onnx-od`, offered a second time on CoreML as `onnx-od-coreml`) — all ahead of their mocks; the Android build reuses the same engine source (compiled against `onnxruntime-android`) and offers each engine on the default CPU provider and on NNAPI (`onnx-sr`/`onnx-sr-nnapi`, `onnx-cls`/`onnx-cls-nnapi`, `onnx-od`/`onnx-od-nnapi`), while the iOS build offers the real ONNX OD engine (`onnx-od`) via `onnxruntime-objc` alongside its mock, with CLS/SR/LLM still mock. The `data` layer also hosts Android-only real engines compiled solely against `androidMain` (not `androidJvmMain`, since LiteRT has no desktop JVM build): `LiteRtClassificationEngine` runs the float32 `mobilenetv2-224.tflite` model via the `com.google.ai.edge.litert:litert` `Interpreter` with the XNNPACK delegate, offered on CPU (`tflite-cls`), so the Android UI can compare ONNX Runtime vs TensorFlow Lite on the same classification task.
- **`presentation`** — `BenchmarkViewModel` (state holder that drives the use case and maps the result to UI models), `BenchmarkUiState`, and the Compose `BenchmarkScreen`.

The UI depends only on abstractions, so each layer is unit-tested independently (see `shared/src/commonTest`).

## 📦 Bundled Models
The Desktop/JVM engines load pre-trained ONNX models bundled as classpath resources under `shared/src/jvmMain/resources/models/` (read at runtime via `classLoader.getResourceAsStream("models/<file>")`); the Android engines load the same files from the app's packed `assets/` under `shared/src/androidMain/assets/models/` (read via `AssetManager.open`):

| Model | Engine | Task | Size |
| :--- | :--- | :--- | :--- |
| `super-resolution-10.onnx` | `onnx-sr` / `onnx-coreml-sr` / `onnx-sr-nnapi` | Super-Resolution (Sub-Pixel CNN ×3) | ~234 KB |
| `mobilenetv2-12.onnx` | `onnx-cls` / `onnx-cls-coreml` / `onnx-cls-nnapi` | Image Classification (MobileNetV2-12, 1000 ImageNet classes) | ~13.3 MB |
| `imagenet_classes.txt` | `onnx-cls` / `tflite-cls` | ImageNet label map (1000 lines) | ~10 KB |
| `classification_sample.jpg` | `onnx-cls` / `tflite-cls` / `mock-cls` / `onnx-od` / `mock-od` | Classification input photo + Object-Detection input photo (Grace Hopper, PD) — ground-truth `military uniform` (CLS) / `person` box (OD) | ~60 KB |
| `sr_sample.jpg` | `onnx-sr` / `mock-sr` | Super-Resolution input photo (real image, resized to the task output) | ~60 KB |
| `yolov8n.onnx` | `onnx-od` / `onnx-od-coreml` / `onnx-od-nnapi` | Object Detection (YOLOv8-nano, 80 COCO classes) | ~12.3 MB |
| `coco_classes.txt` | `onnx-od` | COCO label map (80 lines) | ~1 KB |
| `mobilenetv2-224.tflite` | `tflite-cls` | Image Classification (float32 MobileNetV2, converted from `mobilenetv2-12.onnx`; 1000 ImageNet classes, NHWC + ImageNet mean/std) | ~13.4 MB |

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
6. **Quality:** *implemented* — for Super-Resolution the mock and ONNX engines upscale a **real bundled photo** (`sr_sample.jpg`) and score the reconstruction against the ground truth with **PSNR/SSIM**; for Classification the ONNX and LiteRT engines report the **predicted class, confidence, top-5**, and **standard ImageNet top-5 accuracy** versus the real photo's ground-truth label (`ClassificationQualityMetrics`), while the mock returns the ground-truth label as top-1 (accuracy 1.0) with a top-5 of genuine ImageNet names; for object detection the ONNX engine reports the **number of detections, mean confidence, and mAP@0.5** versus a real ground-truth `person` box on the bundled sample photo (`DetectionQualityMetrics`); the bundled `yolov8n.onnx` is a noisy export, so the engine runs at a raised 0.7 confidence threshold to surface its single correct detection, while the mock emits deterministic pseudo-detections near the ground truth; for on-device LLM the `MockLlmEngine` reports **generated text, tokens/sec, first-token latency, and prompt/completion token counts** (`LlmQualityMetrics`) from a simulated decode.
