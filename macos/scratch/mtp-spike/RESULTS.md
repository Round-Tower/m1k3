# MTP speculative-decoding spike — gemma-4-12B + paired drafter (2026-07-19)

**Question:** can Big use MTP speculative decoding on the pinned mlx-swift-lm
3.31.4, and does it survive gemma's sliding window (1024) with our long
personas?

**Setup:** `GemmaMTPSpike.swift` via `M1K3_SELFTEST_MTP=1`. Target
`mlx-community/gemma-4-12B-it-4bit` loaded through **MLXVLM** (the only Gemma4
that emits `mtpLastHiddenStatesKey`; the production MLXLLM path would silently
passthrough). Drafter `mlx-community/gemma-4-12B-it-qat-assistant-4bit` (238MB,
66M params, 4 layers), alias-registered as `gemma4_unified_assistant` (3.31.4
only registers `gemma4_assistant`). Greedy sampling + the production
repetition guard on BOTH legs, fresh caches per leg, blockSize 4. Live app
CLOSED (GPU idle) — verified before the run.

## Pre-registered gates (written BEFORE the run)

1. **LOADS** — drafter config decodes under the alias registration; 4-bit
   weights load; target loads via VLM path. (FAIL = decode/keyNotFound error.)
2. **ENGAGES** — `passthroughReason == nil` on the short fixture; accept rate
   reported. (FAIL = sticky passthrough from round 1.)
3. **FAITHFUL** — greedy MTP output byte-identical to greedy baseline on the
   **short (never-wraps)** fixture. This is the algorithm's own correctness
   invariant, not a quality judgment.
4. **SURVIVES THE WRAP** — on the medium (wraps mid-decode) and long
   (wrapped-at-prefill) fixtures, either (a) exact match holds, or (b) the
   iterator degrades to passthrough. Divergence WITHOUT passthrough = the
   rejected-token ring pollution is real (MTPSpeculativeTokenIterator.swift:355
   — post-wrap trim no-ops, no passthrough transition) → production
   integration must gate on prompt length or fix upstream first.
5. **SPEED** — ≥1.3× decode tok/s on whichever fixtures speculation stays
   engaged for. (Below that, the complexity isn't worth carrying.)
6. **RAM** — peak with both models resident ≤ 8.5GB (VLM-loaded 12B measured
   7333MB on 2026-07-14 + 238MB drafter + slack).

Promotion path if gates pass: switch Big's production load to the VLM path
(Kev wants multimodal anyway — one move, two unlocks), wire
`generate(mtpDrafter:)` behind the InferenceProvider seam, re-run the
live-path CHATEVAL arm as the acceptance gate (the #43 precedent).

## Run log

Run 1, 2026-07-19 12:18, Debug build off master (da65e178 + spike), app closed
(GPU idle). Raw log: `selftest-mtp-run1-20260719.txt`.

| fixture | prompt | baseline | mtp | speedup | accept | match | passthrough |
|---|---|---|---|---|---|---|---|
| short-no-wrap | 25tok | 20.9 tok/s | 24.5 tok/s | 1.17× | 0/0 | exact | "main model did not emit drafter state" |
| medium-wraps-mid-decode | 588tok | 29.0 tok/s | 23.1 tok/s | 0.80× | 0/0 | exact | same |
| long-wrapped-at-prefill | 2072tok | 31.0 tok/s | 4.0 tok/s | **0.13×** | 0/0 | exact | same |

RAM both models resident: active 6430MB · peak 7789MB · footprint 7077MB.

## Gate results

1. **LOADS — PASS.** The alias registration works: the drafter's
   `gemma4_unified_assistant` config decodes with the stock
   `Gemma4AssistantConfiguration` creator and the 238MB 4-bit weights load
   (no decode/keyNotFound error; all six legs ran). Target loads via VLM.
2. **ENGAGES — FAIL, root-caused.** 0 tokens drafted on every fixture; sticky
   passthrough from round 1. Cause (pinned to source, confirmed on upstream
   `main` 2026-07-19): **`Gemma4Unified` never implements the MTP-aware
   `LanguageModel` entry point.** Only the legacy `Gemma4` class reads
   `mtpEmitFlagKey` from the call state (MLXVLM/Models/Gemma4.swift:2092-2101
   at 3.31.4); `Gemma4Unified`'s callAsFunction takes no `state:`, so the
   iterator's opt-in flag lands on the default protocol implementation and no
   drafter state comes back. The inner `Gemma4TextLanguageModel` it wraps
   ALREADY supports emission fully (Gemma4.swift:1399-1427) — the gap is a
   missing ~15-line mirror of the entry point. Our 12B checkpoint
   (`model_type: gemma4_unified`) and its paired drafter
   (`gemma4_unified_assistant`) are both the unified generation, so 3.31.4's
   MTP feature simply doesn't reach them.
3. **FAITHFUL — PASS (vacuous).** Exact match everywhere, but passthrough IS
   the baseline algorithm; the invariant was never stressed.
4. **SURVIVES THE WRAP — UNANSWERED.** Speculation never engaged, so the
   rejected-token ring-pollution question stays open. The greedy exact-match
   instrument is built and waiting; re-run the moment engagement works.
5. **SPEED — FAIL, with a bonus finding:** the iterator's passthrough
   fallback is much SLOWER than the plain generate path (0.80× at 588tok,
   0.13× at 2072tok prompt — `passthroughStep` does a synchronous per-token
   `.item()` round-trip with none of the plain path's async eval pipelining).
   **Never ship MTP without engagement telemetry + a bail-out to plain
   generate** — a silent fallback would REGRESS Big's decode badly.
6. **RAM — PASS.** 7789MB peak with both resident, under the 8.5GB gate; and
   the VLM-loaded target costs ~nothing over the production text-only load
   (7333MB was the 07-14 VLM figure without the drafter).

## Verdict

**PARKED ON UPSTREAM, instrument ready.** Everything app-side works — alias
registration, drafter load, RAM, the harness — but mlx-swift-lm (3.31.4 AND
current main) only wired MTP into the legacy `gemma4` model class, not
`gemma4_unified`, which is what every current gemma-4 checkpoint (and the 12B
drafter itself) uses. The fix is small and upstream-shaped: mirror `Gemma4`'s
MTP-aware `callAsFunction(_:cache:state:)` into `Gemma4Unified` (thread
`mtpEmitFlagKey` → `languageModel(..., emitDrafterState:)`, wrap the returned
state). Draft patch + issue text in `upstream-patch.md` beside this file —
filing is Kev's call (external action).

Re-run trigger: the upstream fix lands (any release with Gemma4Unified MTP) →
one config line re-runs this spike → gates 2/4/5 get real answers. Gate 4
(wrapped-window pollution, sliding_window=1024 vs our 2-3k personas) is the
one that decides production shape: if pollution is real, the integration gates
the drafter on prompt length; if exact-match holds, it's unconditional.

Multimodal side-quest (Kev's "we want multimodal going forward"): this run
re-confirms the VLM load path is production-viable RAM-wise for Big — the
Big-via-VLMModelFactory switch can proceed INDEPENDENT of MTP, and having it
in place makes the eventual MTP wire-up a one-liner.

*Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.9 (single idle-GPU
run, but the failure is mechanistic not statistical — root cause pinned to
missing code in the wrapper class and confirmed against upstream main; the
passthrough-slowness numbers are one run each, direction unambiguous).
Prior: Unknown.*
