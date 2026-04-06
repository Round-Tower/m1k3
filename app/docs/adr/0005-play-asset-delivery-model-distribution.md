# ADR-0005: Play Asset Delivery for Model Distribution

**Status:** PROPOSED
**Date:** 2026-04-06
**Deciders:** Kev Murphy + Claude

MurphySig: kev+claude / confidence 0.85 / 2026-04-06
Context: Mini M1K3 downloading at 0.3MB/s from HuggingFace at 22:12 in Cork

---

## Context

The current model download pipeline uses WorkManager + HttpURLConnection hitting
HuggingFace's CDN directly. This works but has real UX costs:

- **Speed is whatever HF CDN gives you** — we saw 22MB/s at 6pm, 0.3MB/s at 10pm
- **No global edge caching** — HF CDN serves from wherever, not the nearest edge
- **No Play-level resume** — our append-mode resume works but is hand-rolled
- **User sees a raw progress bar** — not integrated with system download UX
- **Truncated downloads ship silently** — caught by our 99% check now, but fragile

Play Asset Delivery (PAD) solves all of this by routing model weights through
Google's CDN — the same infrastructure that delivers billions of APKs globally,
with OS-level resume, verification, and monitoring built in.

---

## Play Asset Delivery — How It Works

An **Asset Pack** is a ZIP of arbitrary assets delivered by the Play Store.
Three delivery modes:

| Mode | When delivered | Size limit | M1K3 use |
|------|---------------|------------|----------|
| `install-time` | With APK install | 1GB total | — not needed |
| `fast-follow` | Immediately after install, background | 512MB/pack | **Mini M1K3** |
| `on-demand` | App requests at runtime | 512MB/pack | **Lil, Big** |

The app calls `assetPackManager.requestProgressFlow(packName)` which returns
a Flow of `AssetPackState` — directly replaces `WorkManager` + `HttpModelDownloadManager`
for Play Store builds.

---

## Decision

**Replace HuggingFace direct download with Play Asset Delivery for production
Play Store builds.** Keep HuggingFace WorkManager download as the sideload/debug
path (non-Play builds, direct APK installs).

### Tier mapping

**Mini M1K3 — `fast-follow` asset pack**
- Pack: `mini_m1k3` containing `Qwen_Qwen3.5-0.8B-Q4_K_M.gguf` (556MB)
- Arrives automatically after install while the user is onboarding
- By the time user reaches "Install my M1K3" → model may already be there
- Zero-wait first launch is achievable

**Lil M1K3 — `on-demand` asset pack**
- Pack: `lil_m1k3` containing `Qwen_Qwen3.5-2B-Q4_K_M.gguf` (1.33GB)
- 1.33GB > 512MB limit → **split into 3 packs**: `lil_m1k3_part1/2/3`
- App requests all three in parallel via `requestProgressFlow`
- Reassembled on device after all parts complete

**Big M1K3 — `on-demand` asset packs (split)**
- Gemma 4 E2B is 3.1GB and requires Google's license acceptance on HF
- Cannot ship through PAD without resolving the license gate question
- **Decision: Big M1K3 stays on direct HuggingFace download for now**
- When/if a license-clear GGUF variant is available, revisit
- `unsloth/gemma-4-E2B-it-GGUF` is currently accessible without HF auth (302) —
  but that may change; check licence terms before PAD submission

### Licensing check

| Model | Licence | PAD viable |
|-------|---------|------------|
| Qwen3.5-0.8B | Apache 2.0 | ✅ |
| Qwen3.5-2B | Apache 2.0 | ✅ |
| Gemma 4 E2B | Google licence (accept on HF) | ⚠️ Needs review |

---

## Implementation Plan

### Phase 1: Mini via fast-follow (low effort, high impact)

1. Create `mini_m1k3` asset pack in `app/minipacks/mini_m1k3/`
2. Add pack definition to `build.gradle.kts`:
   ```kotlin
   assetPacks += setOf(":mini_m1k3")
   ```
3. `ModelDownloadManager` gets a `PlayAssetDeliveryManager` implementation:
   ```kotlin
   interface ModelDeliveryManager {
       fun deliver(model: LlmModel): Flow<DeliveryState>
       fun isDelivered(model: LlmModel): Boolean
       fun getModelPath(model: LlmModel): String?
   }
   ```
4. `OnboardingViewModel.downloadModel` lambda dispatches to PAD for Play builds,
   HttpModelDownloadManager for debug/sideload builds — detected via
   `BuildConfig.PLAY_BUILD` flag or Koin qualifier.

### Phase 2: Lil via on-demand split packs

1. Split `Qwen_Qwen3.5-2B-Q4_K_M.gguf` (1.33GB) into 3 × ~450MB chunks
   at build time via a Gradle task
2. Three packs: `lil_m1k3_part1`, `lil_m1k3_part2`, `lil_m1k3_part3`
3. On device: stream all three parts to a temp file via `assetPackManager.getPackLocation()`
   → `Files.newInputStream()`, concatenate → final GGUF
4. Validate via SHA-256 (Play verifies pack integrity end-to-end, but belt-and-suspenders)

### Phase 3: Big M1K3 (deferred)

- Resolve Gemma 4 E2B licence for Play distribution
- Or find a licence-clear alternative for the Big tier
- Or keep Big on direct HF download indefinitely (premium/power-user path)

---

## Speed Improvement (Estimated)

| Path | Observed speed | ETA for Lil (1.33GB) |
|------|---------------|----------------------|
| HuggingFace direct (22:00) | 0.3 MB/s | ~74 minutes |
| HuggingFace direct (18:00) | 22 MB/s | ~1 minute |
| Play Asset Delivery | ~5–15 MB/s consistent | 1.5–4 minutes |

Play CDN is globally load-balanced with consistent performance. The evening
throttling that hit us tonight (0.3MB/s) simply doesn't happen on Play infra.

---

## Consequences

**Positive**
- Consistent fast downloads regardless of time of day or HF CDN state
- OS-level resume — Play handles partial downloads, no hand-rolled Range logic
- Play integrity verification — no truncated files, no 99% check hacks
- Mini M1K3 potentially pre-delivered before user even finishes onboarding
- Cleaner app architecture — model delivery is a platform concern, not app logic
- Better Play Store listing — "models delivered via Play" is a quality signal

**Negative / Constraints**
- Play Store submissions only — debug/sideload builds still need HuggingFace path
- 512MB per pack limit requires splitting Lil and Big
- Big M1K3 (Gemma 4) licence review needed before PAD
- PAD requires `com.google.android.play:asset-delivery:2.x` dependency (~500KB)
- Asset packs must be uploaded with each release — model updates = new Play release

**Not affected**
- iOS — PAD is Android-only; iOS uses direct download (or future App Store On-Demand
  Resources for small assets). No change to iOS path.
- The WorkManager download code stays as the non-Play fallback — don't delete it.

---

## Open Questions

1. **Split file reassembly**: stream-concatenate on device or use Android's
   `AssetPackLocation.assetsPath()` with pre-split chunks already named sequentially?
2. **Model updates**: when Qwen3.6 ships, does a new asset pack require a full
   Play release cycle? (Answer: yes — plan quarterly model update releases)
3. **Big M1K3 licence**: reach out to Google/unsloth to confirm Play distribution
   rights for Gemma 4 E2B quantizations
4. **`fast-follow` guarantee**: Play docs say fast-follow "typically" delivers within
   a few minutes of install — not guaranteed. Onboarding still needs the fallback
   download path for the gap window.
