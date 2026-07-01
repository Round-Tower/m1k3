# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Orientation

This directory (`macos/`) is the **live product**: M1K3, a Mac-native SwiftUI app —
a local, private AI companion with live voice, a knowledge graph, document memory,
an embedded agent, call transcription, a 3D avatar, and an MCP server. It targets
**macOS 26 (Tahoe) only** (real Liquid Glass + on-device Foundation Models) and is
written in **Swift 6.2**.

The parent `../CLAUDE.md` documents the **legacy Python CLI** (archived under
`../_legacy/`, last meaningful work Jan 2026). When working under `macos/`, this
file is the relevant one. Durable session history lives in
`.claude/project-memory.md` (read it for in-flight threads and hard-won gotchas);
the roadmap is `PLAN.md` (append-only, signed — reconcile additively, never rewrite
signed blocks).

## Build & Test

```bash
# Run the fast TDD loop — the package builds & tests business logic without Xcode.
# Heavy MLX/Metal + WhisperKit integration is env-gated OFF by default.
swift test                                   # full suite (uses swift-testing, not XCTest)
swift test --parallel                        # what CI runs
swift test --filter M1K3KnowledgeTests       # one target
swift test --filter BrainCatalogueTests      # one suite
swift test --filter "BrainCatalogueTests/recommendsForRam"   # one test

# Build a specific module / the MCP executable without the Metal storm
swift build --target M1K3EvalTests           # M1K3Eval + M1K3Inference only (~no MLX)
swift build -c release --product M1K3MCP     # the stdio MCP server binary

# The SwiftUI app shell (needs Xcode 26 / macOS 26).
# project.yml is the source of truth; M1K3.xcodeproj is a gitignored build artifact.
xcodegen generate                            # regenerate the project after adding M1K3App/ files
xcodebuild -scheme M1K3 -destination 'platform=macOS' build | xcbeautify   # always pipe through xcbeautify
```

- **CI** (`../.github/workflows/ci.yml`) runs `swift test --parallel` on a
  `macos-26` runner with `M1K3_MLX_INTEGRATION=0`, plus a curated Python smoke
  subset for the legacy tree. The app target builds via Xcode Cloud →
  TestFlight; pushing to `master` triggers that pipeline (a deliberate,
  user-gated action).
- Tests use the **swift-testing** framework (`import Testing`, `@Test`), not
  XCTest.

## The metallib wall & on-device verification

`swift test` **cannot run MLX/Metal** — the metallib only resolves inside a
built `.app` bundle. So MLX/WhisperKit code is verified two ways:

1. **Unit tests** cover the pure policy layers (routers, scorers, budget math,
   tier metadata) against fakes — `M1K3_MLX_INTEGRATION=1` enables the heavy
   integration tests that actually download a model and generate (run locally,
   off in CI).
2. **`SelfTest.swift`** (in `M1K3App/`) is the headless on-device harness. Drop
   a `~/Library/Containers/app.m1k3/Data/.m1k3-selftest.json` config file
   (keyed by env-var name), launch the built `.app`, and it runs the real
   pipeline (load / generate / RAM snapshot / TTFT / CHATEVAL) and `exit(0)`s.
   Keys: `M1K3_SELFTEST=1`, `M1K3_SELFTEST_MODEL`, `M1K3_SELFTEST_MEMLOOP=N`,
   `M1K3_SELFTEST_CHATEVAL=1` + `_CHATEVAL_BRAINS`/`_CHATEVAL_MLX_MODEL` (A/B
   override), `M1K3_SELFTEST_OUT=<container path>`. This is the cleanest verify
   path — no UI, no MCP 50s cap.

When a change touches MLX/Metal/RealityKit/voice, the convention is
**verify-by-launch**: state it as a named "verify-owed" rather than claiming it
proven from `swift test`.

## Architecture

The package is **protocol-seam first**: pure, dependency-free logic in the core
targets (so `swift test` drives TDD in seconds), with heavy backends (MLX,
WhisperKit, Kokoro/ONNX, GRDB) isolated into their own targets behind protocols
so they never weigh down the core build. The SwiftUI app (`M1K3App/`) is a thin
shell that wires concrete backends to the seams; `AppEnvironment` (+ its
`AppEnvironment+*.swift` extensions) is the composition root.

**Module map** (`Sources/`):

