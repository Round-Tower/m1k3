# MODEL_CHOICES.md — picking M1K3's local brains

> Living decision doc for the MLX-backed brain tiers (lil / big / huge). Mini is Apple
> Foundation Models and is out of scope here. Started 2026-06-22 after the Phase 21
> OptiQ swap was reverted; this is the evidence base for what replaces it.
>
> **The golden rule (learned the hard way):** existence ≠ loadability ≠ quality. Verify each
> stage. OptiQ repos existed and had correct sizes/names but didn't *load*; Qwen3.5 loads but
> CPU-spikes; gemma-4 loads and runs but *reasons itself into silence*. Specs get you a
> shortlist; only an on-device CHATEVAL picks the winner.

---

## The runtime constraint: mlx-swift-lm (now a main-HEAD revision pin)

**Update 2026-06-23:** M1K3 now builds off **mlx-swift-lm main** (`revision 40c2ff06`, = PR #330's
merge) to unlock Gemma 4 native tool-calling — the last *release*, 3.31.3, had no `.gemma4` parser.
See "Runtime watch" below for the full why + the (temporary) revert path. The failure-axes analysis
below still holds; what runs is decided by the registry (`Libraries/MLXLLM/LLMModelFactory.swift`) +
which arches it implements (main's registry ⊇ 3.31.3's).

Three independent failure axes, all seen this session:
1. **Format loadability** — OptiQ (`mlx-optiq` mixed precision) has no Swift loader → won't load
   (Qwen3.5 OptiQ → token-soup; gemma-4 OptiQ → `k_proj.weight not found`). **OptiQ is parked.**
2. **Architecture / perf** — SSM/linear-attention hybrids (GatedDeltaNet, e.g. Qwen3.5) were
   CPU+GPU-heavy at prefill. **Corrected 2026-06-24:** this was NOT per-token CPU dispatch — on Apple
   Silicon the fused Metal kernel runs the whole T-step loop in ONE GPU dispatch (`GatedDelta.swift:244`'s
   `for t` is the CPU *fallback*, not the path taken). The real cost was the prefill **sync stall**, and
   **main's #225 (asyncEval prefill) fixes it** (2.7–14.6× prefill). See "What building off main unlocks."
3. **Tool-calling dialect** — M1K3 runs a tool loop every turn. Models with a native dialect in
   `MLXToolCalling.resolveToolCallFormat` (json / xmlFunction / gemma / **gemma4** / glm4 / lfm2) are
   reliable; the rest fall to the **prompt-ReAct floor**, which gemma-4 handled badly (the no-response
   bug). **As of the main pin, gemma-4 resolves `.gemma4` → native tools** (on-device verify-owed that
   this kills the no-response bug).

---

## The registry, sorted by what matters

### ✅ Dense attention — safe (no recurrent-scan spike), all registry-confirmed
`qwen3` (dense Qwen3, **not** `.5`), `phi3` (Phi-4-mini), `llama`, `qwen2`, `gemma3`/`gemma3_text`,
`gemma4`/`gemma4_text` (E-variants only), `glm4`, `mistral3`, `smollm3`, `cohere`, `internlm2`,
`minicpm`, `starcoder2`, `olmo2/3`, `exaone4`.

### ⚠️ SSM / hybrid — GatedDeltaNet prefill cost (largely LIFTED on main)
`qwen3_5` (current lil/huge), `qwen3_next`, **`granite` / `granitemoehybrid`** (← the plan's
"Granite 4.1" candidate is a Mamba2 hybrid — *same family*), `falcon_h1`, `nemotron_h`, `jamba_3b`,
`minimax`. The prefill spike was the **sync stall**, not a per-token CPU scan — **main's #225 (asyncEval)
lifts it** (2.7–14.6× prefill on GDN models, scaling with context). Now a bake-off question, not an
automatic avoid; the recurrence itself stays on-GPU per step (inherent to SSMs).

