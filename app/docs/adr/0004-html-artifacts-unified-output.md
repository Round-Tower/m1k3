# ADR-0004: HTML Artifacts — Unified Dynamic Output via WebView

**Status:** ACCEPTED
**Date:** 2026-04-06
**Deciders:** Kev Murphy + Claude

MurphySig: kev+claude / confidence 0.91 / 2026-04-06
Context: discovered while reviewing arch docs during Gemma 4 download

---

## Context

M1K3 currently has three separate, partially-overlapping pipelines for
model output:

| Pipeline | Status | Problem |
|----------|--------|---------|
| `MarkdownText` renderer | ✅ Shipped, works well | Limited to static content |
| ONNX coding module (`codingModule/`) | ❌ Unfinished, `TODO("Not yet implemented")` | Separate 120MB model, ONNX infra complexity |
| Tool calls → JSON → Compose UI | ⚠️ Skeleton wired | Requires defining every UI component type up front |

All three are solving the same underlying problem: **the model knows things that
are better experienced than read.**

Meanwhile, WebView infrastructure exists in M1K3 three times already — avatar
system, MapLibre globe, web avatar demo — with a working JS bridge pattern
(`window.GlobeBridge`, `window.AvatarBridge`).

LLMs are extraordinarily good at generating HTML/CSS/JS. It is among the
most-represented formats in training data, and modern models produce correct,
interactive HTML on first attempt with minimal prompting.

---

## Decision

**Collapse all dynamic output to a single `<artifact>` system.**

The model chooses its output format based on what serves the user best:

```
Plain response           → MarkdownText (existing, unchanged)
<artifact>...</artifact> → WebView (new, reuses existing infra)
```

A self-contained HTML document inside `<artifact>` tags is rendered in an
embedded WebView with M1K3's design tokens injected as CSS variables and a
thin `ArtifactBridge` JS API for native interactions.

### System prompt directive

```
When a response benefits from interactivity, visualization, or structure that
markdown cannot express — charts, timers, forms, calculators, data tables,
code editors — wrap a self-contained HTML document in <artifact> tags.
Inject the M1K3 design system via CSS variables already available on window.Ma.
For everything else: markdown or plain prose.
```

### ArtifactBridge API (JS → Kotlin)

```javascript
window.Ma.haptic()                    // trigger haptic feedback
window.Ma.theme                       // { bg, surface, orange, text, mono }
window.Ma.speak(text)                 // TTS via KokoroTtsEngine
window.Ma.copy(text)                  // copy to clipboard
window.Ma.close()                     // dismiss artifact, return to chat
```

### Response parser

`ArtifactParser` detects `<artifact>` blocks in streaming token output.
Partial content streams into `MarkdownText` until the opening tag is seen;
then routes to `ArtifactView` (WebView composable) for final render.

### What this unlocks immediately

| User says | Model generates | Rendered as |
|-----------|-----------------|-------------|
| "Plot my sleep this week" | Chart.js bar chart | Live interactive chart |
| "Make me a Pomodoro timer" | HTML/JS timer | Fully functional timer |
| "Show this as a sortable table" | HTML table + JS sort | Sortable, themed table |
| "Write me a function" | HTML + CodeMirror | Syntax-highlighted editor + copy |
| "Calculate my mortgage" | HTML form + JS | Working calculator |
| "Show the weather forecast" | HTML widget | Animated forecast card |

---

## Retire ONNX Coding Module

`codingModule/` contains `QwenCodingEngine` (Qwen2.5-Coder-0.5B, 120MB,
ONNX Runtime). `CodeGenerationViewModel.createEngine()` is `TODO()`.

**This module is redundant and should be deleted.**

Reasons:
1. Ma + Qwen3.5-2B (Lil M1K3) generates better code than a 120MB ONNX Coder
2. HTML artifacts make a dedicated code UI unnecessary — the model generates
   a code editor as an artifact
3. ONNX adds a separate inference stack with no benefit over Ma
4. The `codingModule/` dynamic feature was commented out of `settings.gradle`
   and never shipped

The `codingModule/` directory can be deleted in the same commit that ships
`ArtifactParser` and `ArtifactView`.

---

## Platform Fit

This is the strongest cross-platform play in the roadmap:

| Platform | Renderer | Notes |
|----------|----------|-------|
| Android | `WebView` | existing infra |
| iOS | `WKWebView` | expect/actual, same JS |
| macOS | Compose Desktop WebView | same HTML |
| Web | Native browser | HTML artifacts ARE web |

The model generates HTML once. Every platform renders it natively.
No platform-specific UI code for artifacts ever.

---

## Consequences

**Positive**
- One output format to rule dynamic content — no proliferation of tool types
- LLMs are already expert HTML generators; no prompt engineering gymnastics
- WebView infra already exists — JS bridge pattern is established
- Cross-platform for free — same artifact HTML on Android, iOS, Mac, Web
- Retire ONNX coding module — significant complexity reduction
- Infinite extensibility — any library available via CDN or bundled asset

**Negative / Risks**
- WebView has GPU contention with Filament avatar (noted in MapLibre ADR)
  → Mitigate: lazy-load WebView only when artifact is rendered
- HTML artifacts are larger than JSON tool responses for simple cases
  → Mitigate: model learns to use markdown for simple things
- Security: sandboxed WebView, no external network access, no localStorage
  → JS bridge is the only escape hatch; keep it minimal

**Out of scope**
- RCRL routing (deferred — see session notes)
- Streaming HTML partial renders (Phase 2 — render on `</artifact>` close tag)

---

## Implementation Notes

`ArtifactParser` reads the token stream. On `<artifact>`:
1. Buffer remaining tokens until `</artifact>`
2. Extract inner HTML
3. Inject CSS variables: `--ma-bg`, `--ma-surface`, `--ma-orange`, `--ma-text`
4. Load into sandboxed `WebView` (no external network, `file://` context)
5. Expose `window.Ma` bridge

For streaming: show a subtle "generating..." skeleton while buffering, then
snap to the rendered artifact on close tag. Keep it fast.

The `<artifact>` tag was chosen deliberately — it matches Claude's artifact
convention, meaning prompts and model behaviours transfer.
