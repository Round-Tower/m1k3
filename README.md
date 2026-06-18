# M1K3 — Own your AI

### Your AI. Your Mac. Nothing leaves.

M1K3 is a native AI companion that runs **entirely on your Apple-Silicon Mac** —
local LLM inference, live voice, a personal knowledge graph with RAG, encrypted
call transcription, a local agent, and an MCP server. Edge AI you actually own:
no cloud, no telemetry, no network cable it never asks for.

### [⬇ Download for macOS](https://github.com/Round-Tower/m1k3/releases/latest/download/M1K3.dmg) · [m1k3.app](https://m1k3.app)

*Requires macOS 26 Tahoe · Apple Silicon · signed & notarized (Developer ID).*

[![CI](https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml)
[![Security](https://github.com/Round-Tower/m1k3/actions/workflows/security.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/security.yml)
[![Mac review](https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml)

## What's inside

- **On-device inference** — MLX (Gemma, Qwen) + Apple Foundation Models. The model lives on your Mac.
- **Live voice** — speak and be spoken to; neural TTS + on-device speech-to-text.
- **Knowledge graph + RAG** — drop in notes and PDFs; M1K3 remembers and cites, locally.
- **Call memory** — encrypted, on-device call transcription.
- **A local agent** — tools that *do* things, grounded in your own data.
- **MCP server** — expose M1K3's local capabilities to Claude and other agents.

Everything above runs without leaving the device. The only network use is the
one-time model download and an optional, explicitly-enabled web search.

> **Repo map:** this is a multi-surface monorepo. The flagship is the native
> **macOS app** under [`macos/`](./macos). The repo root is the original **Python
> desktop CLI / MCP server** (documented below). The mobile app lives in
> [`app/`](./app). For the live state of anything, `CLAUDE.md` and
> `.claude/project-memory.md` lead this file.

| Surface | Where | Stack | Status |
|---|---|---|---|
| **macOS native** | [`macos/`](./macos) | Swift 6.2, SwiftUI, MLX-Swift | **Flagship** — on-device knowledge · RAG · agent · voice · calls. See [`macos/PLAN.md`](./macos/PLAN.md). |
| **Desktop CLI / MCP** | repo root, `src/` | Python 3.12, MLX-LM | Runs. The original surface (documented below). |
| **間 AI mobile** | [`app/`](./app) | Kotlin Multiplatform | Active — Android first, iOS next. See [`app/README.md`](./app/README.md). |

---

# Desktop CLI / MCP server

The rest of this README covers the Python surface at the repo root.

## Quick start

```bash
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3
pip install -r requirements.txt

# Download local models (AI + embeddings + voice)
python src/models/loaders/download_models.py

# Run
python m1k3.py --no-voice        # CLI, no audio
python m1k3.py --tui --rag       # TUI with retrieval
python cli.py                    # CLI with the avatar dashboard
```

**Apple Silicon (recommended):** M1K3 auto-detects an MLX-LM server on port 8080
and prefers it. Start one with:

```bash
mlx-env/bin/mlx_lm.server --model mlx-community/SmolLM2-135M-Instruct --port 8080
```

See [`docs/MLX_SETUP.md`](./docs/MLX_SETUP.md) for the full setup.

## AI backends

Backends are tried in priority order with automatic fall-through, so M1K3 always
runs — even with nothing installed (the mock fallback answers).

1. **MLX-LM** — Apple Silicon (Metal), fastest on M-series; via `mlx_lm.server` on `:8080`
2. **Ollama** — universal local API
3. **HuggingFace Transformers** — TinyLlama / SmolLM2
4. **ctransformers** — GGUF quantized models
5. **SimpleAIEngine** — dependency-free mock fallback

## Features

- **RAG** — hybrid retrieval over a local knowledge base. Engine:
  [`src/rag/m1k3_rag_engine.py`](./src/rag/m1k3_rag_engine.py). Embeddings:
  `BAAI/bge-small-en-v1.5`.
- **Voice (TTS)** — content-aware synthesis (Kokoro / Piper) that adapts to
  thinking vs. answer vs. narration. Engines in `src/engines/tts/`.
- **Voice input (STT)** — multi-engine with fallbacks: macOS native (0 MB),
  Vosk (offline), Web Speech, Whisper. Engines in `src/engines/stt/`.
- **3D avatar** — THREE.js companion with 13 animated models and emotion state.
  Web demo: `cd src/web-avatar && npm run dev` → <http://localhost:5174/demo.html>.
  Standalone Tauri popover: `cd src/avatar-popover && cargo tauri dev` (⌘⇧M).
- **MCP server** — exposes M1K3 to Claude Desktop (below).

## MCP integration

[`mcp_unified_server.py`](./mcp_unified_server.py) provides 12 tools to Claude
Desktop:

- **TTS** — `speak` (with `emotion_hint`), `list_voices`, `set_voice`, `get_voice_status`
- **Avatar** — `get`/`set_avatar_state`, `set_avatar_emotion`, `set_avatar_model`, `list_avatar_models`
- **STT** — `start_voice_input`, `get_stt_status`

```bash
# Register in your Claude Desktop MCP config
python mcp_unified_server.py
```

## Layout

```
m1k3/
├── m1k3.py, cli.py            # Desktop entry points
├── mcp_unified_server.py      # MCP server (TTS + Avatar + STT)
├── src/
│   ├── engines/ai/            # AI backends (MLX, SmolLM2, …)
│   ├── engines/tts/           # Text-to-speech (Kokoro, Piper)
│   ├── engines/stt/           # Speech-to-text engines
│   ├── rag/                   # RAG engine
│   ├── database/              # Vector memory, conversation storage
│   ├── web-avatar/            # THREE.js 3D avatar
│   └── avatar-popover/        # Tauri standalone app
├── macos/                     # macOS native MVP (Swift) — see macos/PLAN.md
├── app/                       # 間 AI mobile (KMP) — see app/README.md
├── tests/                     # pytest suite (see tests/CI_TRIAGE.md)
└── docs/                      # MLX, STT, voice, plans
```

## Development

```bash
pip install -r requirements.txt
python -m pytest tests/                 # full suite (needs heavy deps)
pip install -r requirements-ci.txt      # slim CI deps
```

The full Python suite needs the heavy ML stack and is partly legacy; CI runs a
curated green subset. See [`tests/CI_TRIAGE.md`](./tests/CI_TRIAGE.md) for what's
in CI and the rehabilitation backlog, and
[`.github/workflows/README.md`](./.github/workflows/README.md) for the pipeline.

Architecture and current state: [`CLAUDE.md`](./CLAUDE.md).

## Privacy

Inference, retrieval, and voice run on-device. No telemetry; conversations stay
on your machine. Network is only used to download models on first run.

## License & Access

**Proprietary — all rights reserved.** M1K3 is a private, invitation-only
project. There is no general public grant: absent a written grant of access from
the Owner, you may not access, copy, use, or redistribute the Software. See
[`LICENSE`](./LICENSE) for the full terms.

If you've been invited (or want to be), start with [`ACCESS.md`](./ACCESS.md)
for how access works, and [`ACCESS_AGREEMENT.md`](./ACCESS_AGREEMENT.md) for the
terms every Authorized User accepts.
