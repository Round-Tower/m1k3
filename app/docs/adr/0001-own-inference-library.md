# 0001. Build Our Own KMP Inference Library

Date: 2026-04-05
Status: ACCEPTED
Deciders: Kev Murphy, Claude

/**
 * Signed: Kev Murphy + Claude claude-opus-4-6, 2026-04-05
 * Format: MurphySig v0.1 (https://murphysig.dev/spec)
 *
 * Context: The hardest architectural decision in M1K3 to date. We're choosing
 * to build our own KMP inference library wrapping llama.cpp rather than
 * depending on Llamatik (stale llama.cpp, unfixed bugs) or Google's stack
 * (AICore unsupported on Pixel 9a, LiteRT-LM locks us out of GGUF).
 * This is the "own the stack" call — same model file everywhere, update
 * llama.cpp same-day, fix streaming properly, add whisper/SD when ready.
 *
 * Confidence: 0.82 - HIGH on the decision itself (the alternatives are worse).
 *   MEDIUM on execution timeline — JNI bridge surface is small (~4 functions)
 *   but NDK/CMake cross-compilation has unknowns. llama.cpp C API has been
 *   evolving and we'll need to track breaking changes across versions.
 *
 * Open: What's the right module structure? Separate repo or monorepo?
 *   How do we handle llama.cpp version pinning vs rolling forward?
 *   CI pipeline for cross-compiling native .so on GitHub Actions?
 *   Name for the library?
 */

## Context

M1K3 is a privacy-first local AI assistant that runs LLM inference entirely on-device. We currently depend on **Llamatik** (`com.llamatik:library:0.18.2`), a third-party KMP wrapper around llama.cpp, for GGUF model inference on Android.

We've hit a wall. Llamatik bundles **llama.cpp b7815** (January 2025) and hasn't updated it in 15 months despite 5 library releases. We need **b8637+** (April 2, 2026) for Gemma 4 support. The maintainer is shipping new features (whisper, stable-diffusion, WASM) but not updating the inference core.

We've also accumulated significant workaround code for Llamatik bugs:

- **UTF-8 streaming crash**: All streaming APIs (`generateStream`, `generateWithContextStream`) crash with `JNI DETECTED ERROR` on multi-byte UTF-8 sequences. We simulate streaming via word-by-word emission of non-streaming output. This is *architectural* — the JNI bridge calls `NewStringUTF` per token with partial multi-byte sequences.
- **Silent init failures**: `initGenerateModel()` logs errors but doesn't throw, leaving `isInitialized = true` with a dead native context.
- **Null JNI returns**: `generate()` returns Java `null` without Kotlin nullability, causing NPEs in downstream code. We patched `stripStopTokens` to be null-safe.

Meanwhile, our needs are growing:
- Gemma 4 GGUF support (requires modern llama.cpp)
- Proper streaming (not simulated)
- Cross-platform: Android now, iOS next, desktop eventually
- Future: whisper.cpp for STT, stable-diffusion.cpp for image gen

### Forces at play

