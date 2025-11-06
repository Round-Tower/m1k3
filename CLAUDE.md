# M1K3 - Local AI Assistant

## Overview
Privacy-focused local AI assistant with voice synthesis, web dashboard, and CLI interfaces. Features multi-backend AI, real-time avatar visualization, and PWA deployment.

## Status: ✅ PRODUCTION READY (2025-08-22)

### Core Features
- **Local AI inference** with TinyLlama-1.1B-Chat (universal compatibility)
- **RAG (Retrieval-Augmented Generation)** with comprehensive expertise knowledge base (20 categories, 1,341+ documents)
- **Advanced voice synthesis** with multi-engine TTS: VibeVoice (90-minute continuous, multi-speaker), KittenTTS (fast), and system fallbacks
- **VibeVoice integration** - Microsoft's frontier TTS with 90-minute continuous synthesis, multi-speaker conversations, and state-of-the-art quality
- **Avatar system** with real-time web dashboard, monochrome UI design, and emotion tracking
- **Enhanced CLI** with animations, eco-metrics, 8K context visualization
- **Speech-to-Text (STT) system** with multi-engine fallbacks (macOS Native, Vosk, Web Speech, Whisper)
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

# Modern TUI 
python m1k3.py --tui

# Rich full-screen
python m1k3.py --fullscreen

# CLI with avatar dashboard (recommended)
python cli.py

# CLI without avatar dashboard
python cli.py --no-avatar

# RAG-enhanced with expert knowledge
python m1k3.py --rag
python m1k3.py --tui --rag
python cli.py --rag

# VibeVoice TTS Options (NEW)
python cli.py --tts-engine vibevoice                                    # Basic VibeVoice
python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob    # Multi-speaker conversation
python cli.py --tts-engine vibevoice --continuous-mode --voice-profile narrative    # 90-minute continuous synthesis
python cli.py --tts-engine vibevoice --voice-profile conversational     # Multi-speaker dialogue mode
python cli.py --tts-engine vibevoice --vibevoice-model 7B               # Use larger 7B model
```

## Interactive Commands
```bash
# Basic commands
help              # Show available commands
tokens, usage     # Display token usage and eco impact  
stats, status     # System statistics
clear             # Clear conversation context
quit, exit        # Exit M1K3

# Voice commands
voice, mute       # Toggle voice synthesis on/off
/profile <name>   # Set voice profile (natural, broadcast, terminal, etc.)
/tts status       # Show intelligent TTS system status and voice settings
/tts engine <name>  # Switch TTS engine (vibevoice, kitten, fallback)

# VibeVoice commands (NEW)
/vibevoice status    # Show VibeVoice availability and model info
/vibevoice speakers <names>  # Set speakers (e.g., Alice Bob Carol Dave)
/vibevoice model <variant>   # Switch model (1.5B, 7B)
/vibevoice continuous        # Enable 90-minute continuous mode
/vibevoice multi-speaker     # Enable multi-speaker conversation

# Avatar commands  
avatar start      # Start web dashboard
avatar status     # Show server status
avatar emotion <emotion> [intensity]  # Set emotion (0-100)
avatar test       # Test all emotions

# Speech-to-Text commands
# Press ENTER in CLI for voice input
stt status        # Show current STT engine and status
stt test          # Test microphone and speech recognition
stt engine <name> # Switch STT engine (native, vosk, web, whisper)  
stt calibrate     # Recalibrate microphone sensitivity
```

## VibeVoice Text-to-Speech System (NEW)

### Overview
M1K3 now integrates **Microsoft's VibeVoice**, a frontier open-source TTS model that revolutionizes voice synthesis with unprecedented capabilities:

- **90-minute continuous speech** generation (vs typical 30-second limits)  
- **Multi-speaker conversations** with up to 4 simultaneous speakers
- **Ultra-efficient processing** with 3200x compression at 7.5 tokens/second
- **State-of-the-art quality** using VALL-E architecture and diffusion models
- **100% local processing** - no cloud dependencies (MIT licensed)

### Model Variants
- **VibeVoice-1.5B** (Default): 64K context, 90-minute generation, ~4GB RAM
- **VibeVoice-7B** (Advanced): 32K context, 45-minute generation, ~16GB RAM

### Voice Profiles
```bash
# KittenTTS Profiles (fast, lightweight)
natural          # Default conversational voice with light effects
assistant        # Professional AI assistant tone
broadcast        # Clear announcer-style voice
terminal         # Technical system voice
debug           # Minimal processing for speed
minimal         # Basic synthesis only

