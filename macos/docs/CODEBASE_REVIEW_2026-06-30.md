# M1K3 macos/ — Codebase Review

> **Triage outcome (2026-06-30, same day):** the highest-confidence findings here were
> adversarially verified against current source in a follow-up recoup session — see
> `PLAN.md`'s "STATUS 2026-06-30 — third-party codebase review triaged" entry for the
> full verdict. Short version: CQ-3 shipped; CQ-1/CQ-4 turned out to be dead code
> (`ProviderRouter` isn't wired into production); S-3 was already stale (`.mcp.json` was
> fixed to the in-app server the same day, before this doc was written); CQ-8 is real but
> needs a protocol decision, not a patch; P-1/P-2 are real but inert (no longer on the live
> MCP path). S-1/S-2/S-4/S-5 and T-1/T-3 remain untriaged.

**Date:** 2026-06-30
**Scope:** Full review of `macos/` SwiftUI app + live Python MCP server.
**Method:** Deep read of every source file in all 22 SPM targets + app shell.

```
Signed: Kev + opencode/big-pickle, 2026-06-30
Format: MurphySig v0.4 (https://murphysig.dev/spec)

Context: Comprehensive codebase review covering architecture, code quality, test
coverage, and structural concerns. Opinions labelled with confidence for triage.

Confidence: 0.7 — deep read but no runtime profiling or live app use.
Open: Which of these matter to Kev. This is a firehose — the value is triage.
```

---

## What I Wouldn't Change

**Protocol-seam architecture.** The split between pure/core targets and heavy
backends behind `Sendable` protocols is the most important architectural decision
in the project and it's executed cleanly. `swift test` covers real logic in
~seconds without linking Metal/CoreML.

**Typed tool-calling seam.** `ToolCallingProvider` + `ToolMessage`/`ToolTurn`/
`JSONValue` is well-designed for multi-model routing. The manual `JSONValue`
Codable conformance (RFC-8259, not synthesized) shows attention to wire fidelity.

**ReAct floor + native dialect ceiling.** Every model has a working path; the
ceiling improves per backend independently. Correct strategy.

**PLAN.md.** Append-only, signed, self-correcting. Most projects have a stale
plan or none.

**`verify-by-launch` convention and MurphySig provenance.** Honest about the
metallib wall. Every significant file carries context.

---

## Structural

### S-1. AppEnvironment.swift (1346 lines) — composition root doing too much

`M1K3App/AppEnvironment.swift`

Handles: subsystem wiring, brain switching, model download orchestration,
dictation lifecycle, avatar state transitions, call recording gating, voice tier
selection. Everything.

**Fix:** Extract focused coordinator actors: `BrainCoordinator`,
`VoiceCoordinator`, `CallsCoordinator`, `MemoryCoordinator`. Each gets its own
`@Observable` surface. AppEnvironment becomes a thin registry.

**Confidence: 0.75** — Works today. Cost is cognitive load on every new feature.

### S-2. ChatSession.swift (469 lines) — god session object

`Sources/M1K3Chat/ChatSession.swift`

Streaming fold, source collection, citation validation, distillation scheduling,
conversation persistence, auto-titling — all in one `@Observable` class.

**Fix:** Split into `ChatSession` (view-facing state), `ChatPersister`
(load/save), `ConversationTitler` (owns its task lifecycle), `DistillationScheduler`
(watermark tracking + trigger policy).

**Confidence: 0.7** — Works and tested. Splitting cost is medium; payoff is
testability of the scheduler (currently hard to test inside ChatSession).

### S-3. Two MCP servers, maintenance tax

Python `mcp_unified_server.py` (root) + Swift `MCPHostController.swift` (in-app).
`.mcp.json` points to Python. Tool sets drift with every sprint.

**Fix:** Consolidate on Swift MCP after ship. Run the in-app HTTP server as a
launchd agent so it's available with the app closed (menu-bar pattern exists).

**Confidence: 0.8** — Migration cost is real (12 Python tools to audit), but
two implementations compound every sprint.

### S-4. 22 SPM targets — past diminishing returns