1. **M1K3 runs on Pixel 9a** — AICore/ML Kit Prompt API is NOT supported on 9a (only 8GB RAM, not in Google's device list). LlamaCpp is our *only* inference path on this device.
2. **Cross-platform is non-negotiable** — same GGUF model file must work on desktop (Python M1K3) and mobile (KMP). Rules out LiteRT-LM (proprietary `.task` format).
3. **We need to move fast** — Gemma 4 is here now, new models drop constantly. Can't wait months for upstream releases.
4. **Two-person team** — Kev + Claude. We have the bandwidth to build and maintain this if it's focused.

## Decision

We will build **our own KMP inference library** wrapping llama.cpp directly, rather than depending on or forking Llamatik.

The library will:
- Provide a thin, correct JNI bridge for Android (~200-300 lines of C)
- Cross-compile llama.cpp via CMake + Android NDK (arm64-v8a, x86_64)
- Fix streaming properly from day one (buffer complete UTF-8 codepoints before calling `NewStringUTF`)
- Use llama.cpp's stable C API with proper error handling and null safety
- Live inside the M1K3 monorepo initially (not a separate published library)
- Add whisper.cpp and stable-diffusion.cpp modules when we need them
- Support iOS via cinterop when we build the iOS target

## Consequences

### Positive
- **Same-day model support** — update llama.cpp to any tag, rebuild, ship
- **Proper streaming** — fix the UTF-8 JNI crash architecturally, not with workarounds
- **Full control** — no inherited bugs, no waiting on upstream, no workaround code
- **Right-sized** — carry only what we use, add capabilities when needed
- **GGUF universality preserved** — same model file works everywhere (desktop, mobile, server)
- **Foundation for whisper/SD** — same JNI/cinterop pattern extends to whisper.cpp and stable-diffusion.cpp

### Negative
- **We own the maintenance** — CMake toolchain, NDK updates, llama.cpp API changes are our responsibility
- **Initial build complexity** — NDK cross-compilation, CI pipeline for native libs
- **No community** — we're not riding an existing project's momentum
- **Effort** — real engineering work to set up correctly, even if the JNI surface is small

### Neutral
- Llamatik remains available as a fallback if our approach fails
- We can contribute learnings back to the llama.cpp community (Android build improvements, JNI patterns)
- The library could be open-sourced later if others find it useful

## Alternatives Considered

### Fork Llamatik, bump llama.cpp, PR upstream
- Pros: Get whisper/SD/WASM "for free", community exists, Maven Central pipeline
- Cons: Inherit 15 months of API drift in JNI bridge, unfixed architectural bugs (UTF-8 streaming), maintain code we didn't write and don't fully trust
- Why rejected: Forking means maintaining someone else's tech debt. The JNI bridge rewrite work is similar to writing our own, but with inherited complexity. The features we'd "get for free" (whisper, SD, WASM) are tied to equally old submodule versions.

### LiteRT-LM (Google's on-device LLM SDK)
- Pros: NPU access via QNN delegate, first-party Gemma optimization, streaming support
- Cons: Proprietary `.task` format (no GGUF), no desktop support, no KMP module, Google ecosystem lock-in
- Why rejected: Breaks cross-platform GGUF universality — same model file must run on desktop M1K3 and mobile. No path to iOS via shared code.

### AICore / ML Kit GenAI
- Pros: Zero inference code needed, Google manages the runtime
- Cons: Pixel 9a not supported (8GB RAM, not in device list), Google-controlled model selection, no GGUF
- Why rejected: Our primary test device (Pixel 9a) is not supported. Device-limited, format-locked.

### Stay on Llamatik 0.18.2
- Pros: Zero effort, works today for Gemma 3
- Cons: No Gemma 4, no proper streaming, accumulating workarounds, dependent on single maintainer
- Why rejected: Untenable long-term. We're already writing more workaround code than integration code.

### Use java-llama.cpp (`de.kherud:llama`)
- Pros: Java bindings, Maven artifact
- Cons: Last release June 2025, not KMP, no iOS path
- Why rejected: Same version lag problem as Llamatik, without KMP support.

## References

- [llama.cpp Gemma 4 support PR #21309](https://github.com/ggml-org/llama.cpp/pull/21309) (merged April 2, 2026)
- [llama.cpp Android build docs](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)
- [Llamatik repo](https://github.com/ferranpons/Llamatik) (llama.cpp pinned at b7815/091a46c)
- [SESSION_LLAMATIK_UTF8_FIX.md](../SESSION_LLAMATIK_UTF8_FIX.md) — UTF-8 JNI crash analysis
- [SESSION_GEMMA3_LLAMATIK_FIX.md](../SESSION_GEMMA3_LLAMATIK_FIX.md) — Chat template issues
- M1K3 memory: [project_gemma4_inference_research.md](research findings)
