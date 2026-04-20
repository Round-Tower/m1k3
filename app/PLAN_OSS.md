# PLAN_OSS.md — 間 AI as the King of Key-Free, Local-First AI

> An assistant that never asks for your credit card, never needs a login, never
> pings a server except when you explicitly press a button — and still answers
> better than the ones that do, because the world's knowledge has always been
> free and we just teach the model where to look.

**Status:** Draft · 2026-04-19 · Kev + Claude · Companion to [PLAN_IOS.md](PLAN_IOS.md) and [ARCHITECTURE.md](ARCHITECTURE.md)
**Scope:** Capabilities sourced entirely from open-source / zero-API-key software and services, across Android, iOS, macOS

---

## 0. TL;DR

間 AI is already on-device for inference. The moat that *nobody else* has is
expanding that guarantee outward — **every retrieval, every tool, every
capability** sourced from open-source software or public endpoints that need
zero API keys, no login, no payment plan, no telemetry terms to accept. The
world's knowledge is already free if you know the URL.

This plan lays out eight phases (~12–16 weeks) to take M1K3 from "privacy-first
LLM with a web-search tool" to "the most capable fully-open on-device assistant
that exists on iOS or Android." Each phase adds a lane — retrieval, voice,
tools, vision, documents, translation, federation, models — and every lane
passes the same four gates: **no API key, no tracking SDK, respectful rate-
limits, graceful degradation.**

Together with [PLAN_IOS.md](PLAN_IOS.md), this is the composite thesis: iOS/macOS
gives us platform integration, OSS gives us capability depth. One without the
other is a nice app. Both together is a new category.

---

## 1. The Thesis: Why OSS-First Is the Moat

Three facts:

1. **The open-source AI stack has caught up.** Whisper-small beats Google's
   STT on accents. BGE-m3 beats OpenAI's `text-embedding-ada-002` on MTEB.
   Qwen 3 7B is in Claude-3-Haiku range. PaddleOCR exceeds Google ML Kit on
   CJK text. The gap is closed or closing on every single capability an
   assistant needs.

2. **The open data web was always there.** Wikipedia / Wikidata / OSM /
   Open-Meteo / NOAA / arXiv / Crossref / Open Food Facts / USGS / MusicBrainz
   / Frankfurter / Open Library — the whole public-interest web is free to
   query, and has been for 20 years. Commercial AI assistants ignore it
   because they can't charge you twice for it.

3. **Users are waking up.** Every month another story breaks about training
   data scraped from private chats, "delete" buttons that don't, prompts
   logged and searched for marketing insights. The privacy-first pitch is no
   longer niche — it's the default preference for anyone who thinks about it
   for thirty seconds.

**What this means for M1K3:** we don't need to catch up on model quality. We
need to catch up on **capability coverage**. Today M1K3 has: chat + on-device
embedding search + DuckDuckGo fallback + 15 Android system tools. Competitors
have hundreds of integrations. We'll never have hundreds of proprietary
integrations. We can have hundreds of *open* ones.

**The pitch that wins:** "Ask me anything. I answer on your device. I look
things up on the free web when I don't know. I never call home. You're my
only customer."

---

## 2. Eight Lanes

Each lane is a vertical capability, owned by a cross-platform `commonMain`
module with thin platform adapters. All use the existing `domain/tools/`
registry + grammar-constrained tool calling.

| # | Lane | Headline capability | Phase |
|---|---|---|---|
| 1 | **Retrieval** | Fast ANN vector search over 100k+ passages, multilingual embeddings | 0 |
| 2 | **Voice** | Genuinely-offline STT, neural TTS, voice activity detection | 1 |
| 3 | **Tool garden** | 10+ no-key HTTP tools — Wikipedia, Wikidata, weather, maps, dictionary, papers | 2 |
| 4 | **Vision** | On-device OCR, VLM photo Q&A, CLIP photo search | 3 |
| 5 | **Documents** | PDF / article / audio → passage corpus | 4 |
| 6 | **Translation** | Offline MT for 50+ language pairs | 5 |
| 7 | **Federation** | RSS / ActivityPub read as personal corpus | 6 |
| 8 | **Models** | Upgraded embedding + LLM tiers, open weights only | 7 |

Lanes are additive — each one compounds the others. OCR feeds documents;
documents feed retrieval; retrieval feeds tools; tools call translation;
translation is multilingual because embeddings are. The whole is larger than
the sum.

---

## 3. Principles

1. **Zero API keys in production code.** Build-time test: `NoApiKeyAudit`
   greps source for `API_KEY` / `Bearer` / `Authorization` / `X-RapidAPI` and
   fails if any show up outside `scratch/` or documented user-side download
   manifests.
2. **No tracking SDKs.** Existing `ManifestPrivacyTest` (Android) and future
   `IpaMetadataAudit` (iOS) already enforce this. Extended list: Firebase,
   Crashlytics, Amplitude, Mixpanel, Segment, Bugsnag, AppsFlyer, Branch,
   Adjust, Sentry, Datadog, New Relic, Embrace.