### 🚫 Registered-but-blocked / RAM-heavy
- **`gemma4_unified` — registered on main's LLM path (#0767814), but gemma-4-12B STILL won't load as-is.**
  `LLMModelFactory.swift:35` maps `gemma4_unified → Gemma4Model` (text wrapper) and the 12B config decodes
  cleanly (text_config nested; 48 layers / hidden 3840; KV-share=0, PLE=0, k_eq_v=true all match). BUT the
  12B-unified checkpoint renamed its vision encoder to **`vision_embedder.*`** (11 tensors), which
  `Gemma4Model.sanitize` (mlx-swift-lm `MLXLLM/Models/Gemma4.swift:72-77`) does NOT drop — it drops
  `vision_tower`/`audio_tower`/`embed_vision`/`embed_audio` (e4b's names), not `vision_embedder`. → 11 orphan
  keys → `model.update(verify:[.all])` throws `UpdateError.unhandledKeys`. **Fix = one upstream line** (add
  `vision_embedder` to that drop list). Verified statically (conf 0.85) + the 11 tensors confirmed in the HF
  index; on-device load is the final gate. So: a `huge` candidate gated on an upstream `sanitize` fix, NOT a
  drop-in. (12B-4bit = 6.74 GB.)
- MoE (RAM-hungry, viable but heavy): `qwen3_moe`, `glm4_moe`, `gpt_oss`, `deepseek_v3`, `phimoe`,
  `olmoe`, `bailing_moe`, `afmoe`.

---

## Verified candidates (HF API + config, 2026-06-22)

| Model id | Size (4-bit) | Arch | `model_type` | Tool dialect | Loads on 3.31.3 | Notes |
|----------|-------------|------|-------------|-------------|-----------------|-------|
| `mlx-community/Qwen3-4B-4bit` | 2.27 GB | **dense** | `qwen3` | `.json` (native) | ✅ verified | lil candidate; smaller than current lil |
| `mlx-community/Qwen3-8B-4bit` | 4.62 GB | **dense** | `qwen3` | `.json` (native) | ✅ verified | **huge candidate** — only loadable capable native-tool model at this size |
| `mlx-community/Phi-4-mini-instruct-4bit` | 2.17 GB | **dense** | `phi3` | `.json` (native) | ✅ verified | lil candidate; strong reasoning-per-size |
| `mlx-community/gemma-4-e4b-it-4bit` | ~5.25 GB | attention | `gemma4` | **ReAct floor** | ✅ (current big) | ⚠️ **no-response bug** (below); no quantized KV |
| `mlx-community/gemma-4-e2b-it-4bit` | 3.6 GB | attention | `gemma4` | **ReAct floor** | ✅ verified | ~2B-eff → **below M1K3's grounding floor** (per the 2026-06-13 2B→4B promotion) |
| `mlx-community/Qwen3-14B-4bit` | ~8 GB (verify) | dense | `qwen3` | `.json` | ⏳ verify-owed | would make a clean all-dense huge |
| `mlx-community/SmolLM3-3B-*` | ~1.8 GB (verify) | dense | `smollm3` | ReAct floor (no dialect) | ⏳ verify-owed | plan candidate; small |
| **current** lil `Qwen3.5-4B` / huge `Qwen3.5-9B` | — | **GatedDeltaNet hybrid** | `qwen3_5` | `.xmlFunction` | ✅ but **CPU-spikes** | works; the perf problem |
| **blocked** `gemma-4-12B-it-*` | — | unified | `gemma4_unified` | — | ❌ won't load | not in registry |

---

## ⚠️ The gemma-4-e4b "No response" bug (observed 2026-06-22)

**Symptom:** simple chat ("How are things M1K3?") → chat shows **"No response."** with a full
`<think>` block present. The model's own reasoning ends: *"No action or response is required
unless the user speaks again. I must remain silent/ready."* — it **deliberately emits an empty
final answer.**

**Likely cause (primarily model behaviour, not pure UI):** M1K3 wraps every turn in the
agentic ReAct tool-loop framing + a brevity/DRY persona. gemma-4 was trained on its **own** native
tool format (not our ReAct text protocol), so on the ReAct floor it interprets the turn as "is a
tool ACTION needed?" → concludes "no action required" → **stays silent**. The brevity/DRY persona
reinforces silence as "correct." This is the same family of issue as the 2026-06-10 note ("Gemma-3n
announces tools in prose without emitting `ACTION:`").

**Verify-owed:** confirm it's not *also* a reasoning-split parse bug — i.e. check the raw model
output for any post-`</think>` answer text that M1K3's splitter is swallowing. The reasoning content
("remain silent") points at intent, but a parse contribution isn't excluded.

**Implication:** this is a **third strike** against gemma-4 for `big` (after ReAct-floor tools and
can't-fill-huge). Either fix the prompt/parse for gemma-4, or move `big` to a native-tool dense model.

---

## Recommendation (pending CHATEVAL)

| Tier | Recommended | Rationale | Confidence |
|------|------------|-----------|-----------|
| **mini** | Apple Foundation Models (keep) | works; no download | high |
| **lil** | eval **Qwen3-4B** vs **Phi-4-mini** | dense, native `.json` tools, smaller download; same-family (Qwen3) vs strong-small (Phi) | high it loads, eval picks winner |
| **big** | **Qwen3-8B** dense (or fix gemma-4 first) | gemma-4's no-response + ReAct-tools make it shaky for an agentic app; Qwen3-8B is dense + native tools | medium — depends on gemma-4 bug fix |
| **huge** | **Qwen3-8B** or **Qwen3-14B** dense | the only loadable, capable, native-tool models at this size (gemma-4-12B blocked); dense → kills the spike | high it loads, eval picks size |

**Cleanest single-family option:** an **all-dense-Qwen3 ladder** — `Qwen3-4B` (lil) / `Qwen3-8B`
(big) / `Qwen3-14B` (huge). One family, native `.json` tools, dense (no spike), fills every size.
Trade-off: one generation older than Qwen3.5 (Apr 2025 vs newer) and no on-device vision — both
largely moot for a RAG/text app (see "what we lose going dense" below).

### What we lose going dense (honest)
1. **One model generation** (Qwen3 ≈ Apr 2025 vs Qwen3.5 newer) — possible small quality/recency
   regression; *this is the only real cost, and CHATEVAL measures it.* A working older model beats a
   spiking/silent newer one.
2. **Long-context efficiency + a 256k window** — moot: M1K3 is RAG-bounded, and the hybrid's
   efficiency never materialised on 3.31.3 (it became the CPU spike).
3. **Multimodality / vision** — never used (text path only); only matters if M1K3 wants on-device
   image understanding later.

### License axis (the whole MLX lineup is Apache 2.0)

Verified on HF metadata + Google's announcement (2026-06-23):
- **Qwen3** (lil/huge): **Apache 2.0** — commercial use, fine-tuning, distillation, redistribution all
  clean (keep attribution / the `NOTICE`).
- **Gemma 4** (big): **Apache 2.0** — Google moved Gemma 4 to the OSI-approved Apache 2.0 license
  (Mar 2026, "first in the Gemmaverse"; no acceptable-use overlay per the announcement). The old custom
  **Gemma Terms** do NOT apply to Gemma 4. (M1K3's #86 licenses surface tagging it apache-2.0 is correct.)
- **Apple Foundation Models** (mini): used via the OS framework under Apple's terms; not redistributed.

So M1K3's model foundation is **unencumbered** — fine-tune any tier on Kev's own data and ship commercially.

Caveats (not legal advice — get a real IP review before a commercial ship leans on tuned/distilled weights):
- **Only Gemma 4 got Apache 2.0 — Gemma 3 still carries the custom Gemma Terms.** Moving `big` to Gemma 3
  (e.g. for native tools on 3.31.3) would RE-introduce license baggage. Gemma 4 is the clean Gemma.
- **Distillation legality is about the TEACHER, not the student's license tag.** Distilling from closed
  models (GPT/Claude/Gemini) likely violates their ToS (no-train-competitors) → provenance taint;
  murky-provenance HF "distilled" repos are a commercial + ethics risk (unauditable, and counter to M1K3's
  help-not-exploit thesis). Distill from an **Apache teacher you control** (Qwen3 / Gemma 4) instead.
- **Verify the repo's actual `LICENSE` file** (the announcement ≠ the authoritative text), and don't trust
  a community quant's license *tag* in isolation — confirm against the base model.

---

## Next steps

1. **CHATEVAL bake-off (the make-or-break measurement).** Candidates × tasks
   (`grounded-Q`, `open-chat`, `tool-use`, thinking quality), on an **idle** machine, **serialised**
   (per the 2026-06-20 thermal/contention lesson). Shortlist: lil = {Qwen3-4B, Phi-4-mini}; huge =
   {Qwen3-8B, Qwen3-14B}; big = {Qwen3-8B, gemma-4-e4b-if-fixed}. This is the one number that turns
   opinion into data.
2. **Investigate the gemma-4 no-response bug** — raw-output check (parse vs model intent); decides
   whether gemma-4 is salvageable for `big` or retired.
3. **Verify-owed repos** — confirm `Qwen3-14B-4bit` + a good `SmolLM3-3B` quant exist + sizes (HF tree API).
4. **Tier-count decision (product):** keep three MLX tiers, or collapse to two (a fast small lil +
   one strong big) — the loadable dense range may not justify three distinct steps.
5. **Ship the winners** as TDD'd id swaps (registry-confirmed dense → far lower risk than OptiQ).
   The code already distinguishes `qwen3` (dense, `.json`, no pre-open-think) from `qwen3_5`
   (xmlFunction, pre-open-think), so dense Qwen3 is near-drop-in.

---

## Runtime watch — mlx-swift-lm version (the gemma-4 unlock) — ✅ DONE 2026-06-23

**M1K3 now builds off `mlx-swift-lm` main** (`revision 40c2ff06` = PR #330's merge). The last
*release* was 3.31.3 (2026-04-15); two fixes that materially change the Gemma 4 calculus landed in
**main** after that tag, so a release pin couldn't reach them:

- **Native Gemma 4 tool-calling** — `mlx-swift-lm` **PR #183** ("Adopt GemmaFunctionParser to
  accommodate Gemma4 tool calls", merge commit `8c618003`, ~2026-05-22) extends the `gemma` parser to
  Gemma 4's `<|tool_call>call:name{key:<|"|>value<|"|>}<tool_call|>` format. (Python `mlx-lm` got the
  same via PR #1105, 2026-04-05 — but that's the Python package, irrelevant to our Swift app.) Once
  this is in a release, M1K3's `resolveToolCallFormat` can return a real dialect for gemma-4 instead
  of `nil` → gemma-4 gets NATIVE tools instead of the ReAct floor, which likely kills its no-response bug.
- **Gemma 4 E-series load fix** — `mlx-swift-lm` **PR #330** (MERGED to main 2026-06-23) "Fix Gemma4
  QAT (E-series) load: KV-shared layers have no k_proj/v_proj/k_norm" is the exact `k_proj.weight not
  found` error we hit loading gemma-4 OptiQ — a known gemma-4-loader gap, not purely OptiQ's fault.
  **As of 2026-06-23, main has BOTH #183 and #330.**

**The spike PASSED (2026-06-23) → shipped.** `Package.swift` pins `revision 40c2ff06` + the regenerated
`Package.resolved` is committed (Xcode Cloud needs the lockfile — the exit-74 lesson). What the spike proved:
- **The WhisperKit / swift-transformers clash did NOT fire** — mlx-swift-lm depends on *neither*
  swift-transformers nor WhisperKit (only mlx-swift + swift-syntax), so it can't drag them. Confirmed both
  analytically and by a clean `swift package resolve`.
- **Blast radius = exactly two resolved pins:** mlx-swift-lm → the revision, and swift-syntax
  `600.0.1 → 603.0.2` (a prebuilt macro artifact — no source compile). Every other pin (incl.
  swift-transformers 1.1.9, WhisperKit 0.18.0, mlx-swift 0.31.4) is byte-identical to master. Zero new packages.
- **The whole lineup builds + the full suite is green off main** (1525 tests / 237 suites). The
  ~2-months-of-drift regression risk is covered by the on-device verify (esp. the dense Qwen3 path).
- **gemma-4 now routes `.gemma4` → native tools.** Whether that kills the no-response bug is the named
  **on-device verify-owed**.

**The pin is explicitly TEMPORARY.** The armed weekly release-watch flags when `mlx-swift-lm` cuts a
*release* > 3.31.3 (which will contain #183 + #330); at that point swap the revision back to
`.upToNextMinor(from: "<next-tag>")` — the cleaner long-term pin. Until then the revision is reproducible
(fixed SHA) and gives us gemma-4 native tools + Apache 2.0 now.

**Net for `big`:** gemma-4-e4b now has native tools AND Apache 2.0 (vs Gemma 3's custom Gemma Terms),
clearing two of the three strikes against it — *if* the on-device verify confirms the no-response bug is
gone. Otherwise dense Qwen3-8B stays the `big` fallback (see Recommendation).

---

## What building off main unlocks (the level-up — 2026-06-24)

Pinning main wasn't just the gemma-4 tool-calling fix — the same ~40 commits (3.31.3 → `40c2ff06`)
carry a stack of capabilities. Verified against the local checkout. **✅ = now live via the pin ·
🔬 = reachable, needs a verify/eval · 🚫 = still blocked.** Each is its own scoped piece of work.

**Gemma 4 (`big`) — went from shaky to strong:**
- ✅ **Native tool-calling** (#183) — shipped (PR #98); `resolveToolCallFormat` → `.gemma4`. Should kill
  the no-response bug (on-device verify-owed).
- ✅ **E-series load fix** (#330) — the `k_proj.weight not found` gap closed.
- 🔬 **Decode +23.8%** (#82d9cd6) + **MTP speculative decoding** (#e145aca) + a **MoE router fix** —
  gemma-4 is now faster, not just functional. Free on the pin; confirm on-device.
- 🚧 **gemma-4-12B — registered (#0767814) + config decodes, but BLOCKED by a `vision_embedder` sanitize
  gap** (one upstream line in mlx-swift-lm `Gemma4Model.sanitize`). The 12B-unified checkpoint renamed its
  vision encoder `vision_tower`→`vision_embedder` (11 tensors); the LLM-path drop-list misses it → orphan
  keys → load throws `unhandledKeys`. A real **`huge`** candidate + all-Gemma-4 ladder (`big` e4b + `huge`
  12B, Apache 2.0) ONCE the drop-list is patched. 12B-4bit = 6.74 GB (fits the 12 GB ceiling, but gemma-4 has
  no quantized KV → tight). Verified statically (recon workflow 2026-06-24); on-device load is the gate.

**Qwen3.5 — the perf blocker is GONE (re-opens the bake-off):**
- ✅ **GatedDeltaNet prefill 2.7–14.6×** (#225 asyncEval) — scales with context = our exact symptom.
  Plus #229 (−2× SSM memory) + fp32 state (quality) + #323 recurrent-cache fixes.
- 🚫 **OptiQ still won't load** — zero OptiQ loader on main; the token-soup cause is unchanged.
- 🔬 **Net:** Qwen3.5 (4B/8B) is worth a CHATEVAL vs dense Qwen3 again — the perf blocker that made it
  "not worth measuring" is lifted. Decider = reasoning quality vs dense, on our context lengths.

**New quant + tuning machinery:**
- 🔬 **ParoQuant + AutoAWQ loader** (#164) — a real mixed-precision / AWQ-checkpoint path (≠ OptiQ);
  opens AWQ-quantized variants we couldn't load before.
- 🔬 **LoRA runtime toggle + PEFT adapter loader** (#5626257) — directly enables the long-carried
  **"knows-me" LoRA** thread: load a fine-tuned adapter at runtime over a base brain.
- ℹ️ **Audio I/O plumbing** (#a47894a) — `UserInput.audios` exists now, but there's still no Gemma audio
  *tower* in Swift (`Gemma4.swift` = "text + vision only") → **WhisperKit stays** (the STT decision holds).
- ℹ️ Quant *formats* (mxfp4/mxfp8/nvfp4) were already in 3.31.3 — no new bit-widths, just ParoQuant/AWQ.

**The shape of the level-up:** `big` becomes a fast, native-tool, Apache-2.0 Gemma 4; `huge` could become
Gemma-4-12B (one clean family, no SSM); `lil`/`huge` get a fair Qwen3.5 re-match; and the knows-me LoRA
path finally has a loader. Nothing here is claimed shipped beyond #98 — it's the now-reachable menu, each
item its own TDD'd id-swap or eval.

---

## Decision log

- **2026-06-22:** OptiQ swap reverted — `mlx-optiq` incompatible with mlx-swift-lm 3.31.3 (no Swift
  loader). Huge CPU spike root-caused to Qwen3.5 GatedDeltaNet. gemma-4-e4b no-response bug observed.
  Direction set: move off the Qwen3.5 hybrid to **dense** models; CHATEVAL to pick. See
  `.claude/project-memory.md` 2026-06-22 for the full arc.
- **2026-06-23:** Dense Qwen3 (lil/huge) shipped + verified on-device (clean prose, native tool-calling).
  Leaked-tool-call stripper shipped (spurious `<function_call>` blocks). **License axis clarified: whole
  MLX lineup is Apache 2.0** (Gemma 4 moved to Apache 2.0 Mar 2026 — *not* the custom Gemma Terms;
  corrected an earlier wrong read). **mlx-swift-lm main now has #183 (gemma-4 tools) + #330 (E-series
  load), both merged** → gemma-4-native-tools is now reachable via a main pin (spike-first, WhisperKit
  clash risk) or the next release (watch armed).
- **2026-06-23 (cont.):** **Shipped the build-off-main spike.** `mlx-swift-lm` pinned to `revision 40c2ff06`
  (main HEAD = #330 merge); `resolveToolCallFormat` flips gemma-4 `nil → .gemma4` + a `gemma4CallText` echo
  renderer (extracted a shared `gemmaStyleCallText`; Gemma 3 unchanged). TDD'd RED→GREEN; tdd-enforcer +
  code-quality + pr-reviewer all green; full suite **1525** green off main. Resolve was clean — NO
  WhisperKit/swift-transformers clash (mlx-swift-lm depends on neither); only mlx-swift-lm + swift-syntax
  (600→603, prebuilt) move. gemma-4 native tools now REACHABLE; the **no-response-bug fix is the on-device
  verify-owed**. Pin is temporary → swap to a tag when the release-watch fires.
- **2026-06-24:** **Mapped what building off main unlocks** (see the section above) + **corrected the
  06-22 GatedDeltaNet diagnosis** — the Qwen3.5 spike was the prefill *sync stall* (fixed by #225's
  asyncEval, 2.7–14.6×), NOT per-token CPU dispatch (the fused kernel was always one GPU dispatch; the
  `for t` at `GatedDelta.swift:244` is the CPU fallback). Confirmed against the local checkout: **OptiQ
  still has no Swift loader** (token-soup cause unchanged), but **ParoQuant/AWQ** (#164) is a new
  mixed-precision path; **gemma4_unified is now registered on the LLM path** (#0767814) but gemma-4-12B is
  **blocked by a one-line `vision_embedder` sanitize gap** upstream (recon 06-24 — config decodes, but the
  renamed vision tensors orphan-throw at load; on-device load is the gate); gemma-4 gained **decode +23.8%** (#82d9cd6) + MTP spec-decoding; and a **LoRA/PEFT adapter
  loader** (#5626257) lands the knows-me thread. Net: `big` = strong Gemma 4; Qwen3.5 perf blocker lifted
  (bake-off worth running again); OptiQ stays parked.
- **2026-06-24 (bake-off):** **gemma-4-12B PARKED as `huge` — verified on-device, fails on tool-use.**
  Built off the #363 pin (the `vision_embedder` sanitize fix) and ran it end-to-end. It **LOADS clean** (no
  `vision_embedder` orphan-throw — #363 works), generates coherent prose, and fits memory comfortably (**peak
  7.4 GB** active vs the 12 GB ceiling, flat across a memloop — better than the ~9 GB estimate). BUT it
  **crashes on the first tool-use fixture**: a `RotatingKVCache.temporalOrder` assertion (`Gemma4Attention →
  RotatingKVCache.update`). Proven **unfixable from M1K3's side** — it crashes under `maxKVSize=8192` AND
  `maxKVSize=nil` (the "maxKVSize forces a uniform cache" theory was disproven on-device; both configs crash).
  The bug is **12B-specific**: `big` (gemma-4-e4b) shares the same RotatingKVCache path but passed tool-use
  **5/5, no crash** — e4b has 18 KV-shared layers + PLE, the 12B has **0 KV-shared layers**, and that
  no-sharing sliding geometry is what trips `temporalOrder`. So **no `big`/production regression**, and the
  swap is blocked on an **upstream** `RotatingKVCache` bug, *separate from #363*. **Decision: `huge` stays
  Qwen3-8B.** Revisit gemma-4-12B when upstream fixes `temporalOrder` for no-KV-sharing gemma-4 (the
  release-watch will catch a tag carrying it). CHATEVAL data (the clean parts): on the
  openChat/grounded/reasoning overlap (19 fixtures) gemma-4-12B and Qwen3-8B both scored ~18/19, but
  **gemma-4-12B was far faster on reasoning** (1–7 s vs Qwen's ~57 s); Qwen3-8B finished 23/36 overall but
  **loop-thrashes on tool-use** (2/5, 130–315 s/fixture) where `big`/e4b aced it 5/5 — i.e. the *incumbent
  huge* has a tool weakness worth a future look. Side-finding: the CHATEVAL **refusal scorer was
  false-FAILing genuine refusals** (curly-apostrophe mismatch + missing "I don't <verb>" phrasings) — fixed
  in this same change (`ChatEvalScorer.isRefusal`: apostrophe normalisation + flat-decline markers).

<!-- Review: Kev + claude-opus-4-8, 2026-06-24 (bake-off), Confidence 0.9 — gemma-4-12B verified on-device
end-to-end: loads (#363) + generates + RAM 7.4 GB, but a deterministic RotatingKVCache.temporalOrder crash on
tool-use, reproduced under both maxKVSize settings and isolated to the 12B (no-KV-sharing) geometry vs e4b's
crash-free 5/5. The decision (huge stays Qwen3-8B) follows from a hard crash on core functionality, not a
quality call. Prior: Kev + claude-opus-4-8. -->

<!-- Signed: Kev + claude-opus-4-8, 2026-06-22, Confidence 0.85 (registry + repo facts verified on-device/web;
gemma-4 no-response is model-behaviour-primary with a named parse verify-owed; the dense-vs-hybrid split is the
load-bearing lens; CHATEVAL is the named decider). Prior: Unknown. -->
<!-- Review: Kev + claude-opus-4-8, 2026-06-24, Confidence 0.85 — reconciled to building off main (revision
40c2ff06): corrected the 06-22 GatedDeltaNet diagnosis (prefill sync stall, fixed by #225 — NOT per-token CPU
dispatch), added the "What building off main unlocks" map, flipped the gemma4_unified registry status. All
findings verified against the local mlx-swift-lm/mlx-swift checkouts (commit SHAs cited). The capability
claims beyond PR #98 are marked 🔬 (reachable, verify/eval-owed), not shipped. Prior: Kev + claude-opus-4-8. -->
