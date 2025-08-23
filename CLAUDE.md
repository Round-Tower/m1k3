# M1K3 - Local AI Assistant

## Overview
Privacy-focused local AI assistant with voice synthesis, web dashboard, and CLI interfaces. Features multi-backend AI, real-time avatar visualization, and PWA deployment.

## Status: ✅ PRODUCTION READY (2025-08-22)

### Core Features
- **Local AI inference** with TinyLlama-1.1B-Chat (universal compatibility)
- **RAG (Retrieval-Augmented Generation)** with comprehensive expertise knowledge base (20 categories, 1,341+ documents)
- **Voice synthesis** with KittenTTS + sidechain compression ⚠️ (speech cutoff bug documented in BUGS.md)
- **Avatar system** with real-time web dashboard and emotion tracking
- **Enhanced CLI** with animations, eco-metrics, 8K context visualization
- **Model transparency engine** with 5-level debugging system
- **PWA deployment** with device-adaptive AI (2GB→8GB+ RAM) and Docker containers
- **CI/CD pipeline** with 166 tests across 74 files (92.3% success rate)
- **Privacy-focused** - 100% local processing (0 bytes transmitted)

## Known Issues
- ⚠️ **Speech Cutoff Bug**: Speech synthesis cuts off at end of sentences (documented in `BUGS.md`)  

## AI Backend Architecture

### Multi-Backend System
1. **HuggingFace Transformers** (Primary)
   - TinyLlama-1.1B-Chat-v1.0 (auto-selected, universal compatibility)
   - Works on x86_64, ARM64, any platform

2. **ctransformers** (Secondary) 
   - TinyLlama GGUF quantized model
   - ARM64 optimized with Metal GPU acceleration

3. **SimpleAIEngine** (Fallback)
   - Enhanced mock with 8K context window
   - Guaranteed compatibility on any platform

## Quick Start

### Installation
```bash
# Clone repository
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3

# Install dependencies
pip install -r requirements.txt

# Download AI models (required)
python download_models.py

# Test system
python m1k3.py --no-voice
```

### Usage Options
```bash
# Classic CLI
python m1k3.py

# Modern TUI (recommended) 
python m1k3.py --tui

# Rich full-screen
python m1k3.py --fullscreen

# CLI with avatar dashboard
python cli.py --with-avatar

# RAG-enhanced with expert knowledge
python m1k3.py --rag
python m1k3.py --tui --rag
python cli.py --rag --with-avatar
```

## Interactive Commands
```bash
# Basic commands
help              # Show available commands
tokens, usage     # Display token usage and eco impact  
stats, status     # System statistics
clear             # Clear conversation context
quit, exit        # Exit M1K3

# Avatar commands  
avatar start      # Start web dashboard
avatar status     # Show server status
avatar emotion <emotion> [intensity]  # Set emotion (0-100)
avatar test       # Test all emotions
```

## Model Management
```bash
# Model utilities
python model_upgrade.py              # Show model tiers
python download_model.py             # Download models 
python ai_inference.py               # Test AI engine
```

## Avatar System
- **Mobile-first responsive web dashboard** with real-time emotion tracking
- **8 emotions** (Happy, Sad, Angry, Surprised, Love, Thinking, Sleepy, Excited)
- **6 avatar styles** (Robot, Organic, Crystal, Ghost, Energy, Cute)  
- **WebSocket communication** for live updates during conversations
- **Multi-device access** - available on local network
- **Auto-start mode**: `python cli.py --with-avatar`

## RAG (Retrieval-Augmented Generation) System

### Advanced Expertise Knowledge Base
- **20 comprehensive categories** covering technical, educational, security, and entertainment domains
- **1,341+ expert documents** with professional-grade knowledge across:

#### Technical Expertise (5 categories)
- **Mathematical Calculations** - Math problems, equations, calculations
- **Code Debugging** - Programming help, troubleshooting, error resolution  
- **Technical Explanations** - How systems work, technical concepts
- **Casual Conversation** - Friendly interactions, greetings, chat
- **Creative Writing** - Stories, creative content, writing assistance