3. **Respectful rate-limiting.** Every HTTP tool has a `minIntervalMs`
   property enforced client-side. Nominatim: 1000. Wikipedia: 100. Overpass:
   2000 on heavy queries. User-Agent strings identify 間 AI with a contact
   URL.
4. **User-initiated network only.** ADR-0006 already committed. Every tool
   invocation is traceable to a user message; background tool calls are
   forbidden except for downloads the user started.
5. **Graceful degradation.** Tool failure returns `ToolResult.Unavailable`
   with an honest reason. Never crash. Never fall back to a proprietary
   service silently.
6. **License is data.** Every downloaded model, dataset, or asset has a
   `LICENSE.txt` in its directory and a row in `licenses.json` at build
   time. `LicensesScreen` is accessible from Settings.
7. **Catalog over code.** Tools are declared in YAML-like `ToolCatalog`
   entries, not hand-coded `when` branches. Onboarding a new tool = add a
   catalog row + adapter + tests.

---

## 4. Phase Plan

### Phase 0 — Retrieval Foundation · 1 week

**Goal:** passages scale to 100k entries with sub-50ms ANN search in a
multilingual embedding space.

**Work:**

| Component | Move |
|---|---|
| `sqlite-vec` | Add as SQLDelight dialect extension. Create `vec0` virtual table alongside existing `Passage` table, mirror embeddings into it on write. |
| Embedding swap | BGE-m3 ONNX → replaces MiniLM-L6-v2. 568MB, multilingual, 1024-dim, long-context (8192). Download tier upgrade on first launch. |
| `EmbeddingEngineManagerImpl` | Per-engine `modelId` already in place; add `bge-m3-v1` alongside existing `minilm-l6-v2`. Passages embedded with the old model are filtered out of new searches (same safeguard `SqlDelightPassageRepository` already uses). |
| Migration | Offer one-tap re-embed: "Upgrade your notes to multilingual search (5 min)." Background `WorkManager` on Android; `BGProcessingTask` on iOS. |
| Hybrid search | Top-k ANN + keyword LIKE union, reciprocal-rank fusion. Beats pure-ANN on entity-heavy queries. |

**Exit criteria:**
- Passage search < 50ms p95 at 100k entries on Pixel 9a and iPhone 15
- Multilingual query ("what did my Spanish notes say about Barcelona?") works
  without any translation step
- Old MiniLM embeddings remain usable until user opts into re-embed
- `SqlDelightPassageRepositoryTest` extended with a 10k-row perf assertion
- Graceful degradation: sqlite-vec unavailable → fall through to linear scan

**ADR-0013:** "BGE-m3 as default embedder; sqlite-vec as ANN backbone."

### Phase 1 — Voice: Honest Offline STT · 1 week

Android's `SpeechRecognizer` quietly routes to Google on most devices. iOS'
`SFSpeechRecognizer` with `requiresOnDeviceRecognition = true` is honest.
This phase brings Android to iOS's standard.

**Work:**

| Component | Move |
|---|---|
| `whisper.cpp` | Submodule alongside `llama.cpp` in `nativeShared/`. Shares build infra. |
| `WhisperEngine` | commonMain interface. Android JNI via `whisper_jni.cpp`. iOS cinterop via `whisper_core.h`. Matches the `ma_core` split pattern. |
| Model tier | `ggml-base.en` (141MB) default. `ggml-small` (466MB) upgrade. User picks. |
| `SileroVadEngine` | Voice Activity Detection, ~2MB ONNX. Auto-stops recording when user finishes. |
| `RnnoiseEngine` | Noise suppression, ~100KB. Cleans mic input before STT. |
| `AndroidSttEngine` | Swapped for `WhisperSttEngine : SttEngine`. `SpeechRecognizer` path removed or downgraded to fallback + Settings toggle. |
| iOS `VoiceRecognizer` | Prefer `SFSpeechRecognizer(onDeviceOnly)` where available; whisper.cpp as shared fallback ensures cross-platform parity for any language iOS doesn't offer offline. |

**Exit criteria:**
- "Airplane mode test": turn off radios, chat by voice, everything works
- On-device STT measurable: network traffic monitor shows zero bytes during
  recording on both platforms
- Whisper transcription accuracy ≥ 95% on Common Voice test slice for English,
  ≥ 85% for French/German/Spanish
- `docs/adr/0014-whisper-cpp-android-stt.md`

### Phase 2 — No-Key Tool Garden · 2 weeks

Goal: 10+ tools callable via grammar-constrained tool calling, covering the
most-asked question classes with zero API keys.

**The catalog (v1):**

