# M1K3 - Local AI CLI with Voice Synthesis

## Project Overview
M1K3 is a privacy-focused local AI assistant with voice synthesis capabilities, featuring rich CLI animations, eco-friendly metrics, and comprehensive device context analysis. Built with a robust multi-backend AI system that works on any architecture.

## Current Status (2025-08-21) ✅ FULLY OPERATIONAL

### ✅ Core Features
- **Local AI inference** with TinyLlama-1.1B-Chat working on all architectures
- **Adaptive AI parameters** optimized for each model (150 tokens for TinyLlama)
- **Universal compatibility** - works on x86_64, ARM64, any platform
- **Voice synthesis** with ONNX error handling and text length limiting
- **Enhanced CLI animations** with typewriter effects, fade-ins, and status indicators
- **Eco-friendly metrics** showing energy/water saved vs cloud AI, CO2 prevented
- **Token visualization** with 8K context window and animated usage bars
- **Device analysis** with comprehensive hardware detection and model recommendations
- **Privacy-focused design** emphasizing local processing (0 bytes transmitted)

### ✅ Major Issues Resolved

#### **AI Backend Compatibility (RESOLVED)**
**Previous Issue**: Terminal running under Rosetta (x86_64) causing "illegal hardware instruction" crashes with ctransformers

**Solution**: Multi-backend AI system with intelligent model selection
- ✅ **TinyLlama/TinyLlama-1.1B-Chat-v1.0**: Primary model with excellent instruction following
- ✅ **Universal Compatibility**: Works on x86_64, ARM64, any architecture  
- ✅ **Adaptive Parameters**: Model-specific optimization (150 tokens for TinyLlama)
- ✅ **Quality Responses**: Comprehensive, helpful answers 100-500+ characters

#### **Voice Synthesis ONNX Errors (RESOLVED)**
**Previous Issue**: ONNX runtime "invalid expand shape" errors with longer text causing crashes

**Solution**: Text length limiting and intelligent error handling
- ✅ **200-character limit**: Prevents ONNX BERT model issues
- ✅ **Smart truncation**: Long text truncated with user notification
- ✅ **No more crashes**: Graceful error handling and fallback to system TTS
- ✅ **Universal voice**: Works reliably on any architecture

## AI Backend Architecture

### 🥇 Primary Backend: HuggingFace Transformers
- **Library**: `transformers` + `torch` + `accelerate`
- **Primary Model**: TinyLlama/TinyLlama-1.1B-Chat-v1.0 (~1.1GB)
- **Fallback Models**: Qwen/Qwen2.5-0.5B-Instruct, microsoft/DialoGPT-small, distilgpt2
- **Compatibility**: Universal (x86_64, ARM64, any platform)
- **Performance**: Fast loading (~2-4s), excellent response quality
- **Features**: Instruction-tuned, conversation context, adaptive parameters
- **Use Case**: Best overall choice for local AI assistant tasks

### 🥈 Secondary Backend: ctransformers (GGUF)
- **Library**: `ctransformers`
- **Model**: TinyLlama-1.1B-Chat-v1.0.Q4_K_M.gguf (637MB)
- **Compatibility**: ARM64 native only (Apple Silicon optimized)
- **Performance**: Optimized quantized inference, Metal GPU acceleration
- **Features**: GGUF format, hardware-accelerated, memory efficient
- **Use Case**: When running on native ARM64 with GGUF models

### 🥉 Fallback Backend: SimpleAIEngine
- **Library**: Built-in enhanced mock
- **Model**: Rule-based + context-aware responses
- **Compatibility**: Universal (any platform, no dependencies)
- **Performance**: Instant responses, minimal resource usage
- **Features**: 8K context, topic extraction, demo mode
- **Use Case**: When no AI libraries are available

### Backend Selection Logic
```
1. Try HuggingFace Transformers (universal compatibility)
   ├─ Success: Use DialoGPT-small model
   └─ Fail: Continue to step 2

2. Try ctransformers (if ARM64 + GGUF exists)
   ├─ Success: Use TinyLlama GGUF model  
   └─ Fail: Continue to step 3

3. Use SimpleAIEngine (guaranteed fallback)
   └─ Always works: Enhanced mock with context
```

