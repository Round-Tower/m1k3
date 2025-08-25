# M1K3 Core Source Architecture

This directory contains the core application logic organized by functional domains.

## Architecture Overview

M1K3 uses a modular architecture with clear separation of concerns:

- **engines/**: AI inference and voice synthesis backends
- **cli/**: Command-line interface components  
- **avatar/**: Real-time avatar visualization system
- **rag/**: Retrieval-Augmented Generation system
- **tts/**: Text-to-speech processing pipeline
- **models/**: Model management and loading infrastructure
- **utils/**: Shared utilities and helper functions

## Design Principles

1. **Multi-backend compatibility** - Each engine supports multiple implementations with automatic fallback
2. **Local-first processing** - All AI operations run locally for privacy
3. **Modular design** - Components can be used independently or together
4. **Performance optimization** - Efficient resource usage and fast startup times
5. **Developer experience** - Clear interfaces and comprehensive testing

## Key Abstractions

- **Engine Pattern**: Consistent interface for AI/voice backends with hot-swapping capability
- **Pipeline Architecture**: Composable processing stages for audio and text
- **Event-driven Communication**: WebSocket-based real-time updates
- **Configuration Management**: Centralized settings with environment-specific overrides