| Tool | Endpoint | Shape | Intents served |
|---|---|---|---|
| `wikipedia.summary` | `https://{lang}.wikipedia.org/api/rest_v1/page/summary/{title}` | `{extract, thumbnail, url}` | "Who/what/where is X?" |
| `wikidata.entity` | `https://www.wikidata.org/w/api.php?action=wbgetentities&ids=Q...` | Structured | "Population of X", "when was X born" |
| `wikidata.sparql` | `https://query.wikidata.org/sparql` | Rows | Complex factoids |
| `openmeteo.forecast` | `https://api.open-meteo.com/v1/forecast` | Hourly + daily | Weather |
| `openmeteo.historical` | `https://archive-api.open-meteo.com/v1/archive` | Historical | "Was it rainy last Tuesday?" |
| `nws.alerts` | `https://api.weather.gov/alerts/active?point={lat},{lon}` | Alerts | US severe weather |
| `nominatim.search` | `https://nominatim.openstreetmap.org/search` | Geocoding | "Where is X?", addresses |
| `nominatim.reverse` | Reverse geocoding | | "What's at these coords?" |
| `overpass.nearby` | `https://overpass-api.de/api/interpreter` | POIs | "Coffee shops near me" |
| `dictionary.define` | `https://api.dictionaryapi.dev/api/v2/entries/en/{word}` | Definitions | Vocab |
| `wiktionary.etymology` | Wiktionary API | Etymology | Word history |
| `openfoodfacts.barcode` | `https://world.openfoodfacts.org/api/v2/product/{barcode}` | Product data | "Is this gluten-free?" |
| `arxiv.search` | `http://export.arxiv.org/api/query` | Papers | Research |
| `crossref.works` | `https://api.crossref.org/works` | Academic | Citations |
| `openlibrary.book` | `https://openlibrary.org/api/books?bibkeys=ISBN:...` | Book metadata | ISBN lookup |
| `musicbrainz.recording` | `https://musicbrainz.org/ws/2/recording` | Music metadata | "Who wrote this song?" |
| `frankfurter.convert` | `https://api.frankfurter.app/latest` | Exchange rates | "How much is 50 EUR in USD?" |
| `usgs.earthquakes` | `https://earthquake.usgs.gov/fdsnws/event/1/query` | Seismic | "Any earthquakes nearby?" |
| `openaq.airquality` | `https://api.openaq.org/v2/latest` | AQ | Health |
| `nasa.apod` | `https://api.nasa.gov/planetary/apod` (DEMO_KEY ok but we use no-key mirror) | Astronomy | Daily picture |

**Architecture:**

- `ToolCatalog.yaml` (or Kotlin data classes) declares each tool: name,
  description, schema, endpoint, rate limits, auth = none.
- `HttpTool` base class in `domain/tools/services/` handles: rate limiting,
  User-Agent, retries, timeouts, JSON parsing, graceful degradation.
- `ToolCallGrammarBuilder` (already exists at `shared/domain/tools`) auto-
  generates GBNF from the catalog. No hand-written grammar per tool.
- `IntentClassifier` (already exists) gets retrained/prompt-tuned on the new
  catalog. Apple FM or Qwen Mini runs this.
- Each tool has an `HttpToolTest` that hits a recorded fixture (VCR pattern).
  CI never hits live endpoints.

**Exit criteria:**
- 10 tools shipped minimum (from the list above)
- All have fixture-backed tests
- Grammar trigger rate ≥ 60% on a benchmark of 100 factual queries
- Rate-limiter trip-wire test: hammering Nominatim at 10 req/s gets
  artificially slowed to 1 req/s
- No tool crashes on network failure; `ToolResult.Unavailable` everywhere
- Settings → Tools: user can disable any tool individually

**ADR-0015:** "No-key tool catalog; HttpTool base class."

### Phase 3 — Vision: OCR + VLM + Photo Search · 2 weeks

**3.1 OCR (universal, on-device):**

- **PaddleOCR** (Apache 2.0) ONNX models: det, cls, rec. ~30MB total per
  language, ships per-language-pack as download.
- **RapidOCR** — a lighter re-packaging; evaluate both, pick by latency.
- Replaces Android `ML Kit Text Recognition` (Google-backed) — we keep ML
  Kit as fallback but prefer the OSS path.
- iOS: same PaddleOCR ONNX via `onnxruntime-objc`. No `Vision.framework`
  dependency for text (avoids Apple-routed processing questions).
- Tool: `ocr.extract(image) → text`. Feeds document ingestion.

**3.2 VLM (photo Q&A):**

- **MiniCPM-V 3** or **Qwen 3 VL** — open VLMs, runnable via llama.cpp's
  `mtmd` or MLX. Point camera, ask question, get grounded answer.
- Model tiers: `VLM-Mini` (~1.5GB), `VLM-Lil` (~3GB). Optional downloads.
- Integration: `VisionChatUseCase` accepts `Bitmap`/`UIImage` + prompt,
  routes through existing inference backend if VLM-capable.
- Android camera flow: reuse existing `CameraX` capture, send Bitmap to VLM.
- iOS: `AVFoundation` capture → send to MLX VLM or whisper-cpp-companion
  mtmd path on Ma.

**3.3 CLIP photo search:**

- **CLIP ViT-B-32** (Apache 2.0) ONNX, ~150MB. Embeds user's photo library
  once in the background (user-initiated, opt-in). Index lives in
  `sqlite-vec`.