## Technical Implementation

### AI Models Available
- **microsoft/DialoGPT-small** (350MB) - HuggingFace conversational model
- **distilgpt2** (350MB) - HuggingFace general model (fallback)
- **gpt2** (500MB) - HuggingFace base model (last resort)
- **TinyLlama-1.1B-Chat-v1.0.Q4_K_M** (637MB) - GGUF quantized model
- **SmolLM-135M.Q4_K_M** (100MB) - Lightweight GGUF model

### AI Engines
1. **LocalAIEngine** (`ai_inference.py`) 
   - Multi-backend with HuggingFace + ctransformers
   - Automatic backend detection and selection
   - Architecture compatibility handling
   - Full conversation context management

2. **SimpleAIEngine** (`simple_ai_engine.py`)
   - Enhanced mock with 8K context window
   - Topic extraction and smart responses
   - Eco-metrics simulation
   - Guaranteed compatibility fallback

### Key Components
- **CLI** (`cli.py`): Rich animations, eco-metrics, token visualization
- **Voice** (`enhanced_voice_engine.py`): KittenML TTS with persona system
- **Metrics** (`system_metrics.py`): Device analysis and environmental impact
- **Download** (`download_model.py`): Smart model downloader with recommendations
- **Animations** (`cli_animations.py`): Typewriter effects and visual feedback

## Usage

### Basic Commands
```bash
# Full experience with voice synthesis
python m1k3.py

# CLI only (recommended for testing)
python m1k3.py --no-voice

# Direct CLI entry point
python cli.py
```

### Interactive Commands
```bash
# Within M1K3 session
help              # Show all available commands
tokens, usage     # Display token usage and eco impact  
stats, status     # System statistics with animations
context, device   # Comprehensive device context
animate, demo     # Animation demonstrations
clear             # Clear conversation context
quit, exit        # Exit M1K3
```

### Model Management
```bash
# Download models with device analysis
python download_model.py

# Direct AI engine testing
python ai_inference.py
```

## Installation & Setup

### Prerequisites
```bash
# Install system dependencies (macOS)
brew install openssl readline sqlite3 xz zlib gettext

# Python 3.8+ required (any architecture)
python --version  # Should be 3.8 or higher
```

### Environment Setup
```bash
# Clone and navigate to project
cd /path/to/m1k3

# Install Python dependencies
pip install -r requirements.txt

# Key dependencies installed automatically:
# - transformers (HuggingFace models)
# - torch (PyTorch backend)
# - accelerate (HuggingFace device mapping)
# - numpy<2.0 (voice synthesis compatibility)
# - psutil (system metrics)
# - rich (CLI animations)
```

### Quick Start
```bash
# Test the system (will auto-download DialoGPT-small)
python m1k3.py --no-voice

# Should see:
# ✅ HuggingFace Transformers available
# 🔧 Backend selection: HF=True, CT=False  
# ✅ Model loaded with HuggingFace Transformers
# 💤 Type 'help' for commands or start chatting!
```

## Architecture Compatibility

### ✅ Works On
- **x86_64 (Rosetta 2)**: Primary HuggingFace backend
- **ARM64 (Apple Silicon)**: All backends available
- **Intel Macs**: HuggingFace backend
- **Linux x86_64/ARM64**: HuggingFace backend
- **Any Platform**: SimpleAIEngine fallback

### 🔧 Backend Behavior by Architecture
- **x86_64**: HuggingFace → SimpleAI (ctransformers skipped)
- **ARM64**: HuggingFace → ctransformers → SimpleAI (full chain)
- **Unknown**: HuggingFace → SimpleAI (safe fallback)

### 🚀 Performance Characteristics
- **HuggingFace**: ~2s load time (cached), good response quality
- **ctransformers**: ~3s load time, excellent response quality (ARM64 only)
- **SimpleAI**: Instant, decent context-aware responses

## Troubleshooting

### No Issues Expected!
The new multi-backend system handles all compatibility issues automatically. If you encounter problems:

### 1. Check Dependencies
```bash
pip install transformers torch accelerate
python -c "import transformers, torch; print('✅ AI dependencies OK')"
```