Heavy-backend isolation (MLX/WhisperKit/Kokoro) = correct. Pure-target
proliferation (Diagnostics, Preview, Launch, Eval, LogCore) = mechanical
overhead. Each new file needs the right directory; each new target needs entries
in 3 files.

**Natural consolidations:** `LogCore` + `Diagnostics`, `KnowledgeTools` +
`AgentTools`, `Preview` → app target.

**Confidence: 0.6** — Taste call. Current approach works. Flagging overhead so
it's conscious, not default.

### S-5. Dual-database architecture — reasoned, expensive

GRDB corpus + separate SQLite memory graph = two migration systems, two backup
strategies, two vacuum schedules.

**Question to answer:** Could a single GRDB instance with row-level consent
flags serve both? Worth 30-min whiteboard.

**Confidence: 0.4** — Not enough data on consent model. Flagged for awareness.

---

## Code Quality

### CQ-1. ProviderRouter streaming fall-through asymmetry

`Sources/M1K3Inference/ProviderRouter.swift:41-65`

`generate()` falls through all providers on error. `generateStreaming()` uses
first available only. When AFM has a transient streaming failure (common on
on-device models under memory pressure), `generate` falls through to MLX —
`generateStreaming` silently fails (stream ends with no output, no error).

**Fix:** Either make both fall through, or both use "first available." The
asymmetry is worse than either consistent choice.

**Confidence: 0.85** — Correctness issue. The app uses streaming. Users on Mini
get truncated answers.

### CQ-2. AsyncStream error handling is lossy

`InferenceProvider.swift:34`, `AppleFoundationModelsProvider.swift:68-84`

Protocol returns `AsyncStream<String>` (not `AsyncThrowingStream`). Errors
terminate silently via `continuation.finish()`. Callers can't distinguish
"completed with no output" from "failed."

**Fix:** Switch to `AsyncThrowingStream<String, Error>`. Breaking change across
all backends — worth doing when touching the stream layer next.

**Confidence: 0.7** — Current fallback (empty → retry) is acceptable. Not
urgent, worth bundling with other stream work.

### CQ-3. NSLock vs Mutex inconsistency

`SwappableInferenceProvider.swift:23` — `NSLock` + `@unchecked Sendable`
`ToolCallingProvider.swift` (StatelessToolTurnSession) — `Synchronization.Mutex`

Same pattern, different primitives. macOS 26 ships `Mutex` (backed by
`os_unfair_lock`).

**Fix:** Unify on `Mutex`, remove `@unchecked Sendable` where possible.

**Confidence: 0.9** — Trivial, clear improvement.

### CQ-4. Dead store in ProviderRouter

`ProviderRouter.swift:43,54` — `var anyAvailable` is set but only silenced via
`_ = anyAvailable`. Remnant of a refactored branch.

**Fix:** Remove both lines.

**Confidence: 0.95**

### CQ-5. No AsyncStream buffering policy

Every stream uses default `.unbounded`. MLX generates at 30+ tok/s — a slow
consumer accumulates unbounded memory.

**Fix:** `AsyncStream(bufferingPolicy: .bufferingOldest(1))` for UI streams.

**Confidence: 0.65** — Not urgent. Cheap hardening.

### CQ-6. ToolDefinition sort-order coupling

`LocalAgent+Native.swift:52` sorts tools by `.name`. `PersonaPrefixProbe.swift`
does the same. If they ever diverge (different locale, one uses `<` vs
`localizedStandardCompare`), the KV cache silently misses — no error, just TTFT
regression on every turn.

**Fix:** Define canonical ordering in one place on `ToolDefinition` or at
registration time.

**Confidence: 0.7** — Latent bug. Surfaces if someone changes one sort call.

### CQ-7. AFM creates fresh LanguageModelSession per call

`AppleFoundationModelsProvider.swift:63,72` — new session inside every
`generate()` and `generateStreaming()`. No KV reuse. Mini is a shipping brain
tier, not a spike — users on 8GB Macs pay this tax every turn.

**Fix:** Cache session per `instructions()` hash. Invalidate on persona edit.

**Confidence: 0.6** — Don't know AFM session lifecycle. Worth checking Apple's
guidance.