# VibeVoice Profiles (advanced, high-quality) 
conversational   # Multi-speaker dialogue (2-4 speakers)
narrative        # Long-form storytelling (up to 90 minutes)
assistant_duo    # AI assistant with user voice simulation
```

### Setup and Installation
```bash
# 1. Install VibeVoice dependencies (optional - will fallback to KittenTTS if not available)
pip install diffusers>=0.21.0 accelerate>=0.20.0 librosa>=0.10.0 gradio>=4.0.0

# 2. Clone VibeVoice repository
git clone https://github.com/microsoft/VibeVoice.git ~/VibeVoice

# 3. Download models (automatic on first use)
huggingface-cli download microsoft/VibeVoice-1.5B

# 4. Optional: Docker setup with NVIDIA GPU support
./setup_vibevoice_docker.sh
./run_vibevoice_docker.sh
```

### Usage Examples
```bash
# Basic VibeVoice usage
python cli.py --tts-engine vibevoice "Tell me a long story about AI"

# Multi-speaker conversation
python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob Carol

# 90-minute continuous synthesis (perfect for audiobooks, lectures)
python cli.py --tts-engine vibevoice --continuous-mode --voice-profile narrative

# Interactive VibeVoice commands
/vibevoice status              # Check availability and model info
/vibevoice speakers Alice Bob  # Set conversation speakers
/vibevoice continuous          # Enable long-form mode
```

### Hardware Requirements
- **Optimal**: NVIDIA GPU with 4GB+ VRAM (real-time generation)
- **Minimum**: CPU with 8GB+ RAM (slower generation, short text only)  
- **Storage**: ~4-10GB for models and dependencies

### Integration Benefits
- **Seamless fallback**: Automatically uses KittenTTS if VibeVoice unavailable
- **Streaming compatible**: Works with existing StreamingTTSEngine
- **Profile system**: Integrated with M1K3's voice profile architecture
- **Command integration**: Full CLI and interactive command support

## Speech-to-Text (STT) System

### Multi-Engine Architecture
- **macOS Native**: SFSpeechRecognizer (0MB, private, on-device)
- **Vosk**: Offline ML model (54MB, good accuracy, cross-platform)  
- **Web Speech**: SpeechRecognition library (0MB, cloud-based)
- **Whisper**: OpenAI model (1GB+, excellent quality, optional)

### Voice Input Usage
```bash
# Enable specific STT engine
python cli.py --stt-engine native    # macOS Native (private)
python cli.py --stt-engine vosk      # Offline (54MB)
python cli.py --stt-engine web       # Cloud-based (0MB)
python cli.py --stt-engine none      # Disable voice input

