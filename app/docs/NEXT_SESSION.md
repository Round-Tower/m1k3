# Next Session — 2026-04-06 (Easter Monday)

## Completed This Session

### Infrastructure
- **Ma library** (`9c1e21e5`) — replaced Llamatik entirely. Own JNI bridge wrapping llama.cpp's stable C API. True token streaming, UTF-8 safe, handle-based, no global state. `libma.so` ships in APK (arm64-v8a + x86_64). llama.cpp as git submodule at `app/composeApp/src/androidMain/cpp/llama.cpp`.
- **16KB page alignment** (`a48d9e62`) — suppresses Android 15 `PageSizeMismatchDialog` for `libma.so`. (Other libs like libfilament still need upstream fixes.)

### Onboarding
- **Mini/Lil/Big M1K3 tiers** (`a7eed65c`) — hardware-matched tiers with awakening screen, dormant avatar, fact carousel, orange progress bar. `M1K3Tier.forDevice(DeviceTier)` domain function. `OnboardingViewModel` (commonMain, KMP-safe). `PreferenceKeys.ONBOARDING_COMPLETE` + `SELECTED_M1K3_TIER` persist post-download.
- **WorkManager download** (`dfe654e3`) — `ModelDownloadWorker` (foreground service) survives screen lock, Doze, backgrounding. Notification shows `%` progress. `ExistingWorkPolicy.REPLACE` so retry works.
- **Proper resume** (`b50db559`) — was truncating `.tmp` with `outputStream()` even on Range resume. Fixed to `FileOutputStream(tempFile, isResume)` (append mode). `REPLACE` policy so `retryDownload()` actually restarts failed jobs.
- **WorkManager reconnect** (`ac07caea`) — observe by **work name** (`getWorkInfosForUniqueWorkFlow`) not UUID. `KEEP` was losing reference to the live job; now always resolves correctly.

### Model Selection
- **Engine loads correct tier** (`df7dc643`) — `single<BaseLlmEngine>` now reads `SELECTED_M1K3_TIER` preference and loads the right model with `overrideModelPath` from `ModelDownloadManager`.
- **Qwen3 tiers, fix 401 gating** (`86dcac6e`) — All Gemma 3 bartowski repos return HTTP 401 (Google gating on community quantizations). Live verified. Replaced with:
  - Mini: **Qwen3.5-0.8B Q4_K_M** (~557MB) — `bartowski/Qwen_Qwen3.5-0.8B-GGUF` (March 2026)
  - Lil: **Qwen3.5-2B Q4_K_M** (~1.33GB) — `bartowski/Qwen_Qwen3.5-2B-GGUF` (March 2026, ≈ Qwen2.5-7B)
  - Big: Gemma 4 E2B unchanged — `unsloth/gemma-4-E2B-it-GGUF` (April 2026)
  - All three natively multimodal, all ChatML, all public. ADR: `docs/adr/0003-model-tiers-no-hf-auth.md`

### Navigation & UX
- Avatar Gallery wired to drawer primary nav (`4c64ea0f`)
- `isModelDownloaded` dead code removed (`4c64ea0f`)
- Globe/RubinGlobe zero-allocation rewrite already committed separately

### Context
- Weather context + `MaSystemPromptBuilder` (`1f503270`) — on-device weather feeds into system prompt. M1K3 knows it's raining in Dublin without being asked.

---

## Current Device State (Pixel 9a `59021JEBF12282`)

- **Big M1K3 download IN PROGRESS** — Gemma 4 E2B `.tmp` file was growing at ~3.5MB/s when device went offline. WorkManager will resume automatically on reconnect.
- **APK installed:** `86dcac6e` build (latest) — BUT the Qwen3 model changes mean the device will show `selected_m1k3_tier = "big"` from the previous onboarding, which correctly maps to Gemma 4 E2B. This is fine.
- **Onboarding:** `onboarding_complete = true` was set during the Big M1K3 download. On app relaunch, Chat screen loads directly. Engine will try to load Gemma 4 E2B from `files/models/`.
- **Battery was at ~16%** — hopefully plugged in overnight.

---

## Platform Roadmap Decided (Apr 2026)

**Android → iOS → macOS → Web** (KMP throughout)

**iOS strategy confirmed:**
- *Free/System tier*: Apple Foundation Models (iOS 18+, zero download, instant)
  - Uses `FoundationModels.framework` via `OnDeviceAi` interface (already stubbed)
  - Compose Multiplatform renders the same UI; shared ViewModels just work
- *Optional upgrade*: Any M1K3 GGUF tier via Ma/cinterop (same llama.cpp, same models)
  - `cinterop` wraps same `llama.h` C header we already use for Android JNI
  - Pixel 9a and iPhone can run identical GGUF weights
- **Ship iOS fast with Foundation Models; add cinterop for model parity later**

**macOS**: Compose for Desktop target already in build (`jvmMain`, `MainKt` entry point).
Ma needs a macOS CMake build (trivial; Metal acceleration on M-series is free).

**Ma as a platform** (not just inference):
- `whisper.cpp` → proper on-device STT (replaces AndroidSttEngine cloud fallback)
- Same pattern: new `WhisperBridge.kt` + `whisper_bridge.cpp`, same CMake
- Gives iOS, Android, and Mac the same STT quality with zero cloud

## Next Up (Priority Order)

1. **Verify first Gemma 4 E2B inference** — once download completes, launch app, send first message, check logs for `MaBridge init: success` and `LlamaCppEngine Initialization complete`. The response quality will be dramatically different from 270M era.

2. **Deploy Qwen3 APK to device** — the `86dcac6e` APK has Qwen3 as default but the device still has old APK. Flash it: `./gradlew :composeApp:assembleDebug -x copyWebAvatarToAndroid && adb install -r ...`

