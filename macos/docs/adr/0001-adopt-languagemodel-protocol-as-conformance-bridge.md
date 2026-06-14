# 0001. Adopt the WWDC26 `LanguageModel` protocol as a conformance bridge, not a rebuild

Date: 2026-06-14
Status: ACCEPTED
Deciders: Kev + claude-opus-4-8

## Context

WWDC26 (macOS 27 "Golden Gate") shipped a public `LanguageModel` protocol in the
`FoundationModels` framework: any model — on-device, local-GPU, or cloud — conforms to a
common Swift surface (`LanguageModel` + `LanguageModelExecutor.respond(to:model:streamingInto:)`
+ a streaming channel), and apps swap providers via SPM with no session-code changes. Apple
also announced open-source `MLXLanguageModel` / `CoreAILanguageModel` adapters, multimodal
image input, Private Cloud Compute for third-party developers, and conformances for Claude/Gemini.

M1K3 already has a hand-built provider seam that maps near 1:1 onto this: `BrainTier`,
`ToolTurnSession`, streaming via `ThinkStreamGate` (reasoning vs answer split), native
tool-calling (`MLXToolCalling`), persona-prefix / cross-turn KV reuse, and a tuned mlx-swift-lm
generate loop (`MLXGemmaProvider`).

Three product forces shape the decision:
- **Device spectrum** — Apple's on-device `SystemLanguageModel` only runs on Apple-Intelligence
  silicon; M1K3 must keep working on the widest range of Macs.
- **Offline ethos** — local, private, nothing leaves the device by default.
- **Inference at the user's request** — open richer/cloud inference only on explicit, per-request
  consent (the existing web-search-toggle pattern).

Tier-0 research (no betas; see `scratch/wwdc26-languagemodel/FINDINGS.md` + two runnable spikes)
established the load-bearing facts:
- The protocol is a **transport + lifecycle contract, not a sampling policy.** All of M1K3's
  tuning (`kvBits`/`quantizedKVStart`, `repetitionPenalty`, sampling, `prefillStepSize`, the
  `KVCache` primitive) lives in `mlx-swift-lm`'s `GenerateParameters`, *below* the protocol.
- Apple's official `MLXLanguageModel` is **not released** (mlx-swift-lm latest tag 3.31.3 — our
  pin — carries no `MLXFoundationModels` module).
- The reference conformance (`mattt/AnyLanguageModel`, `.macOS(.v14)`) does **prefix-match
  `kvCache` reuse** — our persona-prefix reuse runs *with* the grain — and exposes a
  `CustomGenerationOptions` escape hatch.
- The protocol has **no reasoning segment** (`Transcript.Segment` = `.text`/`.structure`/`.image`);
  the naive adapter leaks `<think>` into the answer. M1K3's `ThinkStreamGate` is additive value.

## Decision

We will adopt the `LanguageModel` protocol as a **conformance/bridge layer over M1K3's existing
provider seam — not a rebuild, and not by adopting Apple's `MLXLanguageModel` adapter.**

Concretely:
1. **Conform M1K3's brains to the protocol.** Each brain (MLX floor, Apple on-device, and the
   consent-gated network rungs) becomes a `LanguageModel`. The conformance's executor runs M1K3's
   own tuned generate loop and routes tokens through `ThinkStreamGate` before `channel.appendText`.
2. **Dual-path by availability.** On macOS < 27, conform to a **M1K3-local mirror protocol**
   (names mirror Apple's; see the spike) so the whole architecture builds and runs on Tahoe today.
   On macOS ≥ 27, conform to the real `import FoundationModels` types behind an availability shim.
   The retarget is near-mechanical because the shapes already match.
3. **Keep our tuning + gate.** Sampling, quantized-KV, persona-prefix KV reuse, and the
   reasoning/answer split stay inside our executor. We do not delegate them to a generic adapter.
4. **Express tiering as a consent-gated escalation ladder.** Local-offline is the default; network
   rungs (PCC, third-party) require BOTH the global egress switch AND an explicit per-request
   escalation. (Pure selection policy; spike-proven.)

## Consequences

### Positive
- **Interop for free** — M1K3 speaks the ecosystem's new lingua franca; other apps could even
  import M1K3-as-a-local-`LanguageModel`-provider via SPM.
- **No tuning loss** — the make-or-break risk is disproven; this is a bridge, cheap not costly.
- **Launch story** — "M1K3 built Apple's WWDC26 architecture a year early and adopts it without
  losing a thing; the gate + tuned sampling are more refined than the reference adapter."
- **Escalation standardized** — PCC and Claude/Gemini become first-class, consent-gated rungs.
- **Buildable today** — the mirror-protocol path needs no beta; we de-risk before any OS bump.

### Negative
- **OS floor for the *real* symbols** — compiling against `import FoundationModels` needs the
  macOS 27 SDK (Xcode 27 beta); running the new APIs needs macOS 27. Mitigated by the dual-path:
  our seam remains the universal floor, the real conformance is additive on ≥27.
- **Dual-path maintenance** — a mirror protocol + a real conformance to keep in sync until the
  macOS 27 floor is acceptable to bump to.
- **Apple-platform only** — the protocol is Swift/Apple. The KMP mobile app and Python CLI can't
  share it; this is a macOS/iOS-app decision, not the cross-platform inference layer.

### Neutral
- Xcode 27 beta installs alongside stable Xcode; release signing/notarization must stay pinned to
  stable Xcode (`xcode-select`). macOS 27 beta never goes on the launch machine.
- Adopting the protocol does not change which models M1K3 ships — only how they're surfaced.

## Alternatives Considered

### Full migration onto Apple's `MLXLanguageModel` adapter
- Pros: less code; Apple-maintained generate loop.
- Cons: not released yet (mlx-swift-lm 3.31.3 has no module); generic wrapper would lose
  persona-prefix KV reuse and leak `<think>` (no gate); cedes our tuning.
- Why rejected: nothing to migrate onto, and we'd regress the very things that make M1K3 good.

### Keep only M1K3's own seam; ignore the protocol
- Pros: zero new surface; no OS-floor concern.
- Cons: misses interop, the consent-ladder standardization (PCC/Claude as conformers), and the
  launch story; M1K3 drifts from the ecosystem.
- Why rejected: the conformance is cheap (shapes already match) and the upside is large.

### Wait for macOS 27 GA before doing anything
- Pros: build once against stable symbols.
- Cons: forfeits the first-mover launch narrative; leaves a proven, buildable bridge on the shelf.
- Why rejected: the mirror-protocol path lets us build now on Tahoe with no downside.

## References

- WWDC26 339 — Bring an LLM provider to the Foundation Models framework
- WWDC26 241 — What's new in the Foundation Models framework
- `scratch/wwdc26-languagemodel/FINDINGS.md` + `LanguageModelLadder.swift` + `ThinkGateChannel.swift`
- `ml-explore/mlx-swift-lm` `MLXLMCommon` (`GenerateParameters`, `KVCache`); `mattt/AnyLanguageModel`
- M1K3 project memory, 2026-06-14 (late) WWDC-exploration session
