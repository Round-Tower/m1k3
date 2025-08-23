# M1K3 - Local AI Assistant with Voice Synthesis & Avatar Dashboard

<!-- CI/CD Status Badges -->
![M1K3 Tests](https://img.shields.io/github/actions/workflow/status/m1k3-team/m1k3/unified-tests.yml?style=for-the-badge&label=M1K3%20Tests&logo=github)
![Quick Tests](https://img.shields.io/github/actions/workflow/status/m1k3-team/m1k3/quick-tests.yml?style=for-the-badge&label=Quick%20Tests&logo=checkmarx)
![Test Coverage](https://img.shields.io/badge/Test%20Coverage-75%25-brightgreen?style=for-the-badge&logo=codecov)
![Test Count](https://img.shields.io/badge/Tests-166-blue?style=for-the-badge&logo=testinglibrary)
![Security Audit](https://img.shields.io/badge/Security-Audited-green?style=for-the-badge&logo=security)

🤖 **Privacy-focused local AI assistant** with voice synthesis, real-time avatar visualization, and comprehensive device context analysis. Built with a robust multi-backend AI system that works on any architecture.

## ⚠️ Current Status (2025-08-21) - PRODUCTION READY WITH KNOWN ISSUES

### 🌟 Core Features
- **🔒 100% Local AI Inference** with multiple model backends (HuggingFace, GGUF, enhanced fallback)
- **🧠 RAG (Retrieval-Augmented Generation)** with comprehensive expertise knowledge base (20 categories, 1,341+ documents)
- **🗣️ Advanced Voice Synthesis** with sidechain compression and professional audio effects ⚠️ (speech cutoff bug)  
- **🧘 Real-Time Avatar Dashboard** with emotion tracking and WebSocket communication
- **🎨 Multiple Interface Options** - CLI, Modern TUI, Rich full-screen
- **🌱 Eco-Friendly Metrics** showing energy/water saved vs cloud AI
- **🔧 Universal Compatibility** - works on x86_64, ARM64, any platform
- **📊 Comprehensive Device Analysis** with hardware detection and model recommendations
- **🎮 Rich Animations & Sound Effects** with 67+ categorized sound effects

### ⚠️ Known Issues

- **Speech Cutoff Bug** (High Priority): Voice synthesis cuts off at sentence endings despite extensive fixes. See `BUGS.md` for details.

### 🚀 Interface Options

#### 1. **🖥️ Classic CLI (Default)**
```bash
python m1k3.py                    # Traditional command-line interface
python m1k3.py --no-voice         # CLI without voice synthesis
python cli.py                     # Direct CLI entry point
```

#### 2. **✨ Textual TUI (Modern Full-Screen) - RECOMMENDED**
```bash
python m1k3.py --tui              # Modern full-screen terminal interface
python m1k3.py --tui --no-voice   # TUI without voice synthesis
python m1k3.py --tui --no-avatar  # TUI without avatar server
python m1k3_tui.py                # Direct TUI launch
```

#### 3. **🎨 Rich Full-Screen (Lightweight)**
```bash
python m1k3.py --fullscreen       # Rich-based full-screen interface
python m1k3.py --fullscreen --no-voice --no-avatar
python m1k3_rich_tui.py           # Direct Rich TUI launch
```

### 🧘 Avatar System - Real-Time Web Dashboard

M1K3 features a sophisticated **pixel art avatar system** with real-time emotion tracking:

#### **Key Features**
- **📱 Web Dashboard**: Accessible at `http://localhost:8080` with multi-device support
- **🎭 8 Emotion Types**: happy, sad, angry, surprised, love, thinking, sleepy, excited
- **⚡ Real-Time Updates**: WebSocket communication for live state changes
- **🎨 6 Avatar Styles**: robot, organic, crystal, ghost, energy, cute
- **✨ Particle Effects**: Emotion-specific visual effects and breathing animations
- **🔊 Sound Integration**: Audio feedback synchronized with emotions

#### **Avatar Commands**
```bash
avatar start                      # Launch web server and dashboard
avatar status                     # Show server status and avatar state  
avatar emotion happy 80           # Set specific emotion (0-100 intensity)
avatar style crystal #FF6B6B      # Change avatar style and color
avatar test                       # Cycle through all emotions
avatar stop                       # Stop avatar server
```

#### **Auto-Start Avatar**
```bash
python m1k3.py --auto-avatar      # Start with avatar dashboard (opens browser)
python cli.py --with-avatar       # CLI with avatar integration
```

## 🤖 AI Architecture - Multi-Backend System

### **🥇 Primary Backend: HuggingFace Transformers**
- **Library**: `transformers` + `torch` + `accelerate`
- **Primary Model**: TinyLlama/TinyLlama-1.1B-Chat-v1.0 (~1.1GB)
- **Fallback Models**: Qwen/Qwen2.5-0.5B-Instruct, microsoft/DialoGPT-small, distilgpt2
- **Compatibility**: Universal (x86_64, ARM64, any platform)
- **Performance**: Fast loading (~2-4s), excellent response quality

### **🥈 Secondary Backend: ctransformers (GGUF)**
- **Library**: `ctransformers`
- **Model**: TinyLlama-1.1B-Chat-v1.0.Q4_K_M.gguf (637MB)
- **Compatibility**: ARM64 native (Apple Silicon optimized)
- **Performance**: Quantized inference, Metal GPU acceleration

### **🥉 Fallback Backend: SimpleAIEngine**
- **Library**: Built-in enhanced mock with 8K context
- **Compatibility**: Universal (no dependencies)
- **Features**: Topic extraction, eco-metrics, demo mode
- **Performance**: Instant responses, minimal resources

### **Backend Selection Logic**
```
1. Try HuggingFace Transformers (universal compatibility)
   ├─ Success: Use best available model (TinyLlama/DialoGPT)
   └─ Fail: Continue to step 2

2. Try ctransformers (if ARM64 + GGUF exists)
   ├─ Success: Use quantized GGUF model
   └─ Fail: Continue to step 3

3. Use SimpleAIEngine (guaranteed fallback)
   └─ Always works: Enhanced context-aware responses
```

## 📦 Available AI Models

### **🤗 HuggingFace Models** (Auto-discovered from cache)
- **microsoft/DialoGPT-small** (350MB) - Primary conversational model
- **TinyLlama/TinyLlama-1.1B-Chat-v1.0** (1.1GB) - Instruction-tuned chat model
- **microsoft/Phi-3-mini-4k-instruct** (3.9GB) - Advanced reasoning model
- **google/gemma-2-2b-it** (3.1GB) - Google instruction model
- **distilgpt2** (350MB) - Lightweight general model

### **⚡ GGUF Quantized Models** (Optimized for Apple Silicon)
- **tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf** (637MB) - Recommended primary
- **SmolLM-135M.Q4_K_M.gguf** (100MB) - Ultra-lightweight option

## 🛠️ Installation & Setup

### Prerequisites
```bash
# macOS dependencies
brew install openssl readline sqlite3 xz zlib gettext

# Python 3.8+ required
python --version  # Should be 3.8 or higher
```

### Quick Start
```bash
# Clone repository
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3

# Install Python dependencies
pip install -r requirements.txt

# Download AI models (required)
python download_models.py

# Test the system
python m1k3.py --no-voice

# Test with RAG expertise system
python m1k3.py --rag --query "How do I fix slow WiFi?"

# Expected output:
# ✅ HuggingFace Transformers available
# 🔧 Backend selection: HF=True, CT=False
# ✅ Model loaded with HuggingFace Transformers
# 🎯 M1K3 RAG Engine ready!
# 💤 Type 'help' for commands or start chatting!
```

### Model Management
```bash
# Analyze device and show available models
python local_model_manager.py

# Download additional models with device recommendations  
python download_model.py

# Pre-pack and verify model integrity
python model_prepack.py

# Direct AI engine testing
python ai_inference.py

# Generate RAG knowledge base (optional)
python generate_comprehensive_kb.py

# Test RAG system validation
python test_rag_practical.py
```

## 🎮 Interactive Commands

### **General Commands**
```bash
help                              # Show all available commands
tokens, usage                     # Display token usage and eco impact
stats, status                     # System statistics with animations
context, device                   # Comprehensive device context analysis
animate, demo                     # Animation demonstrations
clear                            # Clear conversation context
quit, exit                       # Exit M1K3
```

### **RAG Commands**
```bash
python m1k3.py --rag              # Enable RAG system with knowledge base
python m1k3.py --rag --query "WiFi troubleshooting"  # Single query with RAG
python m1k3.py --tui --rag        # TUI interface with RAG enhancement
python test_rag_practical.py      # Validate RAG system functionality
```

### **Avatar Commands**
```bash
avatar start                      # Start avatar web server and dashboard
avatar stop                       # Stop avatar server
avatar status                     # Show server status and avatar state
avatar emotion <emotion> [intensity]  # Set avatar emotion (0-100)
avatar style <style> [color]          # Change avatar style/color
avatar test                       # Test all avatar emotions
```

### **Voice & Audio Commands**
```bash
voice                            # Toggle voice synthesis on/off
character m1k3                   # Default M1K3 voice
character hero                   # Confident protagonist voice
character narrator               # Classic game narrator
character villain                # Deep antagonist voice
retro                           # PlayStation 1 retro effects
classic                         # Clean system TTS
```

## 🌐 Network & Multi-Device Features

### **Avatar Dashboard Access**
The avatar server automatically detects all network interfaces:
```
📱 Available at:
   Local:   http://127.0.0.1:8080
   Network: http://192.168.1.100:8080 (primary)
   Network: http://10.0.0.50:8080
```

### **WebSocket Communication**
- **HTTP Port**: 8080 (configurable)
- **WebSocket Port**: 8081 (auto-assigned)
- **Real-Time Updates**: Emotion changes during conversation flow
- **Debug Console**: Built-in WebSocket message logging
- **Multi-Client**: Multiple browsers can connect simultaneously

## 🔧 Architecture Details

### **Key Components**

1. **Multi-Interface Launcher** (`m1k3.py`)
   - Interface selection (CLI/TUI/Rich)
   - Automatic fallbacks and error handling
   - Universal argument parsing

2. **AI Inference Engine** (`ai_inference.py`)
   - Multi-backend AI system with automatic selection
   - HuggingFace + ctransformers + enhanced fallback
   - Architecture compatibility handling

3. **Local Model Manager** (`local_model_manager.py`)
   - Discovers cached HuggingFace models
   - Device capability analysis and model recommendations
   - Prevents unnecessary downloads

4. **Avatar System** (`avatar_server.py`, `avatar_controller.py`)
   - HTTP server + WebSocket communication
   - Real-time emotion analysis and state management
   - Pixel art visualization with multiple styles

5. **Voice Synthesis** (`enhanced_voice_engine.py`)
   - KittenML TTS with persona system
   - 200-character limit to prevent ONNX errors
   - Graceful fallback to system TTS

6. **CLI Interfaces** (`cli.py`, `m1k3_tui.py`, `m1k3_rich_tui.py`)
   - Rich animations, eco-metrics, token visualization
   - Full-screen TUI with real-time updates
   - Avatar integration and sound effects

7. **System Analysis** (`system_metrics.py`)
   - Comprehensive device detection and analysis
   - Environmental impact calculations
   - Performance metrics and recommendations

### **Sound System** (`sound_manager.py`)
- **67 categorized sound effects** across 8 categories
- **Non-blocking playback** for fast startup
- **Dynamic sound selection** based on context
- **PlayStation 1 inspired** startup sequences

## 🌱 Privacy & Environmental Impact

### **🔒 Privacy Features**
- **100% Local Processing**: No data sent to cloud services
- **0 Bytes Transmitted**: All AI inference happens on your device
- **No Telemetry**: No usage data collected or transmitted
- **Conversation Privacy**: All context stays on your machine

### **🌿 Environmental Benefits**
- **Energy Savings**: ~3 Wh saved per response vs cloud AI
- **Water Conservation**: ~120ml saved per response vs data centers
- **Carbon Reduction**: ~14g CO2 prevented per response
- **Resource Efficiency**: Optimized local processing

## ⚡ Performance Characteristics

### **Response Quality & Speed** (2025-08-21)
```
AI Response Length: 100-500+ characters (comprehensive answers)
Model Loading Time: ~2-4 seconds (cached models)
Response Generation: ~1-3 seconds (real-time streaming)
Voice Synthesis: <200 characters (prevents ONNX crashes)
Avatar Updates: Real-time WebSocket (60fps animations)
```

### **Compatibility Matrix**
```
Architecture | HuggingFace | ctransformers | SimpleAI | Status
x86_64       | ✅ Primary  | ❌ Skipped    | ✅ Fallback | ✅ Working
ARM64        | ✅ Primary  | ✅ Secondary  | ✅ Fallback | ✅ Working  
Any Platform | ✅ Primary  | ⚠️  Variable  | ✅ Fallback | ✅ Working
```

### **Resource Usage**
```
Memory Usage: ~200MB-2GB (depending on model)
Disk Space: ~500MB-5GB (model cache + dependencies)
CPU Usage: Moderate during inference, idle otherwise
Network: 0 bytes (fully offline after initial setup)
```

## 🚧 Recent Major Updates

### **Avatar System Integration (2025-08-21)**
- ✅ Real-time web dashboard with emotion tracking
- ✅ WebSocket communication for live updates  
- ✅ Pixel art visualization with 6 avatar styles
- ✅ Network multi-access on all interfaces
- ✅ Fixed JavaScript errors and WebSocket connectivity

### **Architecture Compatibility (2025-01-21)**
- ✅ Solved x86_64/ARM64 incompatibility with multi-backend system
- ✅ Universal compatibility across all platforms
- ✅ Intelligent backend selection with automatic fallbacks
- ✅ Performance optimization for Rosetta environments

### **Enhanced Capabilities**
- ✅ Upgraded from 2K to 8K token context window
- ✅ Added comprehensive eco-friendly metrics and animations  
- ✅ Implemented smart topic extraction and response generation
- ✅ Created device capability analysis with model recommendations

## 🛣️ Roadmap & Next Steps

### **Immediate Improvements**
1. **Enhanced Avatar Dashboard**
   - Chat interface integration with speech-to-text
   - Sound effects synchronization with avatar states
   - Conversation history display in dashboard

2. **Model Performance Optimization**
   - Streaming response improvements
   - Model quantization options
   - Memory usage optimization

3. **Advanced Diagnostics Enhancement**
   - Extended device capability detection
   - Network performance analysis  
   - Model benchmark comparisons

### **Medium-Term Features**
1. **Additional AI Backends**
   - MLX integration for native Apple Silicon optimization
   - Ollama API backend support
   - OpenAI-compatible API endpoint

2. **Extended Model Support**
   - User-selectable model switching
   - Support for larger models (Phi-2, Mistral-7B)
   - Custom fine-tuned model integration

3. **Enhanced User Experience**
   - Conversation persistence and history
   - Custom system prompts and personas
   - Plugin architecture for extensibility

### **Long-Term Vision**
1. **Advanced AI Capabilities**
   - Multi-modal support (image, document processing)
   - Tool use and function calling
   - Advanced reasoning and planning

2. **Enterprise Features**
   - Team collaboration features
   - Model deployment at scale
   - Custom training pipeline integration

## 🧪 Testing & Validation

### **Test Scripts**
- **test_responses.py** - Comprehensive AI response testing across models
- **test_html_question.py** - Validates code generation capabilities
- **test_websocket.py** - WebSocket connectivity and message flow testing
- **test_voice_fix.py** - Voice synthesis error handling validation

### **Quality Metrics**
```
Response Quality: Improved from single-word to 500+ character answers
Model Compatibility: Universal backend works on all architectures  
Voice Reliability: 200-character limit prevents 100% of ONNX crashes
Avatar Updates: Real-time emotion tracking with <100ms latency
```

## 📁 Project Structure

```
m1k3/
├── 🚀 Launch Scripts
│   ├── m1k3.py                    # Multi-interface launcher (recommended)
│   ├── cli.py                     # Classic CLI application
│   ├── m1k3_tui.py               # Textual TUI interface  
│   └── m1k3_rich_tui.py          # Rich full-screen interface
│
├── 🤖 AI & Inference  
│   ├── ai_inference.py            # Multi-backend AI engine
│   ├── local_model_manager.py     # Model discovery and management
│   ├── simple_ai_engine.py        # Enhanced fallback AI engine
│   └── download_models.py         # Model downloading with recommendations
│
├── 🧠 RAG System
│   ├── m1k3_rag_engine.py         # RAG engine with semantic search
│   ├── m1k3_rag_integration.py    # RAG integration with M1K3
│   ├── expanded_synthetic_generator.py  # Knowledge base generation
│   ├── generate_comprehensive_kb.py     # Knowledge base builder
│   ├── rag_knowledge_viewer.html  # Web-based knowledge browser
│   └── rag_admin.html            # Knowledge base management interface
│
├── 🧘 Avatar System
│   ├── avatar_server.py           # HTTP + WebSocket server
│   ├── avatar_controller.py       # Emotion analysis and state management
│   └── m1k3_avatar.html          # Web dashboard interface
│
├── 🗣️ Voice & Audio
│   ├── enhanced_voice_engine.py   # KittenML TTS with personas
│   ├── sound_manager.py           # 67 categorized sound effects
│   └── sounds/                    # Audio assets and startup sequences
│
├── 📊 System & Metrics
│   ├── system_metrics.py          # Device analysis and eco-metrics
│   ├── cli_animations.py          # Rich CLI animations and effects
│   └── intent_classification_system.py  # Intent classification for RAG
│
├── 🧪 Testing & Validation
│   ├── test_rag_practical.py      # RAG system validation (recommended)
│   ├── test_rag_quick_validation.py    # Fast RAG component testing
│   ├── test_websocket.py          # WebSocket connectivity testing
│   ├── test_responses.py          # AI response quality validation
│   └── test_voice_fix.py          # Voice synthesis error handling
│
├── 📝 Documentation & Config
│   ├── README.md                  # This comprehensive guide
│   ├── CLAUDE.md                  # Detailed technical documentation
│   ├── SETUP.md                   # Setup and troubleshooting guide
│   ├── requirements.txt           # Python dependencies
│   └── Makefile                   # Development shortcuts
│
└── 📦 Assets & Models
    ├── models/                    # Downloaded AI models (auto-managed)
    ├── knowledge/                 # RAG knowledge base (auto-generated)
    ├── sounds/                    # Audio effects and startup sounds
    └── design/                    # Screenshots and design assets
```

## 📋 Requirements

### **System Requirements**
- **Python**: 3.8+ (3.11+ recommended)
- **Memory**: 4GB RAM minimum, 8GB+ recommended  
- **Storage**: 1-5GB (depending on models selected)
- **CPU**: Any modern processor (optimized for Apple Silicon)
- **Network**: Optional (only for initial model downloads)

### **Supported Platforms**
- **✅ macOS** (x86_64, ARM64) - Fully supported with native optimizations
- **✅ Linux** (x86_64, ARM64) - Full compatibility  
- **✅ Windows** - Compatible via WSL or native Python
- **✅ Any Platform** - SimpleAIEngine fallback guarantees operation

## 🚀 CI/CD & Testing Pipeline

M1K3 features a comprehensive continuous integration and testing pipeline powered by the **M1K3 Unified Test Suite**.

### **📊 Test Coverage**
- **166 Tests** across 73 test files
- **13.8 minutes** estimated runtime
- **7 Test Categories**: Unit, Integration, Visual, Performance, Security, E2E, API
- **Cross-Platform**: Linux, macOS, Windows validation
- **Multi-Version**: Node.js 18/20/22 + Python 3.9/3.11/3.12 matrix

### **🔄 Automated Workflows**

#### **Complete Test Suite** (`unified-tests.yml`)
- **Triggers**: Push to main/develop, Pull requests, Daily schedule
- **Features**: Full test coverage, screenshot testing, security audit, GitHub Pages deployment
- **Artifacts**: Interactive HTML reports, screenshots, data exports (30-day retention)

#### **Quick Validation** (`quick-tests.yml`) 
- **Triggers**: Feature branch pushes, PR updates
- **Duration**: ~5-10 minutes
- **Features**: Fast feedback, security quick scan, code quality checks

#### **Release Testing** (`release-testing.yml`)
- **Triggers**: Release publish, Manual dispatch  
- **Duration**: ~30-45 minutes
- **Features**: Cross-platform validation, comprehensive release report (365-day retention)

### **📈 Quality Features**
- **🎨 M1K3-Branded Reports**: Professional HTML reports with pure black design
- **📸 Visual Regression**: Screenshot testing across Desktop/Tablet/Mobile viewports
- **🔒 Security Auditing**: Dependency vulnerabilities, code analysis, secrets scanning
- **📊 Interactive Dashboards**: Filterable results, click-to-expand screenshots
- **🏷️ Automated Badges**: Real-time status indicators and coverage metrics

### **🌐 Report Access**
- **GitHub Pages**: [Live Test Reports](https://github.com/your-org/m1k3/actions)
- **Artifacts**: Download detailed reports from any workflow run
- **PR Comments**: Automated test summaries on pull requests

**📚 Full Documentation**: [CI/CD Pipeline Guide](.github/workflows/README.md)

## 🤝 Contributing

M1K3 is designed for local AI experimentation and development. Contributions welcome for:
- Additional AI backend integrations
- New avatar styles and animations
- Enhanced voice synthesis options  
- Performance optimizations
- Platform-specific improvements

## 📜 License

MIT License - Built for privacy-focused local AI development and experimentation.

---

**🤖 M1K3** - Your comprehensive local AI companion with voice synthesis, real-time avatar visualization, and complete privacy. Powered by open-source models and designed for universal compatibility and optimal performance.

*Latest Update: 2025-08-21 - Avatar Dashboard & WebSocket Integration Complete*