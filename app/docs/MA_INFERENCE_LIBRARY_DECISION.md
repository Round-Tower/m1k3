# Ma (間) — Inference Library Decision

**Date:** 2026-04-05
**Status:** Under consideration
**Deciders:** Kev Murphy + Claude

---

## The Question

Should M1K3 build its own KMP inference library ("Ma") wrapping llama.cpp, or continue with/fork Llamatik?

## What Happened

We hit a wall with Llamatik. The library bundles llama.cpp b7815 (January 2025), which is 15+ months behind. Gemma 4 requires llama.cpp b8637+ (April 2, 2026). We spent a session investigating every alternative and documenting the findings.

## The Name: Ma (間)

"Ma" — the Japanese concept of negative space, the gap between. Already the M1K3 design system prefix (`MaColors`, `MaTypography`, `MaSpacing`). As an inference library, Ma is the bridge — the space between Kotlin and C, between the model and the user.

---

## Option 1: Build Ma (Our Own Library)

### What we'd build
- Thin JNI bridge (~200-300 lines of C) wrapping llama.cpp's stable C API
- CMake cross-compile via Android NDK (arm64-v8a, x86_64)
- Proper UTF-8 streaming from day one (buffer complete codepoints before JNI call)
- Lives in M1K3 monorepo initially
- Add whisper.cpp, stable-diffusion.cpp modules when needed
- iOS via cinterop when we build that target

### Pros
- **Same-day model support** — update llama.cpp to any tag, rebuild, ship
- **Fix streaming properly** — the UTF-8 JNI crash is architectural in Llamatik, unfixed across 5 releases
- **Full control** — no inherited bugs, no waiting on upstream
- **Right-sized** — carry only what we use
- **GGUF universality** — same model file on desktop M1K3 and mobile
- **Foundation** — same pattern extends to whisper.cpp, SD, future C libraries

### Cons
- **We own maintenance** — CMake toolchain, NDK updates, llama.cpp API changes
- **Initial setup** — NDK cross-compilation, CI for native libs (~1-2 days)
- **No community** — just us (but also: just us to break it)
- **Whisper/SD not free** — we build each module when we need it

### Effort estimate
- JNI bridge: 1 day
- CMake/NDK setup: 1 day
- Integration + testing: 1 day
- Total: ~3 days to replace Llamatik

---

## Option 2: Fork Llamatik, Bump llama.cpp, PR Upstream

### What we'd do
- Fork github.com/ferranpons/Llamatik
- Update llama.cpp submodule from b7815 → b8664+
- Fix JNI bridge for API changes (~1,800 builds of drift)
- Submit PR back to upstream
- If merged: ride upstream. If not: maintain fork.

### Pros
- **Whisper, SD, WASM included** — features we'll want eventually
- **KMP plumbing done** — multi-platform build already configured
- **Maven Central pipeline** — publishing infrastructure exists
- **Community** — other users benefit, potential contributors
- **Open source citizenship** — give back, build relationship with Ferran

### Cons
- **Inherit tech debt** — UTF-8 streaming bug is unfixed, silent init failures, null JNI returns
- **Maintain code we didn't write** — need to understand full codebase
- **JNI bridge rewrite** — ~1,800 builds of llama.cpp API drift means significant bridge changes anyway
- **Upstream risk** — maintainer may not merge, may disagree on direction
- **Bundled features may be stale** — whisper.cpp, SD submodules also pinned to old versions

### Effort estimate
- Fork + submodule bump: 1 hour
- JNI bridge fixes: 2-3 days (unknown API drift)
- Testing across platforms: 1 day
- PR + communication: 1 day
- Total: ~4-5 days, with more unknowns

---

## Option 3: Stay on Llamatik 0.18.2 (Status Quo)

### What we'd do
- Nothing. Keep using Llamatik with Gemma 3 270M.
- Wait for upstream to update llama.cpp.

### Pros
- Zero effort
- Works today for current model

### Cons
- No Gemma 4
- No proper streaming (simulated word-by-word)
- Accumulating workaround code
- Dependent on single maintainer who hasn't bumped llama.cpp in 15 months
- Every new model release widens the gap

