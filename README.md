# Pocket AI

Pocket AI is a private, local-first Android chat application for GGUF language models. Normal chat inference runs on-device through **llama.cpp**; prompts and responses are not sent to OpenAI or another AI API. The app is aimed at modern arm64 Android phones and defaults are tuned conservatively for a Samsung Galaxy S25 Ultra (12 GB RAM): 4096-token context, 1024 max new tokens, temperature 0.7, top-p 0.9 and top-k 40.

## What is implemented

- Kotlin + Jetpack Compose + Material 3 UI, dark by default.
- Multiple local conversations, local Room history, delete one/all chats.
- Streaming token display, stop/cancel, regenerate, copy and auto-scroll.
- Android Storage Access Framework model picker; no broad storage permission.
- Multiple imported GGUF files in app-private storage; select/load/unload/delete and file-size/status display.
- llama.cpp native inference with model chat templates, configurable context, temperature, top-p, top-k and max generation length.
- DataStore settings and editable system prompt.
- Local-only privacy design: no analytics, ads or trackers; Android cloud backup/device transfer disabled for Pocket AI data.
- Friendly handling at the Kotlin boundary for invalid files, model-load failures, OOM and cancellation. Generation is cancelled when the Activity goes to the background.

## Native engine / reproducibility

`llama-android` is Pocket AI's JNI layer. Its CMake file pins upstream **llama.cpp v0.5.0** with CMake `FetchContent`. On the first native build, CMake therefore downloads that exact upstream source. The resulting APK contains native llama.cpp code; inference itself needs no internet. This avoids checking hundreds of megabytes of third-party upstream history/source into this project while keeping the engine version reproducible.

The build targets `arm64-v8a` and uses portable runtime CPU behavior rather than a global Snapdragon-specific `-march`. The upstream Android implementation recommends portable cross-compilation and warns against global `-march` unless deliberately raising the baseline. Pocket AI currently uses CPU inference (`gpuLayers=0`) as the reliable fallback. Upstream llama.cpp now has Snapdragon backend work for CPU/Adreno/Hexagon, but it is not enabled here because the official Android GUI binding's portable CPU variant is the safer baseline for an installable app. Hardware-specific acceleration can be added after device validation.

## Requirements

- Android Studio with JDK 17 support.
- Android SDK 36.
- Android NDK `29.0.14206865`.
- CMake 3.31.6 and Ninja (Android Studio SDK Manager can install these).
- Internet access **while building for the first time** so Gradle dependencies and pinned llama.cpp v0.5.0 can be fetched.
- Target device Android 13+ (`minSdk 33`); arm64 device required by this build.

## Build

1. Open the `PocketAI` folder in Android Studio.
2. Let Gradle sync. Install missing SDK 36 / NDK / CMake components if Android Studio asks.
3. Build **Build > Make Project**.
4. For a debug APK run `./gradlew assembleDebug` (or use Android Studio's Build APK action).
5. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

### Important wrapper note

This generated archive includes wrapper scripts and wrapper properties, but this execution environment could not fetch the binary `gradle-wrapper.jar`. Android Studio can import/sync the project with its Gradle tooling; alternatively generate the wrapper once with a local Gradle 8.13 installation (`gradle wrapper --gradle-version 8.13`) and then use `./gradlew`.

## Install on Galaxy S25 Ultra

Enable developer options and USB debugging, connect the phone, then run `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Alternatively copy the APK to the phone, allow “Install unknown apps” for the file manager you use, tap the APK and install it. Android may warn because a debug APK is not Play-signed.

## Import a GGUF model

On first launch tap **Import GGUF model**, choose a `.gguf` document from Android's system picker, and wait while it is copied to Pocket AI's private `files/models` directory. The source file remains untouched. In **Models**, select and load it. Loading may take time and Android may kill a process that exceeds available RAM.

Pocket AI deliberately does not silently download models in v0.1. Download a GGUF yourself from a trusted publisher/repository and explicitly import it. This makes multi-gigabyte transfers visible and user-controlled.

## Model size guidance for a 12 GB S25 Ultra

These are planning ranges, not measured S25 Ultra performance. Actual RAM includes model weights, KV cache, native buffers, Android and the rest of the app.

| Class | Practical quantization | Approx model file | Practical note |
|---|---|---:|---|
| 1B–2B | Q4_K_M / Q4_0 | ~0.8–1.5 GB | Best starting point; lots of RAM headroom. |
| 3B–4B | Q4_K_M | ~2–3 GB | Good phone-size balance; 4096 context is a sensible start. |
| 7B–8B | Q4_K_M | ~4.5–5.5 GB | Plausible on 12 GB, but leaves much less headroom; avoid huge contexts. |

Current examples worth investigating include Google Gemma 3 1B IT QAT Q4_0 and current Qwen small instruct GGUF releases. Prefer an **instruction/chat-tuned** GGUF that embeds a supported chat template. Check each model's license before redistribution or commercial use. Do not assume token/s speed until benchmarked on the actual S25 Ultra.

## Troubleshooting

**“Invalid GGUF”** — choose an actual `.gguf` file. Pocket AI validates the GGUF magic before registering the copy.

**Model cannot be loaded / app closes** — the model may be corrupt, unsupported, or too large for available RAM. Close memory-heavy apps, start with a 1B–4B Q4 model, and keep context at 4096 or lower. Android can terminate a process before Kotlin can catch an OOM, so no app can guarantee recovery from every native allocation failure.

**CMake cannot fetch llama.cpp** — verify build-machine internet access and Git availability. The runtime phone does not need this once the APK is built.

**No useful response / odd formatting** — verify the GGUF is an instruct/chat model with a llama.cpp-supported embedded chat template.

**Generation stops when switching apps** — intentional in v0.1. Pocket AI cancels active generation on `Activity.onStop()` to avoid uncontrolled background CPU use.

**Changing context while a model is loaded** — unload and reload the model; context is allocated at model-context creation time.

## Privacy

Room stores conversations/messages and the model catalog locally. Preferences DataStore stores the system prompt and sampling settings locally. Imported GGUFs are copied into app-private storage. `allowBackup=false` plus data-extraction exclusions prevent Pocket AI's app data from being included in normal Android cloud backup/device transfer. There are no analytics, advertising SDKs or trackers. The manifest includes `INTERNET` only to leave room for a future explicit model-download feature; current normal inference and chat persistence do not make network calls.

## Known v0.1 limitations

- CPU path is the supported default; Snapdragon Adreno/Hexagon acceleration is intentionally not claimed without S25 Ultra validation.
- The model catalog stores file size and name. Rich GGUF metadata parsing can be expanded using upstream `GgufMetadataReader`.
- “Regenerate” currently generates another assistant turn from the most recent user message rather than replacing the prior assistant database row.
- No built-in model downloader yet; this is intentional to avoid unexpected multi-GB network use.

## Upstream references checked for this build

- llama.cpp `docs/android.md` and `examples/llama.android` (official Android binding and build guidance).
- llama.cpp `include/llama.h` and `examples/simple-chat/simple-chat.cpp` (chat templates, sampling and decode flow).
- AndroidX DataStore release documentation (Preferences DataStore 1.2.1).