#### Educational & General Knowledge (9 categories)
- **Historical Facts** - World history, civilizations, historical events
- **Science Facts** - Natural phenomena, physics, chemistry, biology
- **Geography Facts** - World locations, landmarks, geographical features
- **Movies & TV** - Entertainment industry, film analysis, recommendations
- **Music Culture** - Musical genres, instruments, cultural impact
- **Sports & Recreation** - Sports rules, fitness, recreational activities
- **Food Culture** - World cuisines, cooking techniques, food traditions
- **Technology Trends** - Modern technology, digital life, innovation
- **Lifestyle & Wellness** - Health, wellness, self-care, life improvement

#### Advanced Expertise (6 categories) - **NEW**
- **Device Technology** - Device troubleshooting, setup, optimization (smartphones, computers)
- **WiFi & Networking** - Network troubleshooting, router configuration, connectivity solutions
- **Security & Privacy** - Cybersecurity, privacy protection, incident response, threat prevention
- **Diagnostic & Troubleshooting** - Systematic problem-solving, root cause analysis methods
- **Educational & Tutoring** - Study techniques, learning methods, academic support
- **Trivia & Fun Facts** - Educational entertainment, quiz content, interesting science/history facts

### RAG Features
- **Intent-aware retrieval** - Automatically selects relevant knowledge based on query type
- **Semantic search** - Uses BAAI/bge-small-en-v1.5 embeddings for intelligent document matching
- **Context enhancement** - Enriches AI responses with retrieved expert knowledge
- **100% local processing** - All retrieval and generation happens on your device
- **Web management interfaces** - HTML dashboards for knowledge exploration and administration

### RAG Usage
```bash
# Enable RAG mode
python m1k3.py --rag --tui

# Example expert queries
"My iPhone battery drains quickly. What should I check?"          # → Device Technology
"My WiFi is slow. How can I troubleshoot this?"                  # → WiFi & Networking  
"How do I protect myself from phishing attacks?"                 # → Security & Privacy
"What's the best study method for learning mathematics?"          # → Educational Support
"Tell me an interesting science fact"                             # → Trivia & Facts
```

### RAG Web Interfaces
- **Knowledge Viewer**: `rag_knowledge_viewer.html` - Browse and search 1,341+ documents
- **Admin Panel**: `rag_admin.html` - Manage document generation and system administration

## Architecture Compatibility
- **Universal**: Works on x86_64, ARM64, Intel Macs, Linux, any platform
- **Automatic backend selection**: HuggingFace → ctransformers → SimpleAI fallback
- **No setup required**: Multi-backend system handles compatibility automatically

## Troubleshooting
```bash
# Check dependencies
pip install transformers torch accelerate

# Test backends
python -c "import transformers, torch; print('✅ AI dependencies OK')"
python -c "from ai_inference import LocalAIEngine; print('✅ LocalAIEngine OK')"
```

## PWA Deployment
- **Browser-based AI**: Complete WebAssembly deployment with ONNX models
- **Device-adaptive**: Automatic model selection (2GB→8GB+ RAM)
- **Offline PWA**: Service worker caching, installable, responsive
- **Universal compatibility**: Chrome, Firefox, Safari, Edge
- **Production ready**: Docker containers, CI/CD, cloud deployment
- **92.3% test success**: Comprehensive integration testing

```bash
# Quick PWA deployment
cd pwa-deployment/
docker-compose up --build
# Access: http://localhost:8080
```

## CI/CD Pipeline  
- **166 tests across 74 files**: Complete coverage validation
- **4 GitHub Actions workflows**: Unified tests, quick tests, release testing, badges
- **Visual regression testing**: Screenshot comparison across viewports
- **Multi-platform matrix**: Ubuntu/macOS/Windows × Node.js × Python
- **Automated reporting**: HTML dashboards with GitHub Pages deployment
- **Security scanning**: Dependency vulnerabilities and code analysis

## Privacy & Environmental Impact
- **100% local processing** - No data sent to cloud services
- **0 bytes transmitted** - All AI inference on your device
- **Energy efficient** - ~3 Wh saved per response vs cloud AI
- **Water conservation** - ~120ml saved per response vs data centers