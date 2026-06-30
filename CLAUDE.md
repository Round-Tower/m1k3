# M1K3 - Local AI Assistant

@.claude/project-memory.md

> **⚠️ Orientation (2026-06-21):** The **live product** is the Mac-native SwiftUI
> app under **`macos/`** — see `macos/PLAN.md` and `macos/.claude/project-memory.md`.
> The Python CLI documented below is **legacy** (pre-Mac-app, last meaningful work
> Jan 2026) and was archived under **`_legacy/`** in this pass. The live MCP surface
> is the Mac app's in-app HTTP server (`.mcp.json` points at `127.0.0.1:4242/mcp`
> as of 2026-06-30); `mcp_unified_server.py` is no longer launched by `.mcp.json`
> and is orphaned — it still exists at the repo root but isn't the active path.

Privacy-focused local AI with voice synthesis, RAG, 3D avatars, and CLI interfaces.

## Commands
```bash
# Run (LEGACY Python CLI — archived under _legacy/)
python _legacy/m1k3.py --no-voice      # CLI without audio
python _legacy/m1k3.py --tui --rag     # TUI with RAG
python _legacy/cli.py                   # CLI with avatar dashboard

# MLX-LM Server (Apple Silicon - see docs/MLX_SETUP.md)
mlx-env/bin/mlx_lm.server --model mlx-community/SmolLM2-135M-Instruct --port 8080
# M1K3 auto-detects MLX server on port 8080

# 3D Avatar Demo
cd src/web-avatar && npm run dev   # → http://localhost:5174/demo.html

# Standalone Popover (Tauri)
cd src/avatar-popover && cargo tauri dev

# Test
pip install duckdb             # Required for tests
python -m pytest tests/        # Run all tests
python -m pytest tests/python/test_mlx_integration.py -v --noconftest  # MLX tests

# Dependencies
pip install -r requirements.txt
python src/models/loaders/download_models.py  # Download AI models
```

## Structure
```
├── cli.py, m1k3.py           # Entry points
├── mcp_unified_server.py     # MCP server (TTS + Avatar + STT tools)
├── src/
│   ├── engines/ai/           # AI backends (ai_inference.py, smollm_engine.py)
│   ├── engines/stt/          # Speech-to-text engines
│   ├── engines/tts/          # Text-to-speech engines (Kokoro, Piper)
│   ├── rag/                   # RAG engine (m1k3_rag_engine.py)
│   ├── database/              # Vector memory, conversation storage
│   ├── web-avatar/           # THREE.js 3D avatar system
│   └── avatar-popover/       # Tauri standalone app
├── scripts/                   # Utility scripts
│   ├── avatar_server.py      # WebSocket server for avatar state
│   └── tests/                # Test scripts
├── tests/                     # pytest tests
├── app/                       # 間 AI mobile (see app/CLAUDE.md)
└── docs/plans/               # Implementation plans
```

## AI Backends (priority order, auto-fallback)
1. **MLX-LM** (Apple Silicon Metal, fastest on M-series) - via `mlx_lm.server` on port 8080
2. Universal Engine (Ollama)
3. HuggingFace Transformers (TinyLlama/SmolLM2)
4. ctransformers (GGUF quantized)
5. SimpleAIEngine (mock fallback)

## Key Files
- Embeddings: BAAI/bge-small-en-v1.5
- RAG engine: `src/rag/m1k3_rag_engine.py`
- Known issues: `BUGS.md` (speech cutoff bug)

## 3D Avatar System (NEW)

Web-based THREE.js avatar with 13 animated models:
- **Web Demo**: `src/web-avatar/demo.html` - Full controls, M1K3 design system
- **MCP App**: `src/web-avatar/mcp-app.html` - Compact iframe for Claude Desktop
- **Standalone**: `src/avatar-popover/` - Tauri app with system tray (⌘+Shift+M)

Models: Colobus (default), Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish, Mask, Fox, CesiumMan, BrainStem, Robot

## MCP Integration

`mcp_unified_server.py` provides 12 tools for Claude Desktop:
- **TTS**: speak (with emotion_hint), list_voices, set_voice, get_voice_status
- **Avatar**: get/set_avatar_state, set_avatar_emotion, set_avatar_model, list_avatar_models
- **STT**: start_voice_input, get_stt_status

## Docs
- **MLX setup**: `docs/MLX_SETUP.md` (Apple Silicon inference)
- **Falcon Mamba2**: `docs/FALCON_MAMBA2_TEST.md` (Hybrid SSM+Attention test results)
- Avatar plan: `docs/plans/mcp-avatar-integration.md`
- STT setup: `docs/STT_QUICK_START.md`
- Voice profiles: `docs/VIBEVOICE_DEMO_GUIDE.md`

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
