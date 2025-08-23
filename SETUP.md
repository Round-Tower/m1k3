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
python download_models.py

# Check what models are available
python download_models.py --check
```

### 5. Generate Knowledge Base (Optional - for RAG)

```bash
# Generate comprehensive knowledge base (1,341+ documents)
python generate_comprehensive_kb.py

# This creates knowledge/comprehensive_knowledge_base.json
```

### 6. Test Installation

```bash
# Test basic functionality
python m1k3.py --no-voice

# Test with RAG system
python m1k3.py --rag --query "How do I fix slow WiFi?"

# Test TUI interface
python m1k3.py --tui

# Test validation suite
python test_rag_practical.py
```

## Verification

After setup, you should have:

- ✅ `models/` directory with downloaded AI models
- ✅ `knowledge/comprehensive_knowledge_base.json` (if generated)
- ✅ Working CLI: `python m1k3.py --help`
- ✅ Working RAG: `python m1k3.py --rag`
- ✅ Web interfaces: `rag_knowledge_viewer.html`, `rag_admin.html`

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
python -c "from m1k3_rag_engine import M1K3RAGEngine; print('✅ RAG engine OK')"
```

### Memory Issues

```bash
# Use quantized models for low-memory systems
python m1k3.py --model-size small

# Check system resources
python -c "import psutil; print(f'RAM: {psutil.virtual_memory().available // (1024**3)} GB available')"
```

### RAG System Issues

```bash
# Regenerate knowledge base
python generate_comprehensive_kb.py

# Test RAG components
python test_rag_quick_validation.py

# Check knowledge base
python -c "import json; data=json.load(open('knowledge/comprehensive_knowledge_base.json')); print(f'Documents: {len(data[\"documents\"])}')"
```

## Optional Components

### Voice Synthesis

```bash
# Install voice dependencies
pip install TTS pydub sounddevice

# Test voice
python m1k3.py --voice
```

### Avatar System

```bash
# Start avatar web dashboard
python cli.py --with-avatar

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
python test_rag_practical.py

# Run comprehensive tests
python test_rag_quick_validation.py

# Run specific test suites
python -m pytest tests/ -v
```

### Knowledge Base Management

```bash
# Web-based knowledge management
open rag_admin.html  # Admin panel
open rag_knowledge_viewer.html  # Browse/search

# Generate specific categories
python expanded_synthetic_generator.py --category device_technology
```

## Production Deployment

### Docker

```bash
# Build container
docker build -t m1k3-ai .

# Run container
docker run -p 8080:8080 m1k3-ai
```

### Environment Variables

```bash
# Optional configuration
export M1K3_MODEL_PATH=/path/to/models
export M1K3_KNOWLEDGE_BASE=/path/to/knowledge.json
export M1K3_VOICE_ENABLED=true
export M1K3_RAG_ENABLED=true
```

## Support

- **Documentation**: See `CLAUDE.md` for full feature list
- **Issues**: Check `BUGS.md` for known issues
- **Testing**: Run `test_rag_practical.py` for system validation
- **GitHub**: [Round-Tower/m1k3](https://github.com/Round-Tower/m1k3)

## Quick Start Summary

```bash
# Essential 4-step setup:
git clone https://github.com/Round-Tower/m1k3.git && cd m1k3
pip install -r requirements.txt
python download_models.py
python m1k3.py --rag --query "Hello M1K3!"
```

**System is ready when you see:** `🎯 M1K3 RAG Engine ready!`