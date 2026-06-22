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

## The hard constraint: mlx-swift-lm 3.31.3

We are pinned to **mlx-swift-lm 3.31.3** (and mlx-swift kernel 0.31.4) — both are the **latest
release**, so there is no newer tag to escape to. What runs is decided entirely by 3.31.3's
model registry (`Libraries/MLXLLM/LLMModelFactory.swift`) and which arches it actually implements.

Three independent failure axes, all seen this session:
1. **Format loadability** — OptiQ (`mlx-optiq` mixed precision) has no Swift loader → won't load
   (Qwen3.5 OptiQ → token-soup; gemma-4 OptiQ → `k_proj.weight not found`). **OptiQ is parked.**
2. **Architecture / perf** — SSM/linear-attention hybrids run a per-timestep recurrent scan
   (`GatedDelta.swift:244`) that spikes **CPU** (sequential kernel dispatch) — the Huge spike.
3. **Tool-calling dialect** — M1K3 runs a tool loop every turn. Models with a native dialect in
   `MLXToolCalling.resolveToolCallFormat` (json / xmlFunction / gemma / glm4 / lfm2) are reliable;
   the rest fall to the **prompt-ReAct floor**, which gemma-4 handles badly (see the no-response bug).

---

## The registry, sorted by what matters

### ✅ Dense attention — safe (no recurrent-scan spike), all registry-confirmed
`qwen3` (dense Qwen3, **not** `.5`), `phi3` (Phi-4-mini), `llama`, `qwen2`, `gemma3`/`gemma3_text`,
`gemma4`/`gemma4_text` (E-variants only), `glm4`, `mistral3`, `smollm3`, `cohere`, `internlm2`,
`minicpm`, `starcoder2`, `olmo2/3`, `exaone4`.

### ⚠️ SSM / hybrid — the GatedDeltaNet CPU-spike trap (avoid for interactive chat)
`qwen3_5` (current lil/huge), `qwen3_next`, **`granite` / `granitemoehybrid`** (← the plan's
"Granite 4.1" candidate is a Mamba2 hybrid — *same trap*), `falcon_h1`, `nemotron_h`, `jamba_3b`,
`minimax`. These do per-timestep recurrent scans → CPU-heavy on this runtime.

### 🚫 Registered-but-blocked / RAM-heavy
- **`gemma4_unified` is NOT in the registry** → **gemma-4-12B / 26B / 31B do NOT load** (all the
  larger Gemma 4s use `Gemma4UnifiedForConditionalGeneration`). Verified via the 12B config.json.
  This is the exact "Gate B" reason Qwen3.5-9B is in huge today.
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

## Decision log

- **2026-06-22:** OptiQ swap reverted — `mlx-optiq` incompatible with mlx-swift-lm 3.31.3 (no Swift
  loader). Huge CPU spike root-caused to Qwen3.5 GatedDeltaNet. gemma-4-e4b no-response bug observed.
  Direction set: move off the Qwen3.5 hybrid to **dense** models; CHATEVAL to pick. See
  `.claude/project-memory.md` 2026-06-22 for the full arc.

<!-- Signed: Kev + claude-opus-4-8, 2026-06-22, Confidence 0.85 (registry + repo facts verified on-device/web;
gemma-4 no-response is model-behaviour-primary with a named parse verify-owed; the dense-vs-hybrid split is the
load-bearing lens; CHATEVAL is the named decider). Prior: Unknown. -->
