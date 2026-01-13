# M1K3 - Local AI Assistant

Privacy-focused local AI with voice synthesis, RAG, and CLI interfaces.

## Commands
```bash
# Run
python m1k3.py --no-voice      # CLI without audio
python m1k3.py --tui --rag     # TUI with RAG
python cli.py                   # CLI with avatar dashboard

# Test
pip install duckdb             # Required for tests
python -m pytest tests/        # Run all tests
python -m pytest tests/python/test_rag_quick_validation.py  # Single file

# Dependencies
pip install -r requirements.txt
python src/models/loaders/download_models.py  # Download AI models
```

## Structure
```
├── cli.py, m1k3.py           # Entry points
├── src/
│   ├── engines/ai/           # AI backends (ai_inference.py, smollm_engine.py)
│   ├── engines/stt/          # Speech-to-text engines
│   ├── engines/tts/          # Text-to-speech engines
│   ├── rag/                   # RAG engine (m1k3_rag_engine.py)
│   ├── database/              # Vector memory, conversation storage
│   └── tts/                   # Voice configs, effects
├── tests/                     # pytest tests
├── app/                       # 間 AI mobile (see app/CLAUDE.md)
└── pwa-deployment/            # Browser deployment
```

## AI Backends (auto-fallback)
1. HuggingFace Transformers (TinyLlama/SmolLM2)
2. ctransformers (GGUF quantized, ARM64/Metal)
3. SimpleAIEngine (mock fallback)

## Key Files
- Embeddings: BAAI/bge-small-en-v1.5
- RAG engine: `src/rag/m1k3_rag_engine.py`
- Known issues: `BUGS.md` (speech cutoff bug)

## Docs
- STT setup: `docs/STT_QUICK_START.md`
- Voice profiles: `docs/VIBEVOICE_DEMO_GUIDE.md`