- Query: "Show me photos of beaches" → text-embed the query → ANN search
  photo index → return matches.
- Privacy: photo-library access is one-time opt-in, reads metadata not
  content for anything other than embedding, embeddings + filepaths stored
  locally only.

**Exit criteria:**
- OCR accuracy on CORD test set ≥ 90%
- VLM "what's in this picture?" works end-to-end on both platforms at < 5s
  latency
- CLIP photo search returns relevant hits for natural-language query on a
  test library of 500 photos
- Zero bytes to Google/Apple vision APIs during any of the above
- ADR-0016: "PaddleOCR over ML Kit / Vision.framework default"

### Phase 4 — Document Ingestion Pipeline · 1 week

Everything that can become text becomes a Passage.

**Work:**

| Source | Pipeline |
|---|---|
| PDF | **MuPDF** (AGPL → commercial license required, OR use **PDFium** BSD-3 — evaluate) or Apple `PDFKit` on iOS. Extract text + images. Images → OCR → text. |
| Web page (share sheet) | **trafilatura** logic ported to Kotlin (strip nav/ads/footer). Or `jsoup` with a readability-style ruleset. |
| Audio file | Whisper (Phase 1) → text. Works for voice memos, podcasts, lectures. |
| Image (camera or share) | PaddleOCR (Phase 3.1) → text. |
| Markdown / plain text | Existing Passage path. |
| EPUB / MOBI | **epub4j** / JEPub for Android; `NSXMLParser` route on iOS. |
| HTML | `ksoup` (jsoup port) for commonMain. |
| Office docs (.docx, .odt, .pptx) | **Apache POI** (Android only, JVM, heavy). Or export-to-PDF fallback. |

**Architecture:**

- `DocumentIngestor` interface in `domain/documents/`. Implementations per
  source type (`PdfIngestor`, `WebArticleIngestor`, `AudioIngestor`, ...).
- Pipeline: source → bytes → `DocumentIngestor.extract()` → `List<Passage>` →
  `PassageRepository.save()`. Embeddings flow via existing
  `EngineBackedPassageEmbedder`.
- Progress UI: Live Activity (iOS) / Notification (Android) for long jobs.
- Chunking: existing `PassageChunker` (domain) with source-specific hints
  (respect PDF page boundaries, paragraph boundaries, etc.).
- Metadata preserved: source URL, title, author if available, ingestion
  timestamp. Displayed as chip on retrieval hit.

**Exit criteria:**
- Five document types ingestible in first cut (PDF, HTML/article, audio,
  image-with-text, markdown)
- "Share to 間 AI" on both platforms lands any of the above into passages
- Retrieval after ingest surfaces the right chunk for a question answerable
  only from the new doc
- Source chip visible on chat response ("From: your_note.pdf")
- No third-party service called during ingestion

### Phase 5 — Offline Translation · 1 week

**Work:**

- **NLLB-200 distilled 600M** (CC-BY-NC-4.0 — evaluate against commercial
  intent) OR **Argos Translate** models (Apache 2.0) as primary. Per-pair
  models ~100MB each.
- `TranslationEngine` interface in `domain/translation/`. CTranslate2 runtime
  for Argos; ONNX Runtime can run NLLB small.
- User picks language pairs in Settings → "Language Packs". Downloads on
  demand.
- Tool: `translate.text(source, source_lang, target_lang) → text`. Callable
  by LLM via tool calling.
- **UI integration:** long-press any chat bubble → "Translate to..." as a
  native action.
- **Passage translation:** when a multilingual query hits a passage in
  another language, auto-translate the excerpt shown to the user. The
  multilingual embedder (BGE-m3) handles cross-lingual retrieval; this
  closes the user-visible loop.

**Exit criteria:**
- 5 language pairs (en↔es, en↔fr, en↔de, en↔ja, en↔zh) downloadable
- Translate a paragraph at < 3s on Pixel 9a
- Zero bytes to Google Translate / DeepL / anyone
- License compliance: if NLLB CC-BY-NC blocks commercial use, fall back to
  Argos across the board. Documented in ADR-0017.

### Phase 6 — Federation: Read-Your-Feed · 1 week

Make the user's own information streams part of the corpus.

**Work:**

- **RSS / Atom** reader. `FeedIngestor` polls user-added feeds at
  user-defined intervals (default 6h, manual refresh always available).
  Articles land as Passages with `Source.kind = FEED`.
- **ActivityPub / Mastodon** read. User enters `@handle@instance`, we read
  their **own** public posts + bookmarks via the public API. No write, no
  auth, no key for public endpoints. Writes would require OAuth — deferred.
- **Email via IMAP** (stretch). Users who want it can point 間 AI at their
  IMAP. IMAP over TLS is the protocol; no third-party API. Credentials in
  Keychain/Keystore. Purely opt-in.

**Architecture:**

- `FederatedSource` interface: `kind: FEED | MASTODON | IMAP`, credentials
  bag, polling interval, last-sync marker.
