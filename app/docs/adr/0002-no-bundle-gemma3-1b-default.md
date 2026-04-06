# ADR-0002: No-Bundle MVP — Gemma 3 1B as Default Model

**Status:** ACCEPTED
**Date:** 2026-04-06
**Deciders:** Kev Murphy + Claude

MurphySig: kev+claude / confidence 0.88 / 2026-04-06
Context: sub-1B models fail at multi-turn chat; quality cliff is real

---

## Context

The app was bundling three GGUF models (~400MB total) in the APK. The default model — Gemma 3 270M (IQ3_XXS, ~110MB) — produced poor chat quality: hallucinations, repetition, loss of coherence in multi-turn conversations. The 270M parameter count is simply below the threshold for useful instruction following.

## Decision

1. **Remove all bundled GGUF models** from APK assets.
2. **Gemma 3 1B** (`bartowski/gemma-3-1b-it-GGUF`, Q4_K_M, ~620MB) is the new default.
3. **First-launch download screen** prompts user to download before chat is available.
4. **Flagship devices** (8GB+) are offered Gemma 4 E2B as an upgrade option.

## The Quality Cliff

| Model | Params | Size (Q4) | Chat Quality |
|-------|--------|-----------|--------------|
| SmolLM2 135M | 135M | ~100MB | Poor |
| Gemma 3 270M | 270M | ~200MB | Poor (was default) |
| Gemma 3 1B | 1B | ~620MB | **Good** ← new default |
| Gemma 4 E2B | 2B eff | ~1.4GB | Excellent |

Sub-1B models fail at multi-turn conversation, instruction following, and reasoning. The 1B class is the minimum viable quality bar for M1K3's use case (personal AI assistant).

## Consequences

- **APK is ~400MB lighter** — no bundled model assets
- **First-launch requires WiFi** — 620MB download (~3 min on LTE, ~1 min on WiFi)
- **Quality bar raised permanently** — responses are coherent and helpful
- **Ma library required** — Llamatik doesn't support Gemma 3 1B correctly (chat format, Llama 3.2 tokenizer differences)

## Alternatives Considered

**Bundle a better small model (360M-500M):** Marginal quality improvement. Still below the coherence threshold. Ruled out.

**Keep 270M bundle as fallback:** Confusing two-tier experience. Users on the 270M path would get poor results and blame the app. Ruled out.

## LlmModel Changes

`Gemma3_1B` added as new `default`. `Gemma3_270M` and `FalconH1_90M` remain in `all()` for low-end device fallback (future work).
