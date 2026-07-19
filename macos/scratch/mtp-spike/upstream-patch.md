# Upstream patch draft — Gemma4Unified MTP entry point (mlx-swift-lm)

**Status: DRAFT — filing the issue/PR upstream is Kev's call.**

## Issue text (draft)

Title: `Gemma4Unified` never engages MTP speculative decoding — missing the
MTP-aware `callAsFunction(_:cache:state:)` override

The 3.31.4 release added Gemma 4 MTP speculative decoding
(`MTPSpeculativeTokenIterator` + `Gemma4AssistantDraftModel`), but only the
legacy `Gemma4` class implements the MTP-aware `LanguageModel` entry point
that reads `mtpEmitFlagKey` from the call state
(`Libraries/MLXVLM/Models/Gemma4.swift`, ~line 2092 at tag 3.31.4).
`Gemma4Unified` does not override it, so the iterator's opt-in flag hits the
protocol-extension default, no `mtpLastHiddenStatesKey`/`mtpSharedKVStatesKey`
come back, and the very first `speculateRound()` transitions to sticky
passthrough with "main model did not emit drafter state".

This matters because current gemma-4 checkpoints are the unified generation:
e.g. `mlx-community/gemma-4-12B-it-4bit` is `model_type: gemma4_unified`, and
its paired drafter `mlx-community/gemma-4-12B-it-qat-assistant-4bit` is
`gemma4_unified_assistant` — so MTP as shipped can't reach the very
checkpoints the drafters are published for. (Two adjacent gaps in the same
area: `Gemma4AssistantRegistration.register()` only registers
`gemma4_assistant`, not `gemma4_unified_assistant` — downstream apps can
alias-register it themselves since the registry is public, but upstream
registration would be friendlier. And `MTPDrafterRegistry` only lists the
26B/31B assistant ids.)

Reproduction: load `gemma-4-12B-it-4bit` via `VLMModelFactory`, load the 12B
drafter via `MTPDrafterModelFactory` (with the alias registered), call
`generate(input:cache:parameters:context:mtpDrafter:blockSize:)` — observe
`proposedDraftTokens == 0` and
`passthroughReason == "main model did not emit drafter state"`.

Observed while spiking MTP for M1K3 (app.m1k3) on an M-series Mac; happy to
PR the fix below.

## The fix (mirror of Gemma4's entry point, adapted)

`Gemma4TextLanguageModel` already fully supports drafter-state emission
(`callAsFunction(..., emitDrafterState:)` → populates the two state keys).
`Gemma4Unified` just needs the same override `Gemma4` has:

```swift
// In Gemma4Unified, beside the existing
// callAsFunction(_ inputs: MLXArray, cache:) at ~line 2555:

/// MTP-aware `LanguageModel` entry point. Reads `mtpEmitFlagKey` from
/// the incoming `state` and threads it through to `Gemma4TextLanguageModel`;
/// the returned `LMOutput` carries `mtpLastHiddenStatesKey` and
/// `mtpSharedKVStatesKey` populated when the flag is set, empty otherwise.
/// Overrides the protocol-extension default at `LanguageModel` which
/// would discard `state`.
public func callAsFunction(
    _ input: LMInput.Text, cache: [any KVCache]?, state: LMOutput.State?
) -> LMOutput {
    let emit = state?[mtpEmitFlagKey] ?? false
    return languageModel(
        input.tokens, cache: cache?.map { $0 },
        emitDrafterState: emit
    )
}
```

Caveats for the PR:
- Verify `Gemma4Unified`'s inner `languageModel` callAsFunction accepts
  `emitDrafterState:` with plain token input (it does at 3.31.4 —
  Gemma4.swift:1394-1427 — but the unified text LM call at ~2545 goes through
  the `inputsEmbeds:` overload; the token-input overload is the one the
  drafter path needs, same as legacy `Gemma4` uses).
- Upstream may also want `Gemma4AssistantRegistration.register()` to register
  BOTH `gemma4_assistant` and `gemma4_unified_assistant`, and the 12B
  assistant ids added to `MTPDrafterRegistry`.
- A regression test: MTP generate on a `gemma4_unified` target must report
  `passthroughReason == nil` after round 1.

## Local evidence

See `RESULTS.md` beside this file: alias registration + drafter load + RAM all
proven on-device (M1K3 SelfTest harness, 2026-07-19); engagement blocked
solely by this missing override, confirmed present on upstream `main` as of
2026-07-19.

*Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (root cause
source-pinned and behaviourally confirmed on-device; the fix code itself is
UNTESTED — adapted by inspection from the legacy Gemma4 override; the
inputsEmbeds-vs-tokens overload caveat is called out for the PR). Prior:
Unknown.*
