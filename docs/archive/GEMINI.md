# M1K3 - Local AI Assistant for Gemini

## Overview
M1K3 is a privacy-focused, local-first AI assistant designed for technical expertise and interaction. It features voice synthesis, a real-time avatar, a web dashboard, and multiple CLI interfaces. The system is built on a multi-backend AI architecture, ensuring compatibility and performance across various hardware.

## Status: ✅ PRODUCTION READY (2025-08-22)

### Core Features
- **Local AI Inference**: Runs AI models locally for privacy and offline use. Supports multiple backends including HuggingFace Transformers and ctransformers.
- **Decision Explanation Engine**: Provides transparent, detailed explanations for how the AI makes classification and configuration decisions, enhancing trust and debuggability.
- **Retrieval-Augmented Generation (RAG)**: Utilizes a vast knowledge base with over 1,300 documents across 20 categories to provide expert-level answers on technical topics, security, and more.
- **Intelligent Voice Synthesis**: Features content-aware Text-to-Speech (TTS) that modulates the voice based on the type of information being delivered (e.g., thinking, narration, answer).
- **Real-time Avatar System**: A web-based dashboard displays an avatar that reflects the AI's emotional state and operational status in real-time.
- **Advanced CLI Interfaces**: Offers multiple command-line experiences, from a classic CLI to a modern TUI and a full-screen interface.
- **Multi-Engine Speech-to-Text (STT)**: A robust voice input system with fallbacks (macOS Native, Vosk, Web Speech, Whisper) to ensure reliability.
- **PWA Deployment**: Can be deployed as a Progressive Web App, with device-adaptive AI that runs in the browser.
- **Privacy by Design**: All processing is done locally, ensuring no data leaves the user's device.

### System Prompt
The core directive for the AI model is:
> You are an on device, local, privacy first expert technical assistant and virtual eco friendly pet. Use only the provided context to answer questions about the current device [INPUT_DEVICE SPECS]. If the information needed to answer the question is not present in the context, state that you do not have the information. Provide clear and concise responses, avoiding unnecessary detail or speculation.

## AI Backend Architecture
M1K3 uses a multi-backend system to ensure maximum compatibility and performance:
1.  **HuggingFace Transformers (Primary)**: The default engine, using `TinyLlama-1.1B-Chat-v1.0` for broad compatibility across x86_64 and ARM64 platforms.
2.  **ctransformers (Secondary)**: Optimized for ARM64 with Metal GPU acceleration, using a GGUF quantized TinyLlama model.
3.  **SimpleAIEngine (Fallback)**: A mock engine with an 8K context window to guarantee functionality on any platform.

## Decision Explanation Engine
A key feature of M1K3 is its ability to explain its own reasoning. The `DecisionExplainerEngine` provides a transparent look into:
- **Intent Classification**: Why the system classified a query as a specific intent (e.g., "mathematical_calculation").
- **Pattern Matching**: Which specific keywords or phrases triggered the classification.
- **Context Influence**: How factors like user mood or task urgency affected the response.
- **Parameter Adjustments**: Why specific generation parameters (like temperature or max tokens) were chosen.
- **Confidence Scoring**: Factors that contributed to the confidence (or uncertainty) of the AI's decisions.

This allows for unparalleled transparency and helps users understand the AI's behavior.

## Speech-to-Text (STT) System
The STT system is designed for robust voice input with a multi-engine architecture that prioritizes privacy and reliability.
- **Engines**: macOS Native (on-device), Vosk (offline ML), Web Speech (cloud-based), and Whisper (optional, high-quality).
- **Features**: Automatic fallbacks, permission management for macOS, real-time audio monitoring, and diagnostic tools.
- **Usage**: In the CLI, press `ENTER` to activate voice input.

## Avatar System
The avatar provides a visual representation of the AI's state.
- **Dashboard**: A mobile-first, responsive web dashboard shows the avatar's emotions.
- **Emotions**: 8 emotions are tracked (Happy, Sad, Thinking, etc.).
- **Communication**: A WebSocket server provides live updates.
- **Status**: The animation system is currently being refactored to resolve conflicts between different animation loops and unify the API.

## RAG (Retrieval-Augmented Generation) System
The RAG system enhances the AI's knowledge with an extensive local database.
- **Knowledge Base**: Contains 1,341+ documents across 20 categories, including technical, educational, and security topics.
- **Process**: Uses semantic search (BAAI/bge-small-en-v1.5 embeddings) to find relevant documents and enrich the AI's context.
- **Privacy**: 100% local processing.

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
# Modern TUI
python m1k3.py --tui

# CLI with avatar dashboard (recommended)
python cli.py

# RAG-enhanced with expert knowledge
python cli.py --rag
```

## Interactive Commands
- `help`: Show available commands.
- `tokens`: Display token usage and eco impact.
- `voice`: Toggle voice synthesis.
- `avatar start`: Start the web dashboard.
- `stt status`: Show STT engine status.
- `clear`: Clear conversation context.
- `quit`: Exit the application.
