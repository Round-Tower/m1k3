# M1K3 - Local AI Assistant

Privacy-focused local AI with voice synthesis, RAG, 3D avatars, and CLI interfaces.

## Commands
```bash
# Run
python m1k3.py --no-voice      # CLI without audio
python m1k3.py --tui --rag     # TUI with RAG
python cli.py                   # CLI with avatar dashboard

# 3D Avatar Demo
cd src/web-avatar && npm run dev   # → http://localhost:5174/demo.html

# Standalone Popover (Tauri)
cd src/avatar-popover && cargo tauri dev

# Test
pip install duckdb             # Required for tests
python -m pytest tests/        # Run all tests

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

## AI Backends (auto-fallback)
1. HuggingFace Transformers (TinyLlama/SmolLM2)
2. ctransformers (GGUF quantized, ARM64/Metal)
3. SimpleAIEngine (mock fallback)

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
- Avatar plan: `docs/plans/mcp-avatar-integration.md`
- STT setup: `docs/STT_QUICK_START.md`
- Voice profiles: `docs/VIBEVOICE_DEMO_GUIDE.md`