3. **First-chat UX** — after a successful inference, review:
   - Does the Rubin globe dim correctly during generation?
   - Does streaming look smooth (true token-level now, not word-split)?
   - Does TTS fire on completion with `autoVoiceReply = true`?

4. **Handle `NoModelAvailableException` in Chat UI** — currently shows generic engine error. Should show "Model not installed" with a CTA to re-trigger download. `ChatScreenViewModel` already catches it as `ChatError.ModelError`; just needs a dedicated UI state.

5. **Onboarding: skip download if model already on disk** — `OnboardingViewModel.startDownload()` doesn't check `isModelAvailable` before downloading. If user already has the model (e.g., re-running onboarding after a reinstall), should show `Complete` immediately.

6. **PageSizeMismatch for libfilament / libggml** — still shows on first launch. These come from third-party deps (Filament, llama.cpp). Requires upstream fixes or `-Wl,-z,max-page-size=16384` on those build targets. Not blocking but worth filing upstream.

---

## Gotchas & Blockers

- **Gemma 3 models: 401.** `bartowski/gemma-3-*-GGUF` repos require HF auth. Do not re-add these as tier models. Qwen3 is the replacement. Gemma variants still in `LlmModel` as legacy references only.

- **Big M1K3 download may have restarted** after device went offline. With the append-mode fix (`b50db559`) the `.tmp` file will resume correctly — the Range header sends `bytes=N-` and we append. Check file size on reconnect.

- **`am force-stop` kills WorkManager.** Don't use force-stop during active downloads. Use `am start` to bring app to foreground instead.

- **Transport ID changes** — ADB transport ID (`-t N`) changes when device reconnects. Always run `adb devices -l` to get current transport_id. Device serial is `59021JEBF12282` (stable).

- **Web avatar npm build** — `buildWebAvatar` task fails (rollup native module issue). Always use `-x copyWebAvatarToAndroid -x buildWebAvatar -x installWebAvatarDeps` flags on all Gradle commands.

- **13 pre-existing test failures** in `GenerationConstantsTest` / `GenerationConfigBuilderTest` — `TokenLimits` constants are all `0` (deferred to `getOptimalMaxTokens()`). Tests expect hardcoded values. Not ours, predates this session.

- **Pixel 9a is Lil M1K3 tier** (8GB → HIGH_END → Big M1K3). For testing Mini/Lil tiers locally, use `deviceRamGbOverride` in `LlamaCppEngine` constructor.

---

## Key Files Modified This Session

| File | Change |
|------|--------|
| `app/composeApp/src/androidMain/cpp/CMakeLists.txt` | 16KB page alignment |
| `app/composeApp/src/androidMain/cpp/ma_bridge.cpp` | C++ JNI bridge (llama.cpp) |
| `app/composeApp/src/androidMain/kotlin/.../ai/ma/MaBridge.kt` | Kotlin JNI declarations |
| `app/composeApp/src/androidMain/kotlin/.../ai/LlamaCppEngine.kt` | Uses MaInferenceBackend, deviceRamGbOverride |
| `app/composeApp/src/androidMain/kotlin/.../ai/download/HttpModelDownloadManager.kt` | Append mode resume, Qwen3 URLs |
| `app/composeApp/src/androidMain/kotlin/.../ai/download/ModelDownloadWorker.kt` | WorkManager foreground service |
| `app/composeApp/src/androidMain/kotlin/.../di/PlatformModule.android.kt` | Tier→model mapping, WorkManager DI |
| `app/composeApp/src/androidMain/kotlin/.../ui/OnboardingScreen.kt` | 3-step awakening UI |
| `app/composeApp/src/commonMain/kotlin/.../onboarding/OnboardingViewModel.kt` | Download orchestration |
| `app/shared/src/commonMain/kotlin/.../ai/LlmModel.kt` | Qwen35_0B8, Qwen35_2B added, default = Qwen35_2B |
| `app/shared/src/commonMain/kotlin/.../ai/M1K3Tier.kt` | Mini→Qwen3.5-0.8B, Lil→Qwen3.5-2B |
| `app/docs/adr/0002-no-bundle-gemma3-1b-default.md` | ADR: no-bundle strategy |
| `app/docs/adr/0003-model-tiers-no-hf-auth.md` | ADR: Qwen3 tier selection |

---

## Continuation Prompt

> We're building M1K3 — a privacy-first on-device AI assistant for Android (KMP/Compose). Today was a huge session: we shipped the Ma inference library (replaced Llamatik with our own llama.cpp JNI bridge), the Mini/Lil/Big M1K3 onboarding experience, WorkManager-backed downloads with proper resume (append mode), and fixed a 401 HuggingFace gating issue that took down Mini and Lil tier downloads — replaced with Qwen3-0.6B (Mini, 484MB) and Qwen3-1.7B (Lil, 1.28GB). Big M1K3 uses Gemma 4 E2B.
>
> The Pixel 9a (`59021JEBF12282`) is currently downloading Gemma 4 E2B via WorkManager. When the download completes, we'll get our first real inference from a 2B-class model on this hardware — a huge quality jump from the 270M era.
>
> Latest master: `86dcac6e`. APK not yet flashed with the Qwen3 changes. Check device download state with: `adb devices -l` then `adb -t {id} shell "run-as app.m1k3.ai.assistant ls -lh files/models/"`.
>
> Priority today: (1) verify Big M1K3 inference on device, (2) flash Qwen3 APK, (3) handle `NoModelAvailableException` gracefully in Chat UI with a re-download CTA, (4) review first-chat streaming quality.