- Each source registered in a `SourcesRegistry` alongside static documents.
- Feed UI: "Add a feed" button in Documents screen, paste URL, auto-detects
  RSS/Atom/JSON Feed/Mastodon account.
- Privacy: everything stays local, no cross-posting, no analytics of feed
  engagement.

**Exit criteria:**
- Add 3 RSS feeds, refresh, articles show as passages, retrieval works
- Mastodon account read: last 50 public posts + bookmarks ingestable
- IMAP path gated behind a "Labs" toggle until hardened
- No outbound traffic beyond the user's configured endpoints

### Phase 7 — Model Upgrades Sweep · 1 week

With the lanes stable, refresh the models they depend on.

**Embedding tier:** BGE-m3 already landed in Phase 0. Add **Nomic Embed v1.5**
and **Stella 1.5B** as optional higher-quality tiers for Mac users with
plenty of RAM.

**LLM tier refresh:**

| Tier | Current | Upgrade candidate | Rationale |
|---|---|---|---|
| Mini | Qwen3-0.6B | **SmolLM3-1.7B Q4** (~1.1GB) | Better reasoning at similar latency |
| Lil | Qwen3-1.7B | Keep, OR **Phi-4-mini Q4** | Phi-4-mini is MIT, strong on reasoning |
| Big | Gemma 4 E2B | Keep, OR **Qwen 3 4B** | Apache license cleaner |
| Mega (Mac only) | — new | **Qwen 3 7B Q4** (~4.5GB) | Desktop-class quality |
| Reasoning | — new | **DeepSeek-R1-Distill-Qwen-1.5B** | `<think>` tags well-tuned |

**VLM tier:** see Phase 3.2.

**Model registry:**

- Single source of truth: `ModelCatalog.yaml` (or similar), lists every
  downloadable artifact with: URL (primary + mirror), SHA256, size,
  license, min RAM, tier, modality.
- `ModelDownloadManager` consumes this at runtime; no hardcoded URLs.
- Mirror strategy: HuggingFace primary, our own IPFS/archive.org mirror
  secondary. Resilience against HF rate-limiting or takedowns.

**Exit criteria:**
- All LLM tiers run green on their target devices with no regression in
  trigger rate, eco stats, or crash rate
- License audit: every bundled or downloadable model has a matched
  `LICENSE.txt` and Settings → Licenses entry
- ADR-0018: "Model catalog as source of truth; drop hard-coded URLs."

---

## 5. Privacy & Licensing Gates

Every addition passes four gates before merge:

**Gate 1: License.** The artifact's license is listed in
`docs/licensing/ALLOWED.md`. Unlisted licenses require an ADR that argues
the fit. Categorically allowed: MIT, Apache 2.0, BSD-2/3, MPL 2.0, Unlicense,
CC0, OFL, ISC. Conditionally allowed: LGPL (dynamic link only), CC-BY
(attribution surfaced). Excluded: AGPL for shipped code (allowed for
self-hosted dev deps), CC-BY-NC (for commercial product), anything custom
without legal review.

