# M1K3

[![CI](https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/ci.yml)
[![Security](https://github.com/Round-Tower/m1k3/actions/workflows/security.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/security.yml)
[![Mac review](https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml/badge.svg)](https://github.com/Round-Tower/m1k3/actions/workflows/claude-code-review-mac.yml)

**Privacy-focused local AI.** On-device inference, retrieval (RAG), voice
(TTS + STT), a 3D avatar, and an MCP server — no data leaves the device.

> **Heads up:** this is a multi-surface monorepo. The root is the **Python
> desktop CLI / MCP server**. The newest, most actively developed surface is the
> native **macOS app** under [`macos/`](./macos). The mobile app lives in
> [`app/`](./app). For the live state of anything, `CLAUDE.md` and
> `.claude/project-memory.md` lead this file.

## Surfaces

| Surface | Where | Stack | Status |
|---|---|---|---|
| **Desktop CLI / MCP** | repo root, `src/` | Python 3.12, MLX-LM | Runs. The original surface (this README). |
| **macOS native** | [`macos/`](./macos) | Swift 6.2, SwiftUI, MLX-Swift | Active MVP — on-device knowledge · RAG · agent · TTS · calls. See [`macos/PLAN.md`](./macos/PLAN.md). |
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

## License

Not yet specified — add a `LICENSE` file before distribution. (`package.json`
currently declares ISC.)