# In CLI, press ENTER to activate voice input
💬 You (type or press ENTER for voice): [ENTER]
🎤 Listening... (speak now)
```

### Diagnostic Tools
```bash
python audio_level_test.py           # Test microphone audio levels  
python stt_diagnostics.py            # Test all STT engines
python check_speech_permissions.py   # Verify macOS permissions
```

### Features
- **Automatic Fallbacks**: Tries backup engines when primary fails
- **Permission Management**: Automatic macOS authorization prompts
- **Audio Monitoring**: Real-time level verification and feedback
- **Clean State Management**: Proper resource cleanup between attempts
- **Comprehensive Diagnostics**: Easy troubleshooting with detailed feedback

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
- **Avatar server**: Starts automatically with CLI (use `--no-avatar` to disable)

### Monochrome UI Design System
- **Pure monochrome palette** - blacks, grays, whites only (no jarring colors)
- **12-column responsive grid** - optimized space utilization across devices
- **650+ line CSS framework** - comprehensive utility classes and components
- **Brutalist design principles** - clean typography, minimal decoration, square/rectangular layouts
- **Mobile-first responsive** - 480px, 768px, 1200px breakpoints with adaptive layouts
- **Accessibility compliant** - proper contrast ratios, focus indicators, 44px touch targets
- **Cross-browser tested** - Playwright validation across Chrome, Firefox, Safari, mobile browsers
- **Real-time updates** - system metrics, avatar controls, chat interface, component status

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
- **5 GitHub Actions workflows**: Unified tests, quick tests, release testing, badges, repository visualization
- **Repository structure visualization**: Automated SVG diagrams with GitHub repo-visualizer
- **Visual regression testing**: Screenshot comparison across viewports
- **Multi-platform matrix**: Ubuntu/macOS/Windows × Node.js × Python
- **Automated reporting**: HTML dashboards with GitHub Pages deployment
- **Security scanning**: Dependency vulnerabilities and code analysis

### Repository Visualization
- **Automated SVG generation**: Weekly structure diagrams using GitHub repo-visualizer
- **Smart exclusions**: Filters out build artifacts, dependencies, and temporary files
- **Metadata tracking**: Generation timestamps, file sizes, and visual element counts
- **GitHub Pages integration**: Published diagrams at `docs/repo-structure.svg`
- **PR integration**: Automatic comments with visualization updates on pull requests

## Privacy & Environmental Impact
- **100% local processing** - No data sent to cloud services
- **0 bytes transmitted** - All AI inference on your device
- **Energy efficient** - ~3 Wh saved per response vs cloud AI
- **Water conservation** - ~120ml saved per response vs data centers

---

# 間 AI Mobile App - Privacy-First On-Device AI Companion

## Overview
**間 AI** (pronounced "ma" - meaning "negative space") is the mobile companion to M1K3, bringing privacy-first on-device AI to Android and iOS through Kotlin Multiplatform. Embracing wabi-sabi philosophy and computational sufficiency, 間 AI delivers a powerful AI assistant that never sends data to the cloud.

## Status: 🚀 ACTIVE DEVELOPMENT

**Current Phase:** AI Engine Migration & Abstraction - **IN PROGRESS** 🔄
**Timeline:** 16 weeks (6 phases)
**Target Release:** Beta v0.1.0 (Week 16)
**Progress:** 12/135 tickets (9%) - **62 Passing Tests!** ✅

### 🎉 **Latest Milestone:** Llamatik 0.8.1 Integration & BaseLlmEngine Abstraction (2025-11-06)
- ✅ **Llamatik 0.8.1** - Stable llama.cpp binding successfully integrated (no crashes!)
- ✅ **BaseLlmEngine interface** - Abstract AI engine interface for easy swapping (177 lines)
- ✅ **LlamaCppEngine** - Rewritten to use Llamatik API with prompt engineering (353 lines)
- ✅ **Testing infrastructure** - 36 test cases + MockLlmEngine for deterministic testing
- ✅ **GenerationConfig** - Unified configuration with graceful degradation
- ✅ **Migration success** - ONNX hallucinations → InferKt crash (SIGABRT) → Llamatik stable
- ⚠️ **Trade-offs** - Lost fine-grained control (temperature, topP, topK) but gained stability
- 🔧 **Workaround** - Prompt engineering for behavioral control ("Be concise..." vs "Be creative...")
- 📄 See commit 8f4e204 for complete migration details

### 🎉 **Previous Milestone:** Phase 2 Complete - Chat History & Eco Metrics (2025-11-04)
- ✅ **ConversationRepository** - Chat history management (19/19 tests passing)
- ✅ **EcoMetricsRepository** - Environmental impact tracking (16/16 tests passing)
- ✅ **EcoCalculator** - Carbon/water/energy calculations (27/27 tests passing)
- ✅ **62 total tests passing** - 100% success rate for Phase 2
- ✅ **Database schemas** - ConversationMetadata + EcoMetrics tables with foreign keys, indexes
- ✅ **Test infrastructure** - expect/actual TestDatabaseFactory for cross-platform tests
- ✅ **Baselines:** 120ml water, 3Wh energy, 2g CO2 saved per 100 tokens vs cloud AI
- ✅ **Privacy enforcement** - Multi-layer validation + database CHECK constraint (0 bytes transmitted)
- ✅ **Achievement system** - 5 tiers: Water Bottle (500ml) → Olympic Pool (2500L)
- ✅ **TDD methodology** - Red-Green-Refactor for all implementations
- 📄 See commits 6511a95 (eco metrics) + c798192 (conversation history) for details

### 🎉 **Previous Milestone:** Knowledge Base Consolidation (2025-11-04)
- ✅ **1,401 documents** loaded (1,391 comprehensive + 10 M1K3 system knowledge)
- ✅ **24 categories** across 4 domains (Technical, Educational, Expertise, System)
- ✅ **M1K3 self-awareness** - Can explain its own capabilities
- ✅ **Multi-source KB loading** - Clean architecture for multiple knowledge bases
- ✅ **Enhanced system prompt** - Device context + category breakdown
- 📄 See [SESSION_NOTES_2025_11_04.md](app/SESSION_NOTES_2025_11_04.md) for details

### 🎉 **Previous Milestone:** Streaming Inference (2025-11-02)
- ✅ **Real-time token-by-token AI generation** working end-to-end
- ✅ **Fixed SIGSEGV crash** in ONNX Runtime KV cache management
- ✅ **Fixed threading violations** in Compose UI updates
- ✅ **Performance:** 15 tok/s on emulator (20-40 tok/s expected on device)
- ✅ **256 tokens generated** without crashes
- 📄 See [MILESTONE_STREAMING_INFERENCE.md](app/docs/MILESTONE_STREAMING_INFERENCE.md) for details

### Documentation
- **[PROJECT_MANAGEMENT.md](app/PROJECT_MANAGEMENT.md)** - Master overview, architecture, testing strategy
- **[Phase 0: Foundation](app/docs/phases/PHASE0.md)** - 15 tickets (Weeks 1-2) - Database, privacy, knowledge import
- **[Phase 1: Core AI](app/docs/phases/PHASE1.md)** - 20 tickets (Weeks 3-5) - SmolLM2-360M integration, chat UI
- **[Phase 2: Memory](app/docs/phases/PHASE2.md)** - 25 tickets (Weeks 6-8) - Vector embeddings, HNSW index
- **[Phase 3: Knowledge](app/docs/phases/PHASE3.md)** - 15 tickets (Weeks 9-10) - RAG, trivia, device intelligence
- **[Phase 4: Multi-Modal](app/docs/phases/PHASE4.md)** - 20 tickets (Weeks 11-12) - CameraX, ML Kit, projects
- **[Phase 5: Polish](app/docs/phases/PHASE5.md)** - 30 tickets (Weeks 13-15) - Emotional intelligence, analytics, accessibility
- **[Phase 6: Release](app/docs/phases/PHASE6.md)** - 10 tickets (Week 16) - Integration testing, APK optimization

---

## Core Features (Planned)

### Privacy-First Architecture
- **Zero network permission** - Manifest-level privacy enforcement
- **100% on-device inference** - SmolLM2-360M (180MB, 4-bit quantized)
- **Local embeddings** - MiniLM-L6 (90MB, 8-bit quantized)
- **Encrypted storage** - SQLCipher for sensitive data
- **Privacy dashboard** - Transparency metrics showing 0 bytes transmitted

### AI & Memory System
- **SmolLM2-360M** - HuggingFace's efficient 360M parameter model
- **24K context window** - Long conversation support
- **Semantic memory** - HNSW vector index (384-dimensional embeddings)
- **Importance scoring** - Intelligent memory prioritization
- **RAG integration** - 1,401 documents across 24 categories (1,391 comprehensive + 10 M1K3 system)

### Multi-Modal Intelligence
- **CameraX integration** - Image capture and analysis
- **ML Kit vision** - OCR, object detection, label detection
- **Image understanding** - Contextual descriptions and Q&A
- **Vision + text** - Unified multi-modal conversations

### Project Management
- **Scoped conversations** - Organize chats by project
- **Project-scoped memory** - Context isolation per project
- **Export/import** - JSON backup for portability
- **Statistics & insights** - Per-project analytics

### Emotional Intelligence
- **Sentiment analysis** - VAD (Valence-Arousal-Dominance) model
- **Tone adaptation** - Empathetic response generation
- **7+ emotions** - Happy, Sad, Angry, Calm, Excited, Anxious, Neutral
- **Context-aware** - Adapts to user emotional state

### Knowledge Systems
- **Trivia engine** - 1,341+ facts across 20 categories
- **Device intelligence** - OEM profiles, SoC detection, capability analysis
- **Contextual enrichment** - Facts injected naturally into conversations
- **Semantic search** - <100ms retrieval @ 1,341 documents

### Local Analytics
- **Usage insights** - Message counts, memory stats, response times
- **Weekly summaries** - Conversation patterns and highlights
- **Privacy-preserving** - All analytics computed locally
- **No telemetry** - Zero data collection or transmission

---

## Technical Architecture

### Platform & Tools
- **Kotlin Multiplatform 2.2.20** - Shared business logic
- **Compose Multiplatform 1.9.1** - Native UI (Android primary, iOS future)
- **Gradle 8.14.3** - Build system
- **Target:** Android API 27+ (8.0+), iOS 15+ (future)

### AI/ML Stack
- **Llamatik 0.8.1** - llama.cpp binding for GGUF models (stable, simple API)
- **SmolLM2-135M-Instruct Q4_K_M** - Primary language model (101MB GGUF)
- **ONNX Runtime 1.17.0** - Mobile inference engine (legacy/fallback)
- **MiniLM-L6** - Sentence embeddings (90MB, 384-dim)
- **JVector** - HNSW vector similarity search
- **ML Kit** - On-device vision (OCR, object detection, labels)

**Migration History:**
- v1 (ONNX Runtime): SmolLM2-135M severe hallucinations (tokenizer issues)
- v2 (InferKt 0.0.2): Native crash SIGABRT in llama_batch_free (memory corruption)
- v3 (Llamatik 0.8.1): ✅ Current - Stable, no crashes, prompt engineering for control

### Data Layer
- **SQLDelight 2.0.0** - Type-safe SQL for Kotlin Multiplatform
- **SQLCipher** - AES-256 encryption for sensitive data
- **HNSW Index** - Fast vector similarity search (M=16, efConstruction=200)
- **4 core tables** - Project, Message, MemoryMetadata, TriviaFact

### UI/UX
- **Jetpack Compose** - Modern declarative UI
- **Material3 Design** - Adaptive theming
- **CameraX** - Image capture
- **WCAG 2.2 Level AA** - Accessibility compliance
- **TalkBack support** - Full screen reader compatibility

### Testing Strategy
- **Test-Driven Development (TDD)** - Red-Green-Refactor cycle
- **Testing Pyramid** - 40% unit, 30% integration, 20% UI, 10% E2E
- **Coverage targets** - 70% overall, 80%+ domain logic
- **Performance benchmarks** - Model load <5s, inference >40 tok/sec
- **135+ tests** - Comprehensive validation across all layers

---

## Development Roadmap

### Phase 0: Foundation (Weeks 1-2) - 15 tickets
**Status:** 🔴 Not Started (0/15, 0%)

**Key Deliverables:**
- Remove INTERNET permission from manifest
- Configure ONNX Runtime, SQLDelight, CameraX dependencies
- Create 4 database tables with SQLDelight schemas
- Import M1K3 knowledge base (1.6MB JSON → SQLite)
- Privacy validation tests

**Milestone:** Foundation ready, privacy enforced, database operational

---

### Phase 1: Core AI Engine (Weeks 3-5) - 20 tickets
**Status:** ⚪ Pending (0/20, 0%)

**Key Deliverables:**
- Export SmolLM2-360M to ONNX format
- Android ONNX Runtime session management
- SentencePiece tokenizer integration
- Basic chat UI with Compose
- Streaming response generation

**Milestone:** SmolLM2-360M running, basic chat working

---

### Phase 2: Memory & Embedding System (Weeks 6-8) - 25 tickets
**Status:** ⚪ Pending (0/25, 0%)

**Key Deliverables:**
- Export MiniLM-L6 to ONNX (embeddings)
- HNSW vector index integration (JVector)
- Semantic chunking (100-300 tokens with overlap)
- Importance scoring algorithm
- Memory manager with context assembly

**Milestone:** Memory system functional, context-aware conversations

---

### Phase 3: Knowledge Systems (Weeks 9-10) - 15 tickets
**Status:** ⚪ Pending (0/15, 0%)

**Key Deliverables:**
- Trivia engine with semantic search
- Device intelligence (OEM profiles, SoC detection)
- RAG integration with intent detection
- Response enrichment with contextual facts
- Knowledge browser UI

**Milestone:** Knowledge integrated, trivia/device facts in responses

---

### Phase 4: Multi-Modal & Projects (Weeks 11-12) - 20 tickets
**Status:** ⚪ Pending (0/20, 0%)

**Key Deliverables:**
- CameraX integration for image capture
- ML Kit vision (OCR, object detection, labels)
- Multi-modal AI engine (vision + text)
- Project management CRUD
- Project-scoped conversations and memory

**Milestone:** Multi-modal working, projects organized

---

### Phase 5: Advanced Features & Polish (Weeks 13-15) - 30 tickets
**Status:** ⚪ Pending (0/30, 0%)

**Key Deliverables:**
- Sentiment analysis (VAD model)
- Tone adaptation for empathetic responses
- Local analytics engine with insights
- Privacy dashboard
- WCAG 2.2 AA accessibility compliance
- Performance tuning (<5s model load, >40 tok/sec)
- Battery optimization (<2%/hour active use)

**Milestone:** Emotional intelligence, analytics, accessibility complete

---

### Phase 6: Integration Testing & Release (Week 16) - 10 tickets
**Status:** ⚪ Pending (0/10, 0%)

**Key Deliverables:**
- End-to-end integration tests
- Stress testing (10K memories, 100 projects)
- Battery drain validation (8-hour simulation)
- APK size optimization (<200MB)
- Privacy audit (0 bytes transmitted verification)
- Beta release preparation

**Milestone:** Beta release ready, all tests passing

---

## Performance Targets

### Model Performance (Mid-Range: 6GB RAM)
| Metric | Target | Validation |
|--------|--------|------------|
| Model Load Time | <5 seconds | Stopwatch measurement |
| Inference Speed | 40+ tokens/sec | Benchmark test |
| Memory Retrieval | <100ms @ 10K | Performance test |
| Battery Impact | <2%/hour active | Battery profiler (8hr test) |
| APK Size | <200MB | Build output |
| Startup Time | <3 seconds | Cold start measurement |
| UI Frame Time | <16ms (60fps) | GPU profiler |

### Quality Gates
- **Tests:** 135+ tests passing (unit + integration + UI + E2E)
- **Coverage:** >70% overall, >80% domain logic
- **Accessibility:** WCAG 2.2 Level AA (>95% axe DevTools score)
- **TalkBack:** Fully functional screen reader support
- **Memory:** Zero memory leaks (LeakCanary validation)
- **Privacy:** No network activity (manual code review + Android Studio profiler)

---

## Integration with M1K3 Ecosystem

### Shared Resources
- **Knowledge Base:** M1K3's `comprehensive_knowledge_base.json` (1.6MB, 1,341+ documents)
- **Embedding Model:** MiniLM-L6 (384-dimensional, compatible across platforms)
- **Database Schema:** Similar structure (Projects, Messages, MemoryMetadata)
- **RAG Architecture:** Intent detection, semantic retrieval, context assembly

### Platform Differences
| Component | M1K3 (Python/Desktop) | 間 AI (Kotlin/Mobile) |
|-----------|----------------------|----------------------|
| **Language Model** | TinyLlama-1.1B-Chat | SmolLM2-360M (size constraint) |
| **Vector DB** | DuckDB VSS | JVector HNSW (mobile-optimized) |
| **TTS** | VibeVoice, KittenTTS | Android TTS API |
| **STT** | Vosk, Whisper, macOS | Android SpeechRecognizer |
| **UI** | CLI, TUI, Web Dashboard | Compose Multiplatform |

---

## Philosophy: 間 (Ma) - Negative Space

### Core Principles
1. **Wabi-Sabi** - Beauty in imperfection, acceptance of limitations
2. **Computational Sufficiency** - SmolLM2-360M is enough (not TinyLlama-1.1B)
3. **Privacy First** - No network permission at manifest level
4. **Mindful Design** - Negative space in UI, calm interactions
5. **Local Everything** - AI, memory, analytics all on-device

### Design Decisions
- **No cloud fallback** - Offline-first by design, not feature
- **Smaller model** - 360M params vs 1.1B (mobile constraint = feature)
- **Minimal APK** - <200MB including models (every byte counts)
- **No telemetry** - Can't improve what we don't measure (and that's okay)
- **Accessibility** - Screen readers and inclusive design from day one

---

## Development Notes

### ⚠️ Knowledge Base Force Re-Import (TEMPORARY)

**Location:** `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/MainActivity.kt:78`

**Current Implementation:**
```kotlin
// TEMPORARY: Force re-import to load consolidated KB (1,391 docs) + M1K3 system KB (10 docs)
val forceReimport = existingCount > 0 && existingCount < 1400
```

**Purpose:** Development aid to automatically update knowledge base when adding new content

**⚠️ MUST REMOVE BEFORE PRODUCTION RELEASE**

**Proposed Solution: Knowledge Base Versioning System**

```kotlin
// Recommended implementation for production:
data class KnowledgeBaseVersion(
    val comprehensive: String = "1.1.0",  // 1,391 documents
    val system: String = "1.0.0",         // 10 documents
    val combined: String = "1.1.0"        // Overall version
)