**Gate 2: No telemetry.** Static check that the dependency or upstream
project doesn't phone home. For models: no model call-home behaviour
possible (they're weights). For libraries: `grep` the source for known
telemetry patterns (Google Analytics URLs, Segment endpoints, etc.) before
adoption.

**Gate 3: No API key.** The integration compiles and runs with zero secrets
configured. If upstream offers a key for higher limits, we document that as
a future `Advanced` setting but ship without.

**Gate 4: Graceful degradation.** Disabling the tool / library / model at
runtime produces a clean fallback, not a crash. Test: flip the feature flag
off, run the full chat loop, confirm no error paths.

**Build-time enforcement:**

```kotlin
// composeApp/build.gradle.kts
tasks.register<NoApiKeyAudit>("noApiKeyAudit") {
    sourceDirs.setFrom(fileTree("src/commonMain"))
    sourceDirs.setFrom(fileTree("src/androidMain"))
    sourceDirs.setFrom(fileTree("src/iosMain"))
    forbiddenPatterns.set(listOf(
        "Authorization:\\s*Bearer\\s+[A-Z]{2,}", // catches real keys, not f-strings
        "X-RapidAPI-Key",
        "GOOGLE_API_KEY",
        // ...
    ))
}
tasks.named("preBuild") { dependsOn("noApiKeyAudit") }
```

Already fits the mental shape of the existing `verify16KbAlignmentDebug`
guard in `composeApp/build.gradle.kts:445`.

---

## 6. Dependency Matrix

**Libraries (bundled in binary):**

| Dep | License | Size | Lane |
|---|---|---|---|
| `sqlite-vec` | Apache 2.0 | ~500KB | Retrieval |
| `whisper.cpp` | MIT | ~8MB core | Voice |
| `silero-vad` ONNX | MIT | ~2MB | Voice |
| `rnnoise` | BSD-3 | ~100KB | Voice |
| `onnxruntime` (existing) | MIT | already shipped | Vision, Voice |
| `paddleocr-onnx` | Apache 2.0 | ~30MB / lang | Vision |
| `clip-onnx` | MIT (model: OpenAI, Apache-consistent terms) | ~150MB | Vision |
| `ksoup` | Apache 2.0 | ~1MB | Documents |
| `pdfium` | BSD-3 | ~5MB | Documents |
| `epub4j` / equivalent | Apache 2.0 | ~500KB | Documents |
| `ctranslate2` (for Argos) | MIT | ~5MB | Translation |

**Models (downloaded on demand):**

| Model | License | Size | Lane | Status |
|---|---|---|---|---|
| BGE-m3 ONNX | MIT | 568MB | Retrieval | Add |
| Nomic Embed v1.5 | Apache 2.0 | 550MB | Retrieval | Optional upgrade |
| Stella 1.5B | MIT | 3GB | Retrieval (Mac) | Optional |
| Whisper base.en | MIT | 141MB | Voice | Add |
| Whisper small | MIT | 466MB | Voice | Add (upgrade) |
| Piper voices | MIT | 60MB / voice | Voice | Add |
| PaddleOCR per-lang | Apache 2.0 | 30MB | Vision | Add |
| CLIP ViT-B-32 | MIT | 150MB | Vision | Add |
| MiniCPM-V 3 | Apache 2.0 | ~1.5GB | Vision | Optional |
| Qwen 3 VL | Apache 2.0 | 3GB+ | Vision | Optional |
| NLLB-200 600M | CC-BY-NC-4.0 | 600MB | Translation | Evaluate |
| Argos per-pair | Apache 2.0 | ~100MB | Translation | Preferred |
| SmolLM3-1.7B Q4 | Apache 2.0 | 1.1GB | LLM | Optional upgrade |
| Phi-4-mini Q4 | MIT | ~2.5GB | LLM | Optional |
| Qwen 3 4B Q4 | Apache 2.0 | 2.5GB | LLM | Optional |
| Qwen 3 7B Q4 | Apache 2.0 | 4.5GB | LLM (Mac) | Optional |
| DeepSeek-R1-Distill-Qwen-1.5B | MIT | 1GB | LLM (reasoning) | Optional |

**Services (runtime HTTP, no auth):**

| Service | Rate limit | License of data |
|---|---|---|
| Wikipedia REST | Generous (100+/s OK with UA) | CC-BY-SA + GFDL |
| Wikidata | 5 req/s ideal | CC0 |
| OpenStreetMap Nominatim | 1 req/s | ODbL |
| Overpass API | 10k req/day typical | ODbL |
| Open-Meteo | Generous | CC-BY-4.0 |
| NOAA NWS | Generous, UA required | US gov PD |
| Open Food Facts | Reasonable | ODbL + CC-BY-SA |
| arXiv | 1 req/3s for bulk | Varies per paper |
| Crossref | Email-in-UA recommended | CC0 metadata |
| Open Library | Reasonable | CC-0 |
| MusicBrainz | 1 req/s | CC0 |
| Frankfurter | Generous | ECB data (PD) |
| USGS Earthquake | Generous | US gov PD |
| OpenAQ | Reasonable | Varies |
| Free Dictionary API | Reasonable | CC-BY-SA 3.0 |

---

## 7. Risk Register

**R1 — License trap on NLLB CC-BY-NC.**
Non-commercial clause may conflict with App Store distribution. Mitigation:
Argos Translate as primary, NLLB only if explicit commercial carve-out.
Legal review before Phase 5.

**R2 — sqlite-vec maturity.**
Library is young (2024). Potential bugs at scale. Mitigation: fallback to
linear scan always present; sqlite-vec is an accelerator, not a hard
dependency.

**R3 — Model download bandwidth.**
BGE-m3 + Whisper-small + CLIP + PaddleOCR + VLM = multi-GB first-launch
downloads. Mitigation: tiered opt-in. Mini profile ships only BGE-m3 +
Whisper-base. Power users opt into the rest. Progress UI explicit about
sizes.

**R4 — Rate-limit bans.**
Violating Nominatim's 1/s will get our User-Agent banned. Mitigation:
client-side rate limiter with hard cap, exponential backoff on 429, User-
Agent names contact URL so they warn before banning.

**R5 — Endpoint disappearance.**
Any of the free services could go away, change terms, or start requiring
keys. Mitigation: `ToolCatalog` versioning + graceful degradation; if a
tool returns 401/403/404 repeatedly, auto-disable and notify user via
in-app chip (not push).

**R6 — Model licensing audit drift.**
Over time, new model upgrades may slip through without license check.
Mitigation: `ALLOWED.md` gate in CI; model-catalog PRs require the
license field filled.

**R7 — Binary size explosion.**
Bundling whisper.cpp + PaddleOCR + CLIP + Piper + sqlite-vec is 40-50MB
before any models. Mitigation: split delivery on Android (dynamic features),
on-demand resources on iOS, core app stays < 60MB base, everything else
downloads.

**R8 — OSS tool UX mismatch.**
Free services often have quirky schemas, inconsistent responses, edge-case
404s. Mitigation: `HttpTool` base class normalizes to a consistent
`ToolResult` shape; per-tool adapter owns schema eccentricities.

**R9 — Grammar bloat as catalog grows.**
20+ tools × grammar rules could slow generation. Mitigation: intent
classifier (Apple FM or Qwen Mini) narrows to ≤5 candidate tools per
query before grammar is built. Already the approach from `ChatWithToolsUseCase`.

**R10 — Users confused by capability breadth.**
"Can it do X?" — when the answer is "yes but you have to download a
600MB model first" it feels like friction. Mitigation: **"Capability
Unlocked"** onboarding moment per tool first-use. The feel matches
earning a gym badge, not a paywall.

---

## 8. Open Questions

- [ ] **Q1:** sqlite-vec vs USearch — which is the faster path? sqlite-vec is
  tighter fit to existing SQLDelight; USearch is header-only and
  platform-neutral. Benchmark both in Phase 0 spike.
- [ ] **Q2:** Bundle Whisper-base with the app (141MB always-on STT) or make
  it a download? Current inclination: **download**, because the dot-matrix
  mascot + inference core is already the core app, and not every user
  wants voice.
- [ ] **Q3:** Do we do Wikipedia via REST API or **offline Wikipedia dump**?
  Dump is ~90GB compressed, not feasible to ship. Kiwix offers a 4GB
  "Wikipedia for schools". Some users on glaciers want fully-offline fact
  lookup — Kiwix is the answer. Defer to a Labs toggle.
- [ ] **Q4:** Mastodon federation — read-only (no key needed) is great, but
  users will want to *post* too. Posting requires OAuth. Is that worth the
  UX cost for v1? Probably no.
- [ ] **Q5:** CLIP photo indexing — background opt-in indexing of user's
  photo library is powerful but expensive. Do we only index on explicit
  user request ("search my photos") and lazily build the index? Probably
  yes, avoids surprise battery hits.
- [ ] **Q6:** VLM: do we run via llama.cpp `mtmd` or MLX-VLM (iOS)? Two
  backends for two platforms. Grammar-constrained tool calling from a VLM
  is uncharted. Probably: start with free-form answers, add tool calling
  later.
- [ ] **Q7:** Translation as a tool vs. UI surface — is the LLM routing
  multilingual queries through a `translate()` tool call, or do we just use
  multilingual BGE-m3 for retrieval and let the LLM answer in user's
  language? Likely both, for different flows.
- [ ] **Q8:** Offline TTS voice branding. Kokoro (current) + Piper (new) +
  PersonalVoice (iOS). Do we let users mix and match per-voice, or pick one
  per install? UX complexity vs. joy of choice.

---

## 9. Success Metrics

| Metric | Target | Why |
|---|---|---|
| API key count in production codebase | **0** | Principle integrity |
| Tracking SDK count | **0** | Same |
| Number of tools callable | ≥ 15 by Phase 2 exit | Capability breadth |
| Tool grammar trigger rate | ≥ 60% on factual query benchmark | LLM actually uses them |
| Average tool response latency | < 500ms p95 | User perception of "smart" |
| Passage search p95 | < 50ms at 100k entries | Retrieval doesn't feel slow |
| Whisper WER (English Common Voice) | ≤ 5% | Voice quality claim holds |
| Translation BLEU vs. Google Translate | within 5 BLEU points | "Close enough to feel the same" |
| Models under non-commercial license in prod | **0** | License cleanliness |
| First-launch download (Mini profile) | ≤ 1GB | On-ramp friction |
| Crash-free rate post-OSS additions | ≥ 99.5% | Stability holds |
| Offline mode test suite pass rate | 100% | "Airplane mode works" truly |

---

## 10. Sequencing & ADRs

**ADR line-up:**

- ADR-0013: BGE-m3 as default embedder; sqlite-vec as ANN backbone (Phase 0)
- ADR-0014: whisper.cpp as Android STT default (Phase 1)
- ADR-0015: No-key tool catalog; HttpTool base class (Phase 2)
- ADR-0016: PaddleOCR over ML Kit / Vision.framework (Phase 3)
- ADR-0017: Argos Translate primary; NLLB evaluated but CC-BY-NC-gated (Phase 5)
- ADR-0018: Model catalog as source of truth; drop hardcoded URLs (Phase 7)

**Stacked PR plan:**

- PR #1: sqlite-vec integration + BGE-m3 download + hybrid search
  (Phase 0, revert-safe: old MiniLM path preserved)
- PR #2: whisper.cpp submodule + `WhisperEngine` + Android/iOS actuals +
  `SileroVadEngine` (Phase 1)
- PR #3: `HttpTool` base + first 5 tools (Wikipedia, Open-Meteo, Nominatim,
  Dictionary, Frankfurter) (Phase 2 slice 1)
