# M1K3 Setup Guide

## Prerequisites

- Python 3.8+ (recommended: Python 3.10+)
- Git
- Internet connection (for initial model download)
- 2GB+ free disk space for AI models

## Installation

### 1. Clone Repository

```bash
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3
```

### 2. Create Virtual Environment (Recommended)

```bash
# Create virtual environment
python -m venv venv

# Activate it
# On macOS/Linux:
source venv/bin/activate
# On Windows:
venv\Scripts\activate
```

### 3. Install Dependencies

```bash
pip install -r requirements.txt
```

### 4. Download AI Models

```bash
# Download required models (TinyLlama, embeddings, etc.)
python src/models/loaders/download_models.py

# Check what models are available
python src/models/loaders/download_models.py --check
```

### 5. Test Installation

> **Note (legacy):** The `m1k3.py` / `cli.py` Python CLI has been archived under
> `_legacy/` (superseded by the macOS app under `macos/`). The `python m1k3.py …`
> and `python cli.py …` commands below run as `python _legacy/m1k3.py …` /
> `python _legacy/cli.py …` from the repo root.

```bash
# Test basic functionality
python _legacy/m1k3.py --no-voice

# Test with RAG system
python _legacy/m1k3.py --rag --query "How do I fix slow WiFi?"

# Test TUI interface
python _legacy/m1k3.py --tui

# Test validation suite
python -m pytest tests/python/test_rag_practical.py -v
```

## Verification

After setup, you should have:

- ✅ `models/` directory with downloaded AI models
- ✅ `knowledge/comprehensive_knowledge_base.json` (if generated)
- ✅ Working CLI: `python _legacy/m1k3.py --help`
- ✅ Working RAG: `python _legacy/m1k3.py --rag`
- ✅ Web interfaces: `web/static/rag_knowledge_viewer.html`, `web/static/rag_admin.html`

## Troubleshooting

### Models Not Downloading

```bash
# Check internet connection
ping huggingface.co

# Install/upgrade huggingface_hub
pip install --upgrade huggingface_hub transformers

# Manual download
python -c "from huggingface_hub import snapshot_download; snapshot_download('TinyLlama/TinyLlama-1.1B-Chat-v1.0', local_dir='models/TinyLlama_TinyLlama_1.1B_Chat_v1.0')"
```

### Import Errors

```bash
# Check Python version
python --version  # Should be 3.8+

# Reinstall dependencies
pip install --upgrade -r requirements.txt

# Test specific imports
python -c "import transformers, torch; print('✅ Core AI libraries OK')"
```

### Memory Issues

```bash
# Use quantized models for low-memory systems
python _legacy/m1k3.py --model-size small

# Check system resources
python -c "import psutil; print(f'RAM: {psutil.virtual_memory().available // (1024**3)} GB available')"
```

### RAG System Issues

```bash
# Test RAG components
python -m pytest tests/python/test_rag_quick_validation.py -v
```

## Optional Components

### Voice Synthesis

```bash
# Install voice dependencies
pip install TTS pydub sounddevice

# Test voice
python _legacy/m1k3.py --voice
```

### Avatar System

```bash
# Start avatar web dashboard
python _legacy/cli.py --with-avatar

# Open http://localhost:3000 in browser
```

### PWA Deployment

```bash
cd pwa-deployment/
docker-compose up --build
# Access: http://localhost:8080
```

## Development Setup

### Testing

```bash
# Run quick validation
python -m pytest tests/python/test_rag_practical.py -v

# Run comprehensive tests
python -m pytest tests/python/test_rag_quick_validation.py -v

# Run specific test suites
python -m pytest tests/ -v
```

### Knowledge Base Management

```bash
# Web-based knowledge management
open web/static/rag_admin.html  # Admin panel
open web/static/rag_knowledge_viewer.html  # Browse/search
```

## Production Deployment

### Environment Variables

```bash
# Optional configuration
export M1K3_MODEL_PATH=/path/to/models
export M1K3_KNOWLEDGE_BASE=/path/to/knowledge.json
export M1K3_VOICE_ENABLED=true
export M1K3_RAG_ENABLED=true
```

## Support

- **Documentation**: See `README.md` for full feature list
- **Issues**: Check `tests/CI_TRIAGE.md` for known CI issues
- **Testing**: Run `python -m pytest tests/python/test_rag_practical.py -v` for system validation
- **GitHub**: [Round-Tower/m1k3](https://github.com/Round-Tower/m1k3)

## Quick Start Summary

```bash
# Essential 4-step setup:
git clone https://github.com/Round-Tower/m1k3.git && cd m1k3
pip install -r requirements.txt
python src/models/loaders/download_models.py
python _legacy/m1k3.py --rag --query "Hello M1K3!"
```

**System is ready when you see:** `🎯 M1K3 RAG Engine ready!`