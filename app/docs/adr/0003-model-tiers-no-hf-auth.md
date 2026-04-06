# ADR-0003: Model Selection for Mini/Lil Tiers — No HF Auth Required

**Status:** ACCEPTED
**Date:** 2026-04-06
**Deciders:** Kev Murphy + Claude

MurphySig: kev+claude / confidence 0.90 / 2026-04-06
Context: live 401 failure on device confirmed gating; Qwen3 verified public

---

## Context

During the first on-device QA session (Easter Monday 2026), Mini and Lil M1K3
downloads were returning **HTTP 401 Unauthorized** from HuggingFace. The root
cause: all Gemma-family models (including community quantizations by bartowski)
require a HuggingFace account and Google's usage policy acceptance — even the
270M variant.

```
curl -sI https://huggingface.co/bartowski/gemma-3-1b-it-GGUF/.../... → HTTP 401
curl -sI https://huggingface.co/bartowski/gemma-3-270m-it-GGUF/.../... → HTTP 401
curl -sI https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/.../... → HTTP 302 ✅
```

Big M1K3 (Gemma 4 E2B via unsloth) was unaffected — unsloth's repo does not
inherit Gemma's gating.

## Model Landscape Research (April 2026)

Qwen3 (May 2025) and Qwen3.5 (March 2026) supersede the original Gemma 3
candidates on quality-per-byte, and both are fully public on HuggingFace with
no authentication required.

| Tier | Rejected | Reason | Accepted |
|------|----------|--------|---------|
| Mini | Gemma 3 270M | 401 gated | **Qwen3-0.6B Q4_K_M** |
| Lil  | Gemma 3 1B   | 401 gated | **Qwen3-1.7B Q4_K_M** |
| Lil  | Qwen2.5-1.5B | superseded | **Qwen3-1.7B Q4_K_M** |

## Decision

Replace both Mini and Lil tier models with Qwen3 variants:

**Mini M1K3 → Qwen3-0.6B Q4_K_M (~484MB)**
- `bartowski/Qwen_Qwen3-0.6B-GGUF` — HTTP 302, no auth
- Verified: `x-linked-size: 484220320`
- Better instruction-following than SmolLM2-360M at similar scale

**Lil M1K3 → Qwen3-1.7B Q4_K_M (~1.28GB)**
- `bartowski/Qwen_Qwen3-1.7B-GGUF` — HTTP 302, no auth
- Verified: `x-linked-size: 1282439584`
- Qwen3-1.7B ≈ Qwen2.5-3B quality (full generation ahead of Qwen2.5-1.5B)

Both use **ChatML format** — identical to `ChatFormat.ChatML` in our codebase.
Zero prompt formatting changes needed.

## HuggingFace Gating — Future Consideration

Adding HF token support would re-enable Gemma models and open access to all
gated models. Pattern: store token in `PreferencesStore`, pass as
`Authorization: Bearer {token}` header in `HttpModelDownloadManager`. Deferred
— not needed while Qwen3 family covers the tier requirements.

## Consequences

- Mini downloads are 484MB vs 270MB (SmolLM2) — 79% larger but meaningfully
  better quality and no gating risk
- Lil downloads are 1.28GB vs 620MB (Gemma 3 1B) — but quality is
  dramatically higher (Qwen3-1.7B ≈ Qwen2.5-3B)
- All three tiers now work without a HuggingFace account
- Gemma 3 variants retained in `LlmModel` as legacy references