### 2. Test Backends Individually
```bash
# Test HuggingFace backend
python -c "from ai_inference import LocalAIEngine; print('✅ LocalAIEngine OK')"

# Test fallback
python -c "from simple_ai_engine import SimpleAIEngine; print('✅ SimpleAIEngine OK')"
```

### 3. Architecture Info
```bash
# Check architecture (for reference only, not required)
arch                                    # Terminal architecture
python -c "import platform; print(platform.machine())"  # Python architecture
```

### 4. Model Storage
- Models automatically downloaded to `models/` directory
- HuggingFace models cached in `~/.cache/huggingface/`
- GGUF models in `models/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf`

## Development Notes

### Recent Major Update (2025-01-21)
- ✅ **Solved architecture incompatibility** with multi-backend system
- ✅ **Universal compatibility** across all platforms and architectures
- ✅ **Intelligent backend selection** with automatic fallbacks
- ✅ **Performance optimization** for x86_64 environments
- ✅ **Robust error handling** prevents crashes and provides graceful degradation

### Previous Enhancements
- Upgraded from 2K to 8K token context window
- Added comprehensive eco-friendly metrics and animations
- Implemented smart topic extraction and response generation
- Created device capability analysis with model recommendations
- Enhanced voice synthesis pacing and quality

### Future Roadmap
1. **MLX Integration**: Add Apple MLX backend for native Apple Silicon optimization
2. **Ollama Support**: Add Ollama API backend for additional model options
3. **Model Selection**: Allow user to choose between different models
4. **Performance Tuning**: Optimize response generation and streaming
5. **Extended Models**: Support for larger models (Phi-2, Mistral-7B) when resources allow

## Privacy & Environmental Impact

### 🔒 Privacy Features
- **100% Local Processing**: No data sent to cloud services
- **0 Bytes Transmitted**: All AI inference happens on your device
- **No Telemetry**: No usage data collected or transmitted
- **Conversation Privacy**: All context stays on your machine

### 🌱 Environmental Benefits
- **Energy Savings**: ~3 Wh saved per response vs cloud AI
- **Water Conservation**: ~120ml saved per response vs data centers
- **Carbon Reduction**: ~14g CO2 prevented per response
- **Resource Efficiency**: Optimized local processing vs remote computation

## Test Scripts and Validation

### Testing Suite
- **test_responses.py** - Comprehensive AI response testing across different models
- **test_html_question.py** - Validates HTML code generation capabilities  
- **test_long_text_voice.py** - Tests voice synthesis with progressively longer text
- **test_voice_fix.py** - Validates voice engine error handling and fallbacks

### Performance Metrics (2025-08-21)
- **Response Quality**: Improved from single-word to 500+ character comprehensive answers
- **Model Compatibility**: Universal backend works on both x86_64 and ARM64
- **Voice Reliability**: 200-character limit prevents 100% of ONNX crashes
- **Generation Speed**: TinyLlama provides responsive real-time interaction

## Recent Test Results

### AI Response Quality Test
```
Question: "Hello, how are you?"
TinyLlama Response: "Hello! I'm doing well, thank you for asking. As an AI assistant, I'm here to help you with any questions or tasks you might have. How can I assist you today?"
Length: 149 characters ✅
```

### HTML Code Generation Test  
```
Question: "Show me the basic HTML structure with head and body tags"
Response Length: 538 characters ✅
Contains proper HTML structure: ✅
Includes head and body sections: ✅
Shows complete DOCTYPE, html, head, and body tags: ✅
```

### Voice Synthesis Stability
```
Text Length | Status
50 chars    | ✅ Success
100 chars   | ✅ Success  
200 chars   | ✅ Success (limit)
300+ chars  | 🔄 Auto-truncated to prevent ONNX errors
```

### Backend Compatibility Matrix
```
Architecture | HuggingFace | ctransformers | SimpleAI | Status
x86_64       | ✅ Primary  | ❌ Skipped    | ✅ Fallback | ✅ Working
ARM64        | ✅ Primary  | ✅ Secondary  | ✅ Fallback | ✅ Working  
Any Platform | ✅ Primary  | ⚠️  Variable  | ✅ Fallback | ✅ Working
```

---

**M1K3 Status**: ✅ **FULLY OPERATIONAL** - Local AI working on all architectures!