---

## Option 4: LiteRT-LM (Google's New SDK)

### What we'd do
- Replace llama.cpp with Google's LiteRT-LM for on-device inference
- Use pre-converted Gemma 4 models in `.task` format

### Pros
- NPU access via QNN delegate (Qualcomm) — better power efficiency
- First-party Gemma optimization
- Google maintains the runtime
- Streaming supported

### Cons
- **No GGUF** — proprietary `.task` format, can't use same model on desktop M1K3
- **No KMP module** — need expect/actual wrappers (same as llama.cpp)
- **No desktop** — mobile only
- **Google lock-in** — their models, their format, their timeline
- **Young** — launched 2025, less battle-tested

### Why it's not the primary choice
Breaks cross-platform GGUF universality. Could be an optional Android accelerator later.

---

## Option 5: AICore / ML Kit GenAI

### Ruled out for Pixel 9a

- Pixel 9a is **NOT on Google's supported device list** for ML Kit Prompt API
- Only Pixel 9/Pro/XL/Fold (12GB+), Samsung flagships, etc.
- 9a has 8GB RAM → ships Gemini Nano XXS (internal, not exposed to devs)
- Our `MlKitAvailabilityChecker` handles this gracefully — falls back to LlamaCpp
- Could revisit if Google adds 9a support

---

## Context: What We Know About Llamatik

| Issue | Severity | Status |
|-------|----------|--------|
| UTF-8 streaming crash (JNI NewStringUTF with partial multi-byte) | Critical | Unfixed across 5 releases |
| `initGenerateModel` silently fails (no throw) | High | Unfixed |
| `generate()` returns null without nullability | High | We patched around it |
| llama.cpp b7815 (15 months stale) | High | No plans visible |
| Maintainer shipping features (whisper, SD, WASM) not updating core | Concerning | Ongoing pattern |

## Context: What We Know About llama.cpp Gemma 4

| Item | Detail |
|------|--------|
| First support | PR #21309, merged April 2, 2026 (tag b8637) |
| Latest stable | b8664 (April 4, 2026) |
| Active bugs | Segfault on longer context (#21336), tokenizer fix (#21343) |
| Audio support | NOT yet implemented |
| Maturity | Days old — bleeding edge |

## Context: Our Current Stack

- **Llamatik 0.18.2** (upgraded from 0.13.0 this session)
- **Gemma 3 270M** GGUF (175MB, works on Pixel 9a)
- **Gemma 4 E2B** GGUF downloaded (3.1GB on device) — loads but fails (llama.cpp too old)
- **LlamaCppEngine** with null-safe `stripStopTokens` and empty-response guards
- **Pixel 9a** connected via USB, 7GB usable RAM
- **Emulator** available for UI testing

---

## The Real Question

This comes down to philosophy:

**Fork Llamatik** = "Stand on others' shoulders. Accept their choices. Contribute back."

**Build Ma** = "Own the stack. Carry only what you need. Build each piece when it matters."

Both are valid. The fork gives breadth faster. Ma gives depth and control.

There's no wrong answer. There's only the one that feels right for M1K3.

---

## What's Already Done (This Session)

1. **Llamatik upgraded** 0.13.0 → 0.18.2 (API backwards-compatible, compiling clean)
2. **LlamaCppEngine hardened** — null-safe stripStopTokens, empty-response guards
3. **ClearConversationDialogTest fixed** — wrong SessionEcoStats import
4. **ChatInputBar redesigned** — glassmorphic pill, animated send button, gradient fade
5. **Bottom nav → drawer** — primary nav in drawer, full-screen chat
6. **ADR-0001 written and signed** (MurphySig 0.82 confidence)
7. **Research documented** — AICore, LiteRT-LM, fork vs build analysis in memory

## What's Next (When You Decide)

- If **Ma**: Set up the module structure, NDK/CMake, write the JNI bridge
- If **Fork**: Clone Llamatik, bump submodule, start fixing the JNI bridge
- If **Wait**: Focus on other features, revisit when Gemma 4 in llama.cpp stabilizes
- Either way: commit today's work (UI + Llamatik upgrade + bug fixes)