- PR #4: Next 10 tools (Phase 2 slice 2)
- PR #5: PaddleOCR + OCR tool + image-to-passage ingestion (Phase 3.1 +
  Phase 4 slice)
- PR #6: Document ingestion (PDF, article, audio) (Phase 4)
- PR #7: VLM (Phase 3.2) + CLIP photo search (Phase 3.3)
- PR #8: Argos Translate (Phase 5)
- PR #9: RSS/Mastodon federation (Phase 6)
- PR #10: Model catalog refresh + ADR-0018 (Phase 7)

Each PR independently revert-safe; each lane behind a feature flag during
rollout.

---

## 11. Appendix: Tool Catalog Example Entry

Shape of a catalog entry. Lives in `domain/tools/catalog/`:

```yaml
id: openmeteo.forecast
name: "Weather forecast"
description: >
  Hourly and daily weather forecast for any location on Earth. Zero API key.
  Source: Open-Meteo (CC-BY-4.0, https://open-meteo.com/).
parameters:
  - name: latitude
    type: number
    description: Decimal degrees, -90 to 90
    required: true
  - name: longitude
    type: number
    description: Decimal degrees, -180 to 180
    required: true
  - name: days
    type: integer
    description: Forecast horizon (1-7)
    required: false
    default: 3
endpoint:
  method: GET
  url: "https://api.open-meteo.com/v1/forecast"
  query_template:
    latitude: "{latitude}"
    longitude: "{longitude}"
    daily: "temperature_2m_max,temperature_2m_min,precipitation_sum"
    forecast_days: "{days}"
rate_limit_ms: 100
user_agent: "m1k3-ai/0.1 (+https://m1k3.app)"
response_shape:
  - daily.temperature_2m_max: list<float>
  - daily.temperature_2m_min: list<float>
  - daily.precipitation_sum: list<float>
license:
  data: "CC-BY-4.0"
  code: "MIT (Open-Meteo client if any)"
  attribution_required: true
  attribution_text: "Weather data by Open-Meteo.com"
graceful_degradation: "Return ToolResult.Unavailable; LLM says 'I can't reach the weather service right now.'"
```

