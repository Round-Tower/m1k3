# Spike — Gemma 4 E4B native-audio transcription

**Status:** investigated 2026-06-06 (desk research — Kev away until Tue; MLX can't
run headless anyway). **Verdict reached without a device run** by reading the real
runtime source. Device benchmark still wanted, but the *lane* is now decided.

**Question:** should Gemma 4 E4B native audio replace/augment WhisperKit behind
M1K3's `TranscriptionProvider` seam, and can its native diarization collapse Phase 7?

---

## VERDICT (evidence-backed)

**Gemma 4 audio is BATCH, file-based, ≤30s — not a live-streaming engine.**
→ **It does NOT fit P6 (live dictation). WhisperKit + Apple Speech stay on the mic button.**
→ **It DOES fit P7 (batch call transcription):** chunk a recording into ≤30s windows →
transcribe each → same model can then summarise. Diarization would be prompt-driven
("label speakers"), **unverified** — that's the one thing to benchmark on device.

This **corrects my earlier over-claim** ("E4B plausibly viable for P6 too"). The
40ms-frame "low-latency ASR" headline does *not* mean a streaming Swift API exists
today. It doesn't. Honest course-correction logged.

## The evidence (read from source, 2026-06-06)

**Our pinned `mlx-swift-lm` (2.30.6) has NO Gemma 4 audio.** It ships Gemma 3n
**E4B/E2B text-only** (`gemma3n_E4B_it_lm_*` in `LLMModelFactory`) — no audio tower.
Libraries present: MLXLLM, MLXLMCommon, MLXEmbedders, MLXVLM (vision) — **no audio**.

**The audio path is the community `VincentGourbin/gemma-4-swift-mlx`** (`Gemma4Swift`
library product, Swift 6, macOS 14+, Apple Silicon). What its source actually shows:
- Models: `mlx-community/gemma-4-e4b-it-4bit` (and e2b, 8/6/bf16). E2B/E4B have the
  audio tower; 26B/31B do **not**.
- `Gemma4Pipeline.chat(prompt:)` / `chatStream(prompt:)` are **TEXT-ONLY** — no audio
  parameter. Audio is **not** on the convenient API.
- Audio runs through the **low-level multimodal path**: `AudioProcessor.processAudio(
  url:maxDurationSeconds: 30)` → log-mel (16kHz, vDSP FFT) → Conformer encoder →
  `Gemma4Processor` expands `<|audio|>` tokens (boa + audio_token×N + eoa) →
  `MultimodalEmbedder` scatters audio features into the sequence → multimodal generate.
- Hard cap: **30s / 480K samples @ 16kHz** ("limite du modele", per the source).

## Integration risks (for Tuesday — do NOT bolt onto the main package blind)

1. **Version conflict.** `Gemma4Swift` wants `mlx-swift-lm`@**main** + `mlx-swift`
   ≥0.31.3; M1K3 is pinned `mlx-swift-lm` 2.30.6 / `mlx-swift` 0.30.6. Adding it to the
   main package could bump those for our **working** `MLXGemmaProvider`/`MLXEmbeddingService`
   and break them. → **Prototype in an ISOLATED package first** (this dir), don't add the
   dep to `macos/Package.swift` until proven compatible.
2. **Low-level wiring.** No `chat(audio:)` — you drive `processAudio` + the multimodal
   model directly (see the CLI's audio path for the reference call). More than "call chat".
3. **MLX boundary.** `xcodebuild` not `swift build` (Metal); runs only from a bundled
   `.app`. Same wall as our MLX gen.

## Revised design — a BATCH transcriber (fits P7, not the live seam)

The shipped seam is `startListening() -> AsyncStream` (live). Gemma 4 audio is batch, so
it wants a **different method**: `transcribe(fileURL:) async throws -> [TranscriptSegment]`
(the prior call-pipeline's protocol had exactly this). Two clean options for Tuesday:
- **A — extend the seam:** add an optional `transcribeFile(_:)` to `TranscriptionProvider`
  (default-throws), implemented by a batch `GemmaAudioTranscriber`. WhisperKit/Apple keep
  live; Gemma 4 serves files/calls.
- **B — separate `BatchTranscriptionProvider` protocol** for the P7 call pipeline; keep the
  live `TranscriptionProvider` untouched. Cleaner separation; my lean.

See `GemmaAudioTranscriber.swift` (sketch, batch design against the real API).

## Benchmark protocol (P7 framing, device session)

On a recorded **≤30s 2-speaker clip** (use `audio_samples/`):

| Metric | WhisperKit + FluidAudio | Gemma 4 E4B (4-bit) |
|---|---|---|
| **WER** (accuracy) | | |
| **Diarization DER** (the deciding metric) | FluidAudio | prompt-driven — *does it even work?* |
| Transcribe latency for 30s | | |
| Model size / load / peak mem | | |
| Bonus: can it transcribe + summarise in one pass? | n/a | |

## Decision gate

- **Fold P7 onto Gemma 4** only if WER is competitive **and** prompt-driven diarization DER
  is within range of FluidAudio. The summarise-in-the-same-model bonus is the tiebreaker.
- **Else:** keep the prior call-pipeline's lift (WhisperKit batch + FluidAudio + AFM/Gemma summary) for P7;
  shelve Gemma 4 audio behind the seam at `isAvailable=false`.
- Either way: **P6 stays on WhisperKit.** Run a `challenger` pass before any "one model for
  calls" commitment.

## Next actions (Tuesday, device)

1. Isolated package here → add `Gemma4Swift`, `swift package resolve` (confirm it fetches
   without forcing incompatible mlx versions on the main app — keep it ISOLATED).
2. Flesh out the batch `GemmaAudioTranscriber` against `processAudio` + the multimodal model.
3. ⌘R-equivalent / xcodebuild the spike; transcribe a fixed clip; fill the table.
4. Decide per the gate; if promising, re-plan P7 (one model) with `challenger`.