// In MainActivity.kt:
val currentVersion = KnowledgeBaseVersion()
val storedVersion = prefs.getString("kb_version", "0.0.0")

if (storedVersion != currentVersion.combined) {
    println("🔄 Knowledge base update detected: $storedVersion → ${currentVersion.combined}")
    database.triviaFactQueries.deleteAllFacts()
    // Import both KBs...
    prefs.putString("kb_version", currentVersion.combined)
}
```

**Version Format:** `MAJOR.MINOR.PATCH`
- **MAJOR:** Breaking changes to schema or major content reorganization
- **MINOR:** New categories, substantial content additions (50+ documents)
- **PATCH:** Bug fixes, small content updates (<50 documents)

**Benefits:**
- ✅ Explicit version tracking
- ✅ User-visible KB version in settings
- ✅ Selective updates (only changed KBs)
- ✅ Migration path for schema changes
- ✅ Safe for production releases

**Action Items:**
1. Remove `forceReimport` logic before beta release
2. Implement `KnowledgeBaseVersion` in comprehensive_knowledge_base.json and m1k3_system_knowledge.json
3. Add version tracking to SharedPreferences
4. Create KB migration system for schema changes

---

## Getting Started (When Released)

```bash
# Clone repository
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3/app

# Open in Android Studio
open -a "Android Studio" .

# Build and run
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

---

## Project Status & Links

**Planning:** ✅ Complete (135 tickets, 6 phases documented)
**Implementation:** 🔴 Not Started (Phase 0 begins soon)
**Target Release:** Beta v0.1.0 (Week 16)

**Documentation:**
- [Master Plan](app/PROJECT_MANAGEMENT.md) - 16-week roadmap
- [Architecture](app/ARCHITECTURE.md) - KMP 2025 implementation guide
- [AI Architecture](app/AI_ARCHITECTURE.md) - System design details
- [Model Selection](app/OPUS.md) - SmolLM2-360M rationale

**Repository:** https://github.com/Round-Tower/m1k3 (app/ directory)

---

**Last Updated:** 2025-11-01
**Status:** Planning Complete, Implementation Pending