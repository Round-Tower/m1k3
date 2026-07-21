# K1 spike — MLX-Kokoro vs the ONNX path

Ran: 2026-07-18. Gate for: dropping onnxruntime from M1K3's TTS (the visionOS
unlock). Prior: K0 scout verdict FEASIBLE-NOW (see PLAN-DRAFT.md).

## What ran

- Cloned **Blaizzy/mlx-audio-swift** (MIT, pushed 2026-07-17) to session scratch;
  built its stock `mlx-audio-swift-tts` executable with `swift run -c release`
  (231s clean build, warnings only).
- Synthesized one bm_daniel sentence via `mlx-community/Kokoro-82M-bf16`
  (auto-downloaded by its HubApi path to ~/.cache/huggingface — NOT the app
  container; the 07-16 cache-poison trap does not apply).

## Results

| Gate | Outcome |
|---|---|
| Audio produced | **YES** — 10.57s WAV, `kokoro-mlx-bm_daniel.wav` (sent to Kev) |
| Speed | RTFx **1.30** (M-series, bf16, first run, non-streaming; TTFB 8.1s = whole utterance — the tool has no streaming mode) |
| Memory | Peak 2.49G / active 315M / cache 250M |
| Dep-graph fit | **CLEAN on paper**: it pins `mlx-swift-lm ≥3.31.3 <4` — the SAME family M1K3 pins (upToNextMinor 3.31.4); `mlx-swift ≥0.30.6 <1` co-resolves |
| visionOS compile | **BUILD SUCCEEDED** — `xcodebuild -scheme MLXAudioTTS -destination 'generic/platform=visionOS Simulator'`, stock manifest, NO platforms patch needed (unlisted platforms get default minimums) |
| Quality (ear) | **KEV'S CALL** — A/B staged: M1K3 spoke the sentence live via ONNX (MCP `speak`) + the MLX wav delivered |

## Gotchas (real, will bite the integration)

- **The metallib wall applies to SwiftPM CLI runs of mlx-audio-swift**: `swift
  run` produced NO Cmlx metallib bundle → `MLX error: Failed to load the default
  metallib` (mlx-c memory.cpp:69), exit 255. Fix used: copy a Mac
  `mlx-swift_Cmlx.bundle` (from any Xcode-built DerivedData) beside the release
  binary. Inside the M1K3 .app this is a non-issue (Xcode builds the bundle),
  same as the existing MLX brains.
- TTFB = full-utterance latency (no streaming synth in the tool). M1K3's current
  ONNX path chunks via SpeechChunker — integration keeps our chunker and calls
  the MLX synth per chunk, so felt latency ≈ first-chunk time, not 8s.
- Peak 2.49G during synth is additive with a loaded brain — thermal/RAM
  etiquette wants the same backgroundWorkAllowed() gating the warms use.

## Verdict

**PASS** on every machine-checkable gate (audio ✓ · dep fit ✓ · visionOS
compile ✓); quality is Kev's ear on the staged A/B. Recommended adoption per PLAN-DRAFT.md call #3: vendor the
MIT `StyleTTS2/` sources (or depend on MLXAudioTTS) behind M1K3Kokoro's
existing `SpeechProvider` seam — G2P + SpeechChunker stay ours; only the
`infer()` seam swaps. The weight-format shim (safetensors + per-voice .npy vs
our staged ONNX + voices-v1.0.bin) is the one real integration task K0 named.

_Signed: Kev + claude-fable-5, 2026-07-18 (K1 spike), Confidence 0.85 (synth
measured on this Mac from the stock tool; dep fit read from both manifests;
quality deliberately left to Kev's ear; visionOS compile pending at write
time — addendum carries it). Prior: none (new spike)._
