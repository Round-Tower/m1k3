<p align="center">
  <img src="assets/brand/readme-hero.png" alt="M1K3 — Your AI. Your Mac. Nothing leaves." width="100%">
</p>

<h1 align="center">M1K3 — Own your AI</h1>

<p align="center">
A native AI companion that runs <strong>entirely on your Apple-Silicon Mac</strong> —
local LLM inference, live voice, a personal knowledge graph with RAG, encrypted
call transcription, a local agent, and an MCP server.<br>
Edge AI you actually own: no cloud, no telemetry, no network cable it never asks for.
</p>

<p align="center">
  <a href="https://github.com/Round-Tower/m1k3/releases/latest/download/M1K3.dmg"><strong>⬇ Download for macOS</strong></a>
  · <a href="https://m1k3.app">m1k3.app</a>
  · <a href="https://testflight.apple.com/join/UCUJGbJe">TestFlight beta</a>
</p>

<p align="center"><em>Requires macOS 26 Tahoe · Apple Silicon · signed & notarized (Developer ID).</em></p>

<p align="center">
  <a href="https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml"><img src="https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/Round-Tower/m1k3/actions/workflows/security.yml"><img src="https://github.com/Round-Tower/m1k3/actions/workflows/security.yml/badge.svg" alt="Security"></a>
  <a href="https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml"><img src="https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml/badge.svg" alt="Mac review"></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Round-Tower/m1k3?color=0a0a0a&labelColor=0a0a0a" alt="Apache-2.0"></a>
  <a href="https://github.com/Round-Tower/m1k3/releases/latest"><img src="https://img.shields.io/github/v/release/Round-Tower/m1k3?color=0a0a0a&labelColor=0a0a0a&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/platform-macOS%2026%20·%20Apple%20Silicon-0a0a0a?labelColor=0a0a0a" alt="macOS 26 · Apple Silicon">
  <img src="https://img.shields.io/badge/swift-6.2%20strict-0a0a0a?labelColor=0a0a0a" alt="Swift 6.2">
  <a href="https://murphysig.dev/signed/Round-Tower/m1k3/"><img src="https://murphysig.dev/badge/Round-Tower/m1k3.svg" alt="MurphySig: signed"></a>
</p>

---

## What's inside

- **On-device inference** — MLX (Gemma, Qwen) + Apple Foundation Models. The model lives on your Mac.
- **Live voice** — speak and be spoken to; neural TTS + on-device speech-to-text.
- **Knowledge graph + RAG** — drop in notes and PDFs; M1K3 remembers and cites, locally.
- **Call memory** — encrypted, on-device call transcription.
- **A local agent** — tools that *do* things, grounded in your own data.
- **MCP server** — expose M1K3's local capabilities to Claude and other agents.

Everything above runs without leaving the device. The only network use is the
one-time model download and an optional, explicitly-enabled web search.

| Surface | Where | Stack | Status |
|---|---|---|---|
| **macOS native** | [`macos/`](./macos) | Swift 6.2, SwiftUI, MLX-Swift | **The product** — on-device knowledge · RAG · agent · voice · calls. Build it: [`macos/README.md`](./macos/README.md). |
| **間 AI mobile** | [`app/`](./app) | Kotlin Multiplatform | **Next** — Android first, iOS after. See [`app/README.md`](./app/README.md). |
| **The attic** | [`attic/`](./attic) | Python, THREE.js, Tauri | Where M1K3 grew up — the original CLI, avatar experiments, and ideas. Still boots. |

## Get M1K3

- **[TestFlight beta](https://testflight.apple.com/join/UCUJGbJe)** — the easiest way in.
- **[Download the DMG](https://github.com/Round-Tower/m1k3/releases/latest/download/M1K3.dmg)** — signed & notarized.
- **Build from source** — [`macos/README.md`](./macos/README.md): clone → `xcodegen generate` → ⌘R.

## MCP integration

The running Mac app serves MCP over HTTP at `http://127.0.0.1:4242/mcp` —
knowledge search, documents, voice, and `ask_m1k3` (ask the resident AI).
`.mcp.json` at the repo root wires Claude Code into it; setup for any client:
[`macos/docs/MCP_SETUP.md`](./macos/docs/MCP_SETUP.md).

## The attic

M1K3 didn't start as a Mac app. It started in August 2025 as a Python CLI with
a synthesized voice, grew a THREE.js avatar, a PWA, a Tauri popover, a RAG
engine, and an MCP server — and then everything it learned was rebuilt native.
That history lives in [`attic/`](./attic), runnable and signed, because a
project about provenance should keep its own. The tour: [`attic/README.md`](./attic/README.md).

## Contributing

Start with [`CONTRIBUTING.md`](./CONTRIBUTING.md). Architecture and current
state: [`CLAUDE.md`](./CLAUDE.md). Security reports: [`SECURITY.md`](./SECURITY.md).

## Privacy

Inference, retrieval, and voice run on-device. No telemetry; conversations stay
on your machine. Network is only used to download models on first run.

## License

**[Apache License 2.0](./LICENSE).** M1K3 is free and open source — use it, fork
it, build on it, commercially or otherwise. Attribution and third-party notices
are in [`NOTICE`](./NOTICE).

Contributions are accepted under the same Apache-2.0 terms (per
section 5 of the License). M1K3 is built in the open with
[MurphySig](https://murphysig.dev) provenance — the git history is signed,
human-and-AI collaboration on the record.
