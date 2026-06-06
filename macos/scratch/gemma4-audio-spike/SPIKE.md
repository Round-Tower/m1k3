# Spike — Gemma 4 E4B native-audio transcription

**Status:** scaffolded 2026-06-06 · not yet run (needs a device session — MLX runs
only from a bundled `.app`, never CLI/`swift test`). This is the plan + design so
the device session is turnkey.

**Goal:** decide whether **Gemma 4 E4B** native audio should replace (or sit
alongside) WhisperKit behind M1K3's `TranscriptionProvider` seam — and whether its
native diarization is good enough to collapse the Phase-7 calls stack into one model.

---

## Why now

Gemma 4 shipped 2026-06-03 (post-cutoff). The **edge variants** are the interesting
ones for shipping in-app:

| Variant | Eff. params | Audio | Notes |
|---|---|---|---|
| E2B | ~2B | native in | phone-class memory |
| **E4B** | ~4B | native in | **spike target** — audio encoder 50% smaller than Gemma 3n, **40ms frame tuned for low-latency ASR**, 128K ctx |
| 12B | 12B | native in | too heavy for the mic button (~16GB unified) |

E4B is ~the footprint of the `gemma-3-1b-qat-4bit` we already run for chat, rides the
**same `mlx-swift-lm` runtime + the model-download-UX** built 2026-06-06, and does
**ASR + speaker diarization** natively. The dream: one model for chat-gen + ASR +
diarization + summary → drop WhisperKit **and** FluidAudio.

## Two runtime paths (both verified to exist; neither yet wired)

1. **MLX-Swift** — `VincentGourbin/gemma-4-swift-mlx` (native text+vision+audio+video
   on Apple Silicon via MLX Swift). We already depend on `mlx-swift-lm`; check whether
   it (or this repo) exposes a Gemma 4 **audio** config + a streaming decode loop.
   *Preferred* — stays in the runtime we know, reuses the download-UX.
2. **LiteRT-LM** — `litert-community/gemma-4-12B-it-litert-lm` on HF (text+audio now,
   image later). This is the dormant P3 LiteRT path finally having a reason. Heavier
   integration (C++/sidecar), but Google's first-party audio route.

## Integration design (drops into the shipped seam)

The Phase-6 seam (committed) is exactly the plug point. A `GemmaAudioTranscriber`
conforms to `TranscriptionProvider` (in `M1K3Voice`) and lives in a new isolated
target `M1K3GemmaAudio` (mirrors `M1K3WhisperKit`/`M1K3MLX` — only it + the app link
the heavy model). Then:

```swift
// AppEnvironment wiring becomes:
transcription = TranscriptionRouter(providers: [gemmaAudio, whisperKit, AppleSpeechTranscriber()])
// gemmaAudio.isAvailable == false until its model loads → router falls through to
// WhisperKit/AppleSpeech. No other code changes (mic button, ticker, accumulator,
// auto-send all unchanged). Promotion = it reports available + wins the benchmark.
```

`prepareModel(progress:)` reuses the same download-UX pattern as `MLXGemmaProvider`
and `WhisperKitProvider`. Live partials: yield cumulative text (the
`TranscriptAccumulator` contract) so nothing downstream changes.

See `GemmaAudioTranscriber.swift` in this dir for the skeleton.

## Benchmark protocol (the decision gate)

Run **on device** (⌘R), same audio inputs through each provider:

| Metric | How | WhisperKit (base.en) | Gemma 4 E4B |
|---|---|---|---|
| **Live latency** | time from speech-end → final segment; partial cadence | | |
| **WER** (accuracy) | a fixed read-aloud script vs ground truth | | |
| **Diarization DER** | a 2-speaker clip; compare to hand-labelled turns | n/a (FluidAudio) | |
| **Model size / load** | download MB + cold/warm load time | | |
| **Peak memory** | Activity Monitor during a 60s session | | |

Reuse `audio_samples/` (repo root) for fixed inputs; capture numbers in a results
table appended here.

## Decision gate

- **Promote to default for P6** only if E4B matches WhisperKit on WER **and** is within
  ~1.5× on live latency (the mic button must feel instant).
- **Fold P7 diarization into Gemma 4** only if DER is competitive with FluidAudio on a
  real 2-party clip. Otherwise keep diarization separate (the prior call-pipeline lift) and use Gemma 4
  only for ASR + summary.
- **If it loses:** keep it behind the seam at `isAvailable=false`; no harm, no rip-out.
  Run a `challenger` pass before any "unify on one model" commitment — unifying
  concentrates ASR + chat + calls risk in a single model's maturity.

## Risks

- Days-old; MLX-Swift audio support is a fresh community impl, LiteRT audio is v1.
- **Streaming-partial API unproven** for Gemma 4 in Swift — WhisperKit's is mature. If
  E4B only does batch (not low-latency partials), it's a P7 tool, not a P6 one.
- MLX metallib boundary: verify only by launching the app, not `swift test`/`swift run`.

## Next actions (device session)

1. Add `gemma-4-swift-mlx` (or confirm `mlx-swift-lm` Gemma-4-audio support); resolve.
2. Flesh out `GemmaAudioTranscriber` against the real audio decode API.
3. New `M1K3GemmaAudio` target; wire as the first provider in the router (`isAvailable`
   gated on model-loaded).
4. ⌘R, load E4B, run the benchmark table above. Record results here.
5. Decide per the gate. If promising → re-plan P7 with `challenger`.