Benefits:

- Grammar generation is automated from the schema
- Rate limiter picks up `rate_limit_ms` at runtime
- Attribution surfaces in Settings → Data Sources automatically
- New tool = new YAML + one adapter class + two tests
- Whole catalog is searchable, exportable, documentable

---

## 12. What "King" Looks Like

A user asks 間 AI, on a train with no signal:

> "Compare the population density of Dublin and Barcelona, and translate
> the result to Irish."

No network. Here's what happens:

1. **Intent classifier** (Apple FM or Qwen Mini): "structured_fact_query +
   translate". Picks `wikidata.sparql` tool and `translate.text`.
2. **Grammar-constrained tool call**: LLM emits
   `<tool_call>{"tool":"wikidata.sparql","args":{"query":"..."}}`.
3. **No network available.** Tool returns `ToolResult.Unavailable`.
4. **Fallback:** LLM checks its own passages. User has an offline Wikipedia
   snippet for Dublin from an earlier share-to-M1K3. Population: 554,000.
5. **Barcelona not in corpus.** LLM says honestly: "I know Dublin's around
   554k in a small area — Barcelona I don't have offline. Want me to remind
   you when you're back online?"
6. User comes back online later; 間 AI re-runs Wikidata, now gets
   Barcelona's number, calls `translate.text(result, en, ga)`, delivers the
   full comparison in Irish.

All of this — classification, tool routing, Wikipedia-fact lookup,
translation, retrieval fallback, the LLM conversation itself — happens on
the user's device or against zero-key open public data. No account. No
subscription. No logs on anybody's server. Works in airplane mode to a
degree. Gracefully recovers when online.

That's the king.

---

## 13. Composite Thesis With PLAN_IOS.md

[PLAN_IOS.md](PLAN_IOS.md) brings **integration** — Siri, Widgets,
Shortcuts, Liquid Glass, PersonalVoice, Visual Intelligence.

**PLAN_OSS.md** brings **capability** — Wikipedia, Open-Meteo, OCR, VLM,
Whisper, BGE-m3, Translation, Federation.

Together:

- "Hey Siri, ask 間 what the weather's like in Cork" — AppIntent → Open-Meteo
  tool → answer in PersonalVoice.
- Point the Camera Control at a plant — Visual Intelligence handoff → local
  VLM → identify → store in personal plant journal as a Passage.
- Share a PDF paper from Safari — Share Extension → PDF ingestor → Passages
  → retrievable in chat → translatable on demand.
- Lock Screen widget: tap, speak, Whisper transcribes on-device, LLM answers
  via MLX, response streams to your AirPods in spatial audio.

Neither plan is enough alone. The combination is the category.

---

*MurphySig: kev+claude / confidence 0.80 / 2026-04-19*
*Confidence higher than PLAN_IOS.md because OSS lanes have fewer unknowns —
everything here either works in production elsewhere today or has open
reference implementations we can read. Biggest remaining risks are
licensing (R1, R6) and model download friction (R3, R10), both addressable
with discipline rather than R&D.*