| Target | Role |
|---|---|
| `M1K3Knowledge` | RAG corpus: GRDB store, embeddings, hybrid (vector + FTS) search, RRF fusion, grounding gate. The knowledge primitives. |
| `M1K3Memory` | Temporal memory **graph** (atomic facts + typed edges + recursive-CTE traversal). Separate DB/consent lifecycle from the corpus. |
| `M1K3MemoryViz` | 3D memory constellation (RealityKit over a pure layout model). |
| `M1K3Inference` | The `InferenceProvider` seam + `BrainTier`. Backends are thin adapters. No external deps. |
| `M1K3Agent` | Local agent: ReAct + native tool-calling loop over the inference seam; tools injected. |
| `M1K3LanguageModel` | WWDC26 LanguageModel bridge (ADR 0001) — local mirror of Apple's FoundationModels surface + escalation-ladder policy. |
| `M1K3Eval` | Model-evals enclave: fixtures + deterministic heuristic scorer + cross-brain report (pure; the model-running half rides SelfTest). |
| `M1K3KnowledgeTools` / `M1K3AgentTools` | Knowledge-backed agent tools (search/list/get document). |
| `M1K3Chat` | RAG chat brain: embed → hybrid search → documents-first prompt → generate; multi-conversation history. |
| `M1K3Voice` | TTS (`SpeechProvider`) + transcription (`TranscriptionProvider`) seams (system AVFoundation/Speech only). |
| `M1K3MLX` | **Heavy.** MLX embeddings + Gemma/Qwen generation on Metal. Conforms to the `EmbeddingService`/`InferenceProvider` seams. |
| `M1K3WhisperKit` | **Heavy.** WhisperKit on-device transcription (CoreML). Apple Speech is the always-available fallback behind the same seam. |
| `M1K3Kokoro` | **Heavy.** Kokoro neural TTS via ONNX Runtime + G2P phonemizer. |
| `M1K3Calls` | Model-agnostic call intelligence: batch transcription + diarization + two-stage summarization protocols. |
| `M1K3Avatar` | 3D companion (RealityKit) + pure emotion/animation types + earcons. Per-clip companion USDZs as resources. |
| `M1K3MCPKit` / `M1K3MCP` | MCP server: testable tool handlers (`-Kit`) + the thin stdio executable (`M1K3MCP`) Claude spawns. |
| `M1K3Launch` | Launch-at-login (SMAppService seam) for the menu-bar companion. |
| `M1K3Preview` | Review-panel router (link/file → `ReviewTarget`); QuickLook/WKWebView renderers live in the app. |

**Brains** (`BrainTier.swift`): user picks one of four at onboarding — **Mini**
(Apple Foundation Models, instant, no download), **Lil** (`Qwen3-4B-4bit`),
**Big** (`gemma-4-e4b-it-4bit`), **Huge** (`Qwen3-8B-4bit`). `BrainBacking`
maps a tier to `appleFoundationModels` or `mlx(modelID:)`. Current model choices
and their hard-won rationale (dense Qwen3 over the Qwen3.5 GatedDeltaNet hybrid;
gemma-4-12B rejected; OptiQ parked) are in `docs/MODEL_CHOICES.md`.

**Tool-calling routing** (`LocalAgent.run`): a brain with a resolvable tool-call
format runs **native** (`runNative`); otherwise the **ReAct** floor
(`runReAct`). Qwen3 → `.json`; gemma-4 → `.gemma4` (requires the mlx-swift-lm
`main` revision pin — see `Package.swift`).

**MCP exposure (two surfaces):**
- **In-app HTTP MCP server** (`M1K3App/MCPHostController.swift`) — serves on
  `127.0.0.1:4242/mcp` while the app runs. This is the live way agents reach
  M1K3's voice/RAG/memory.
- **`M1K3MCP` stdio binary** — registered into Claude Desktop/Code; reads the
  app's sandbox store. See `docs/MCP_SETUP.md`. (Note the 50s MCP timeout —
  verbose thinking models can exceed it; test those in-app or via SelfTest.)

## Conventions specific to this repo

- **Bundle ID / log subsystem / Keychain / sandbox container are all `app.m1k3`**
  (renamed from `dev.murphysig.M1K3` on 2026-06-14 — translate any old ref on
  read). MLX models cache **inside the sandbox container**
  (`~/Library/Containers/app.m1k3/Data/Library/Caches/models/<org>/<repo>/`),
  not `~/Library/Caches`. `DEVELOPMENT_TEAM` is pinned in `project.yml` because a
  stable signing identity is load-bearing for persistent Keychain/TCC grants.
- **`Package.swift` mlx-swift-lm is temporarily pinned to a `main` revision**
  (not a tag) to get gemma-4 tool-calling; swap back to `.upToNextMinor` when
  upstream cuts a release > 3.31.3 (an armed release-watch routine pings when it
  lands). Dep bumps are probe-first (`swift package resolve`) because of the
  WhisperKit/swift-transformers `Tokenizers` clash landmine.
- **`rg -rn` is a footgun** — `-r` is `--replace`, so `-rn "pat"` rewrites every
  match to "n". Use `rg -n` (recursive is the default). This trap has bitten
  repeatedly.
- Parallel sessions share `macos/.build` and the git index — `swift build` can
  queue behind another session's `.build/.lock`. For commits, use an isolated
  worktree branched off `origin/master`; stage only your own paths
  (`git add -A` sweeps other sessions' uncommitted files).
- SwiftLint pre-commit is **advisory** (warnings/errors don't block). Pre-existing
  length/cyclomatic violations on large files are a standing "don't chase" set.
