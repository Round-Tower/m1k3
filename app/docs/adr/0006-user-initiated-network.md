# ADR-0006: User-Initiated Network (retire "Zero Network" stance)

**Status:** ACCEPTED
**Date:** 2026-04-19
**Deciders:** Kev Murphy + Claude

MurphySig: kev+claude / confidence 0.9 / 2026-04-19
Context: shipped WebSearchExecutor + HttpModelDownloadManager. Manifest
already grants INTERNET. Privacy test + marketing copy still claim "zero
network" — a lie. Fix the story.

---

## Context

Early 間 AI shipped with a strong narrative: *Zero network permission. 100%
local.* That was true when the app did nothing but on-device inference.
It is no longer true. Two shipped features need the network:

1. **Model downloads** — `HttpModelDownloadManager` + `ModelDownloadWorker`
   pull GGUF weights (Mini/Lil/Big tiers) from HuggingFace on first launch.
   The user triggers the download; there is no reasonable way to ship these
   inside the APK (500MB – 2GB per tier, three tiers, Play 150MB APK cap).
2. **Web search tool** — `WebSearchExecutor` (DuckDuckGo Instant Answer API)
   is one of 15 tools the LLM can invoke. The user (or the LLM acting on
   their behalf) calls it by name; searches are not speculative.

The manifest already declares `INTERNET`, `ACCESS_NETWORK_STATE`, and
`ACCESS_WIFI_STATE`. But:

- `ManifestPrivacyTest.kt` AC1–AC3 assert those permissions do **not**
  exist. The test fails on every CI run.
- User-facing copy in ~8 screens (onboarding, settings, about, eco stats,
  demo) still says *"Zero network permission"* / *"ZERO network"* /
  *"0 bytes transmitted"*. This is dishonest.
- `EcoCalculator.calculateSavings` hardcodes `bytesSent = 0` with the
  comment *"Always 0 - privacy enforcement"*, and `EcoMetricsRepository`
  throws `IllegalStateException` on non-zero bytes. The SQLDelight schema
  has a `CHECK (bytes_sent = 0)` constraint.
- `app/CLAUDE.md` line 126: *"Zero INTERNET permission in manifest."* —
  load-bearing for contributors reading the file to understand the app's
  invariants.
- No ADR documents the permission decision.

Shipping anything marketing-facing without fixing this makes us dishonest.

---

## Decision

**Retire the "zero network" stance.** Adopt a new headline and a clearer
set of invariants.

### New headline

**"Your device is the cloud."**

Support line: *"間 AI runs on your phone, not in someone else's data centre.
Network is a tool you wield, not a default."*

### New privacy invariants (enforced, not aspirational)

1. **Chat inference stays on-device.** No cloud LLM fallback, ever.
2. **Network calls are user-initiated.** Two callers only:
   `HttpModelDownloadManager` (user opts into a download) and
   `WebSearchExecutor` (user or LLM explicitly invokes the tool).
3. **No analytics, telemetry, crash reporting, or tracking SDKs.**
   Enforced at dependency-classpath level by `ManifestPrivacyTest` —
   any Firebase / Crashlytics / Sentry / Mixpanel / Segment import will
   break the test.
4. **No background network.** WorkManager downloads are user-triggered;
   no periodic sync, no auto-update, no phone-home.
5. **SQLCipher at rest.** Conversations encrypted with a per-device key.

### What we keep

The OnboardingScreen ethos copy (lines 333, 335, 336) is still true and
stays verbatim:
- *"Your conversations never leave your device."*
- *"Your data stays on your device, always."*
- *"No account. No tracking. No compromise."*

These are the strongest parts of the story. They survive the pivot.

---

## Consequences

### Positive
- Honest marketing. Users who inspect the manifest will see INTERNET and
  understand why (ADR reference, manifest comment, onboarding copy).
- Stronger test-level enforcement. Dependency-classpath audit catches
  analytics SDK creep at CI time, not at release time.
- Eco story pivots from a binary "0 bytes" claim to a more defensible
  "cloud inference bytes avoided" metric (see ADR-follow-up for
  EcoMetrics migration).

### Negative
- Loss of the punchy *"Zero network permission"* line. The new headline
  *"Your device is the cloud"* is good but different.
- The `ManifestPrivacyTest` rewrite is a visible reversal — future
  contributors will see the inverted assertions and may need context.
- `EcoMetricsRepository.bytes_sent = 0` database invariant is no longer
  defensible. Schema migration landed in a follow-up commit.
- **ML Kit transitive telemetry**: `MlKitGenAiEngine` (on-device Gemini
  Nano) pulls in `com.google.android.datatransport` via ML Kit vision
  deps. This is Google's internal batching library used by ML Kit for
  usage statistics. `ManifestPrivacyTest` allow-lists it with a comment,
  and a follow-up task tracks the audit + opt-out.

### Alternatives rejected

- **Bundle models in the APK.** Play caps APKs at ~150MB; our smallest
  tier is 484MB. Fails.
- **No web search.** Kills a tool users want. The privacy gain is illusory
  since model downloads already need network.
- **Ship with `INTERNET` but a runtime "network off" toggle.** Complexity
  for no real benefit — the honest story is that downloads and searches
  are user-initiated, which is already a user-visible toggle.

---

## Enforcement

- `AndroidManifest.xml` — inline comment above `INTERNET` cites this ADR.
- `ManifestPrivacyTest.kt` — rewritten to assert INTERNET present + no
  analytics libraries on classpath. Runs on Pixel 9a instrumented test.
- `app/CLAUDE.md` — privacy section rewritten with pointer to this ADR.
- Follow-up task: EcoMetrics schema migration (`CHECK (bytes_sent = 0)`
  removal + `cloudBytesAvoided` field + real byte tracking in
  HttpModelDownloadManager + WebSearchExecutor).
