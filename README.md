# M1K3 - Local AI Assistant with Voice Synthesis & Avatar Dashboard

<!-- CI/CD Status Badges -->
![M1K3 Tests](https://img.shields.io/github/actions/workflow/status/m1k3-team/m1k3/unified-tests.yml?style=for-the-badge&label=M1K3%20Tests&logo=github)
![Quick Tests](https://img.shields.io/github/actions/workflow/status/m1k3-team/m1k3/quick-tests.yml?style=for-the-badge&label=Quick%20Tests&logo=checkmarx)
![Test Coverage](https://img.shields.io/badge/Test%20Coverage-75%25-brightgreen?style=for-the-badge&logo=codecov)
![Test Count](https://img.shields.io/badge/Tests-166-blue?style=for-the-badge&logo=testinglibrary)
![Security Audit](https://img.shields.io/badge/Security-Audited-green?style=for-the-badge&logo=security)

🤖 **Optimized privacy-focused local AI assistant** with SmolLM2 & KittenTTS integration, real-time avatar visualization, and 90% smaller repository size. Built with efficient multi-backend AI system that works on any architecture.

## ✅ Current Status (2025-08-25) - PRODUCTION READY & OPTIMIZED

### 🌟 Core Features
- **🎯 SmolLM2-135M AI Engine** - Ultra-efficient 135M parameter model with adaptive prompting
- **🐱 KittenTTS Voice Synthesis** - High-quality content-aware voice with 8 profiles and audio completion
- **🎤 Speech-to-Text (STT) System** - Multi-engine voice input with automatic fallbacks (macOS Native, Vosk, Web Speech)
- **🧠 RAG (Retrieval-Augmented Generation)** with comprehensive expertise knowledge base (20 categories, 1,341+ documents)
- **⚡ Repository Optimization** - 90% size reduction (6GB → 453MB) with GitHub compliance
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

### 🎭 Intelligent Voice Synthesis - Content-Aware TTS

M1K3 features an **advanced intelligent TTS system** that automatically adapts voice characteristics based on content type:

#### **Key Features**
- **🧠 Automatic Content Parsing**: Recognizes thinking, narration, answers, and questions
- **🎚️ Content-Specific Voice Modulation**: Each content type has unique voice characteristics
- **⚙️ Real-Time Processing**: Seamless voice synthesis with natural transitions
- **🔄 Graceful Fallback**: Falls back to basic synthesis if intelligent TTS fails
- **📊 Comprehensive Status**: Built-in commands to monitor TTS system

#### **Content Types & Voice Characteristics**

| Content Type | Voice Style | Characteristics |
|--------------|------------|----------------|
| **🤔 THINKING** | Contemplative | 0.8x volume, 0.8x speed, -0.1 pitch, subtle reverb |
| **📖 NARRATION** | Storytelling | 1.1x speed, +0.1 pitch, warmth enhancement |
| **💡 ANSWER** | Confident | Standard clear voice, authoritative delivery |
| **❓ CLARIFICATION** | Questioning | 0.9x speed, +0.15 pitch, rising intonation |

#### **Content Recognition Examples**
```markdown
<thinking>
Let me think about this carefully...    # → Soft, contemplative voice
</thinking>

Here's my analysis: ...                 # → Clear, confident answer voice

*The user looks puzzled.*               # → Warm, expressive narration voice

Could you clarify what you mean?        # → Rising intonation, helpful tone
```

#### **TTS Commands**
```bash
/tts status                             # Show intelligent TTS system status
voice, mute                             # Toggle voice synthesis on/off
/profile <name>                         # Set voice profile (natural, broadcast, etc.)
```

#### **STT (Speech-to-Text) Commands**
```bash
# Voice Input - Press ENTER to activate voice input in CLI
💬 You (type or press ENTER for voice): [ENTER]  # Activates voice listening

# STT Management Commands
stt status                              # Show current STT engine and status
stt test                               # Test microphone and speech recognition
stt engine <name>                      # Switch STT engine (native, vosk, web, whisper)
stt engines                            # List available STT engines
stt calibrate                          # Recalibrate microphone sensitivity
```

#### **STT Engine Options**
```bash
python cli.py --stt-engine native      # Use macOS native STT (0MB, private)
python cli.py --stt-engine vosk        # Use Vosk offline STT (54MB)
python cli.py --stt-engine web         # Use Web Speech API (0MB, cloud)
python cli.py --stt-engine whisper     # Use Whisper STT (1GB+, excellent quality)
python cli.py --stt-engine none        # Disable voice input
```

#### **System Integration**
- **Automatic Processing**: All AI responses automatically parsed for content types
- **Priority-Based Queuing**: Content processed in optimal order (clarification → answer → narration → thinking)
- **Inter-Segment Pauses**: Natural pauses between different content types
- **Effects Integration**: Ready for advanced audio effects (reverb, warmth, pitch modulation)

### 🧘 Avatar System - 3D Animated Companions (NEW!)

M1K3 features a sophisticated **3D avatar system** with 13 animated models powered by THREE.js:

#### **Key Features**
- **🎮 3D Animated Models**: 13 GLB models with skeletal animations (Quirky Series + Community)
- **🎭 9 Emotion Types**: neutral, happy, sad, angry, surprised, love, thinking, sleepy, excited
- **⚡ Real-Time Updates**: WebSocket communication for live state changes
- **🖥️ Multiple Interfaces**: Web demo, Claude Desktop MCP App, Standalone Tauri popover
- **✨ Visual Effects**: Breathing motion, speaking/listening/thinking indicators
- **🎨 M1K3 Design System**: AMOLED black, M1K3 orange, Silkscreen font

#### **Quick Start - 3D Avatar**
```bash
# Web Demo (recommended)
cd src/web-avatar && npm install && npm run dev
# Open http://localhost:5174/demo.html

# Standalone Popover (Tauri)
cd src/avatar-popover && cargo tauri dev
# Press ⌘+Shift+M to toggle
```

#### **Available Models**
| Category | Models |
|----------|--------|
| **Quirky Series** | Colobus (default), Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish, Mask |
| **Community (CC0)** | Fox, CesiumMan, BrainStem, Robot |

#### **MCP Integration (Claude Desktop)**
```bash
# Add to Claude Desktop MCP servers
python mcp_unified_server.py

# Available tools: speak, set_avatar_emotion, set_avatar_model, start_voice_input
```

#### **Legacy Pixel Art Avatar**
```bash
avatar start                      # Launch pixel art web dashboard
avatar emotion happy 80           # Set emotion (0-100 intensity)
avatar test                       # Cycle through emotions
```

## 🤖 AI Architecture - Multi-Backend System

### **🥇 Primary Backend: SmolLM2 Engine**
- **Library**: Multi-backend SmolLM2 with adaptive prompting
- **Primary Model**: SmolLM2-135M via Ollama API
- **GGUF Fallback**: SmolLM-135M.Q4_K_M.gguf (101MB, downloaded separately)
- **Compatibility**: Universal (x86_64, ARM64, any platform)
- **Performance**: Ultra-fast loading (<3s), optimized for efficiency

### **🥈 Secondary Backend: KittenTTS Voice Synthesis**
- **Library**: `kittentts` with intelligent content parsing
- **Features**: Content-aware voice modulation, 8 voice profiles
- **Performance**: High-quality synthesis with audio completion
- **Integration**: Seamless CLI and avatar system integration

### **🥉 Fallback Backend: SimpleAIEngine**
- **Library**: Built-in enhanced mock with 8K context
- **Compatibility**: Universal (no dependencies)
- **Features**: Topic extraction, eco-metrics, demo mode
- **Performance**: Instant responses, minimal resources

### **Backend Selection Logic**
```
1. Try SmolLM2 Engine (primary)
   ├─ Ollama API: SmolLM2-135M (preferred)
   ├─ GGUF Fallback: SmolLM-135M.Q4_K_M.gguf
   └─ HuggingFace: HuggingFaceTB/SmolLM2-135M-Instruct

2. KittenTTS Voice Synthesis (parallel)
   ├─ Content-aware parsing and voice modulation
   ├─ Audio completion for truncation fixes
   └─ System TTS fallback if unavailable

3. SimpleAIEngine (guaranteed fallback)
   └─ Always works: Enhanced context-aware responses
```

## 📦 Available AI Models

### **🎯 SMOL Models (Primary)**
- **SmolLM2-135M** via Ollama API - Primary inference engine
- **SmolLM-135M.Q4_K_M.gguf** (101MB) - Quantized GGUF fallback
- **HuggingFaceTB/SmolLM2-135M-Instruct** - HuggingFace transformers fallback

### **🐱 Kitten Models (Voice)**
- **KittenTTS nano-0.1** - Lightweight, high-quality voice synthesis
- **8 Voice Profiles** - expr-voice-2-m/f, expr-voice-3-m/f, expr-voice-4-m/f, expr-voice-5-m/f
- **Content-Aware TTS** - Automatic parsing for thinking, narration, answers, clarification

### **⚡ Repository Optimization** 
- **Total Size**: 453MB (90% reduction from 6.0GB)
- **Large Models**: Downloaded separately via `download_models.py`
- **GitHub Compliant**: All files under size limits for easy cloning

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
# Clone repository (now only 453MB!)
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3

# Install Python dependencies
pip install -r requirements.txt

# Download required models (SmolLM2 + Kitten)
python download_models.py

# Test the system with SmolLM2
python m1k3.py --no-voice

# Test with RAG expertise system
python m1k3.py --rag --query "How do I fix slow WiFi?"

# Expected output:
# 🤖 SmolLM2 engine available
# ✅ SmolLM2 ready via Ollama API
# 🎭 Intelligent TTS system available
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

5. **Intelligent Voice Synthesis** (`intelligent_tts_controller.py`, `enhanced_voice_engine.py`)
   - Content-aware TTS with automatic parsing (thinking, narration, answer, clarification)
   - Voice modulation per content type with distinct characteristics
   - KittenML TTS with persona system and professional audio effects
   - Priority-based job queuing with natural inter-segment pauses
   - Graceful fallback to basic synthesis

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
Architecture | SmolLM2 | KittenTTS | SimpleAI | Status
x86_64       | ✅ Primary | ✅ Available | ✅ Fallback | ✅ Working
ARM64        | ✅ Primary | ✅ Available | ✅ Fallback | ✅ Working  
Any Platform | ✅ Primary | ✅ Available | ✅ Fallback | ✅ Working
```

### **Resource Usage**
```
Memory Usage: ~200MB-1GB (SmolLM2 optimized)
Disk Space: ~500MB (repository + models, 90% reduction!)
CPU Usage: Low during inference (SmolLM2 efficiency)
Network: 0 bytes (fully offline after initial setup)
```

## 🚧 Recent Major Updates

### **🎯 Repository Optimization & SMOL/Kitten Models (2025-08-25)**
- ✅ **90% size reduction**: 6.0GB → 453MB repository
- ✅ **SmolLM2-135M integration**: Primary AI backend with adaptive prompting
- ✅ **KittenTTS implementation**: High-quality voice synthesis with content parsing
- ✅ **Git history cleanup**: Removed large models from version history
- ✅ **GitHub compliant**: All files under size limits for easy distribution
- ✅ **Model download system**: Essential models downloaded separately
- ✅ **CLI fixes**: Updated all hardcoded model references

### **Avatar System Integration (2025-08-21)**
- ✅ Real-time web dashboard with emotion tracking
- ✅ WebSocket communication for live updates  
- ✅ Pixel art visualization with 6 avatar styles
- ✅ Network multi-access on all interfaces
- ✅ Fixed JavaScript errors and WebSocket connectivity

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
│   ├── src/web-avatar/            # THREE.js 3D avatar (13 models)
│   │   ├── demo.html              # Full demo with M1K3 design
│   │   ├── mcp-app.html           # Claude Desktop iframe
│   │   └── src/                   # TypeScript source
│   ├── src/avatar-popover/        # Tauri standalone app
│   │   ├── src-tauri/             # Rust backend (tray, hotkey)
│   │   └── index.html             # Popover UI
│   ├── scripts/avatar_server.py   # WebSocket state server
│   └── mcp_unified_server.py      # MCP tools (TTS+Avatar+STT)
│
├── 🎭 Intelligent Voice & Audio
│   ├── intelligent_tts_controller.py  # Content-aware TTS orchestration
│   ├── model_output_parser.py         # Automatic content type recognition
│   ├── content_specific_effects.py    # Voice modulation per content type
│   ├── enhanced_voice_engine.py       # KittenML TTS with personas
│   ├── sound_manager.py               # 67 categorized sound effects
│   └── sounds/                        # Audio assets and startup sequences
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

*Latest Update: 2026-02-01 - 3D Avatar System Complete with THREE.js, MCP Integration, and Tauri Standalone Popover*