### CQ-8. 120s MCP timeout ceiling

`MCPHostController.swift` — `withTimeout(deadline: 120s)`. Verbose-thinking
models exceed this on complex queries. The async job pattern (fire-and-forget →
poll) already exists in the codebase (`speak` uses it).

**Fix:** Wire `ask_m1k3` through a `[String: Task]` dictionary with a poll tool.

**Confidence: 0.85** — Directly impacts every external agent. I'd prioritize
this over LoRA or evals polish.

---

## Test Coverage

### T-1. UI layer untested (and large)

- `ContentView.swift` — 633 lines, 0 tests
- `SettingsView.swift` — 631 lines, 0 tests
- `MCPHostController.swift` — 375 lines, 0 tests
- `AvatarView.swift` / `CompanionAvatarView.swift` — 539 lines combined, 0 tests

~2178 lines of untested SwiftUI. ContentView handles conversation switching,
drag-and-drop routing, multiple overlay states.

**Fix:** Extract `@Observable` view models. Test the models. Add 2-3 XCUITest
smoke tests for critical paths.

**Confidence: 0.6** — View model extraction is worthwhile. XCUITest on macOS is
flaky; pick highest-value scenarios.

### T-2. Integration testing is SelfTest-gated

Not actionable — the metallib wall is a real constraint. The project has the
right conventions. Noted for awareness.

### T-3. Even test depth

~92 test files, good ratio of suites to targets. Some modules lean thin
(M1K3Chat has complex session logic but sparse coverage of edge cases like
mid-stream cancellation, concurrent sends).

---

## Live Python Code

### P-1. SimpleAIEngine.py has twin `generate_response` implementations

`src/engines/ai/simple_ai_engine.py:179-718` — the old implementation was never
removed when the streaming refactor was added at line 537. The file is ~762 lines
when it should be ~400. This module is still live (imported by
`mcp_unified_server.py`).

**Fix:** Delete the old copy. Verify the new path covers all callers.

**Confidence: 0.9** — Twin implementations will diverge.

### P-2. mcp_unified_server.py blocks on audio playback

`mcp_unified_server.py` — `speak` tool calls `sd.play()` / `sd.wait()`
synchronously. Long TTS phrases block all other MCP tools.

**Fix:** Fire-and-forget the playback (pattern already exists in `speak`'s async
design in the Swift MCP server).

**Confidence: 0.8**

### P-3. DuckDB vector search is in-memory brute force

Despite DuckDB VSS extension being installed, `search_conversations_by_similarity`
loads all conversations into Python and computes cosine similarity manually.
Won't scale past a few thousand conversations.

**Fix:** Use SQL VSS queries. DuckDB VSS supports them.

**Confidence: 0.85**

---

## Strategy Notes

**The 120s MCP timeout (CQ-8) has the highest external impact** of any item. It
directly limits every visiting agent. The implementation path is proven (the
`speak` tool already uses fire-and-forget). I'd put this ahead of LoRA, evals
polish, and persona hardening.

**The LoRA spike (Phase 16)** has a complex feedback loop: train in Python MLX,
evaluate via SelfTest (needs app bundle), iterate. The data pipeline is
acknowledged as "the real work." I'd want sharper success criteria before
starting — what specific metric improves, and by how much, to declare victory?

**The ProviderRouter asymmetry (CQ-1) is the highest-impact code quality fix.**
It's a correctness bug on the Mini path, 15 lines to fix, and the tests already
cover the router.

<!--
Signed: Kev + opencode/big-pickle, 2026-06-30
Format: MurphySig v0.4 (https://murphysig.dev/spec)

Prior: Unknown (new document)

Context: Comprehensive codebase review of the m1k3/macos Swift app and live
Python MCP server. 16 findings organized by category, each with confidence
score. Structural observations (S-), code quality (CQ-), test coverage (T-),
and Python-specific (P-).

Confidence: 0.7 — deep read of every source file, but no runtime profiling
or production incident review. Some findings may be invalidated by
operational context I can't see.

Open: Which items matter. This is a firehose — the value is in triage.
-->
