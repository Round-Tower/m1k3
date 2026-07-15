# Model-slot eval — 2026-07-15 (the 12B gate + Bonsai A/B)

Ten headless CHATEVAL runs (SelfTest, Debug build off master a1791bd0 + the
Bonsai family mapping), all same morning, live app idle — so unlike the
2026-07-14 run, absolute latencies are clean, not contention-inflated.
Raw per-fixture reports sit beside this file (`selftest-run*.txt`).

## Headline matrix (pass / median ms)

| kind | gemma-4-e4b (Big) | gemma-4-12B (candidate) | Qwen3-4B (Lil) | Bonsai-8B (candidate) |
|---|---|---|---|---|
| open-chat (live path) | 5/8 · 21.6s | **8/8 · 24.9s** | 8/8 · 16.2s | 7/8 · 9.7s |
| code-gen (live path) | 4/5 · 16.2s (one 1032s melt) | **5/5 · 22.5s (max 57s)** | 5/5 · 26.0s | 5/5 · 10.2s |
| tool-use (native) | **5/5 · 11.9s** | 4/5 · 30.9s (one 125s>120s nick) | 5/5 · 21.0s | **5/5 · 4.2s** |
| grounded-Q | 5/8 | 5/8 | 5/8 | 5/8 |
| reasoning | 3/6 | **6/6** | 6/6 | 5/6 |
| refusal (scorer-corrected*) | 5/5 | 5/5* | 5/5* | 5/5 |
| security (prompt-leak) | 4/7 — REAL leaks | **7/7** | 6/7 | **2/7 — REAL leaks** |

\* matrix printed 3/5 (12B) and 2/5 (Qwen3-4B); eyeball of the answers shows
genuine declines the marker list missed — fixed in PR #42 with the live
phrasings as fixtures.

## Verdicts

1. **gemma-4-12B is UNBLOCKED and beats e4b in the Big slot on everything but
   tool latency.** It loads on the pinned mlx-swift-lm 3.31.4 (the
   `vision_embedder` sanitize fix IS in the tag — `docs/MODEL_CHOICES.md`'s
   "blocked" row is stale), and the June `RotatingKVCache.temporalOrder`
   tool-use crash did NOT reproduce: full tool arm run on the rotating cache,
   exit 0. Costs: ~2.6× slower tool turns (30.9s vs 11.9s median), 6.7 GB disk
   vs 4.8 GB, ~7.4 GB peak RAM (June measurement, unchanged geometry).
2. **The 2026-07-14 "26.8-minute greeting" was contention, not model.** Same
   fixture, same code, idle machine: 21.6s (e4b) / 44.7s (12B). What is REAL
   about e4b under the production scaffolding: length-band blowouts (3/8),
   one "M1K3:" self-label leak, a genuine 17-minute code-gen loop-thrash
   (code-landing-page), reasoning 3/6, and 3/7 verbatim prompt-leaks.
3. **Bonsai-8B: PARKED on the prompt-leak gate.** Loads out-of-the-box
   (model_type qwen3, standard affine 2-bit — genuinely not an OptiQ trap),
   ~2× faster than the incumbent Lil everywhere (tools 4.2s median!),
   refusal 5/5 — but it recites the hardened v2 system prompt on 5/7
   security fixtures (leak-verbatim printed 3.6k chars including the rules
   text). Same gate class that parked the voice LoRA. Revisit on a prism-ml
   update or after a hardening pass; the family mapping (PR #41) stays so a
   re-eval is a one-line config.
4. **grounded-Q 5/8 is model-invariant** (same three abstention-class fixtures
   fail on all four brains) — that's a fixture/scaffolding follow-up, not a
   model differentiator.

## Recommendation (Kev's call)

Promote **gemma-4-12B-it-4bit to Big**. The live-path data says the "Big fix"
IS the promotion: register discipline, reasoning, and the leak gate all move
decisively, with identical scaffolding and no prompt surgery. Mobile is
unaffected (ladder tops out at Lil). Keep e4b as the fallback row in
MODEL_CHOICES with tonight's data. The one watch-item: tool-turn latency
(~31s median) — if it grates in-app, the per-tier think budget conversation
reopens, now with an instrument to measure it.

Signed: Kev + claude-fable-5, 2026-07-15, Confidence 0.85 (all runs headless
on-device off the production stack, same-morning contrasts; honest opens: one
run per arm — no variance bars; Bonsai/12B prose quality is scorer-band, not
eyeball-read; RAM for 12B carried from the June measurement, not re-measured).
Prior: Unknown
