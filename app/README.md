# 間 AI - Privacy-First On-Device AI Companion

<p align="center">
  <strong>間</strong> (pronounced "ma" - meaning "negative space")
</p>

> A mobile AI assistant that never sends data to the cloud. Embracing wabi-sabi philosophy and computational sufficiency.

## Overview

**間 AI** is the mobile companion to M1K3, bringing privacy-first on-device AI to Android (and future iOS) through Kotlin Multiplatform. Every inference, every memory, every conversation stays on your device.

### Key Features

- **100% Local AI** - SmolLM2-135M runs entirely on-device via Llamatik
- **Zero Network Permission** - Manifest-level privacy enforcement
- **RAG System** - 1,401+ documents across 24 knowledge categories
- **Semantic Memory** - HNSW vector search with importance scoring
- **Eco Metrics** - Track environmental savings vs cloud AI
- **Pixel Art Avatar** - Activity-aware emotional feedback

## Current Status

**Phase:** AI Engine Migration Complete
**Tests:** 227 passing
**Model:** SmolLM2-135M-Instruct Q4_K_M (101MB GGUF)

### Recent Milestones

- **2025-12-22** - Project cleanup, 11GB recovered, 227 tests passing
- **2025-11-08** - RAG quality improvements, word boundary matching
- **2025-11-07** - Pixel art avatar with activity-based sprites
- **2025-11-06** - Llamatik 0.8.1 stable integration

## Quick Start

### Prerequisites

- Android Studio Ladybug (2024.2.1+)
- JDK 17+
- Android device/emulator with API 27+ (Android 8.0+)

### Build & Run

```bash
# Clone repository
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3/app

# Build debug APK
./gradlew :composeApp:assembleDebug

# Install on connected device
./gradlew :composeApp:installDebug

# Run tests
./gradlew :shared:test :composeApp:testDebugUnitTest
```

## Architecture

```
app/
├── composeApp/          # Android app (Compose Multiplatform)
│   ├── src/
│   │   ├── androidMain/ # Android-specific (MainActivity, assets)
│   │   ├── commonMain/  # Shared Kotlin code
│   │   └── commonTest/  # Cross-platform tests
│   └── build.gradle.kts
├── shared/              # Shared library module
│   └── src/
│       ├── commonMain/  # Core domain logic
│       └── commonTest/  # Shared tests
└── docs/                # Phase documentation
```

### Tech Stack

| Layer | Technology |
|-------|------------|
| **UI** | Compose Multiplatform 1.9.1 |
| **AI Engine** | Llamatik 0.8.1 (llama.cpp binding) |
| **Model** | SmolLM2-135M-Instruct Q4_K_M |
| **Database** | SQLDelight 2.0.0 |
| **Embeddings** | MiniLM-L6 (384-dim, ONNX) |
| **Vector Search** | JVector HNSW |
| **Build** | Gradle 8.14.3, Kotlin 2.2.20 |

## Features

### AI Chat
- Real-time streaming token generation
- Query-type-aware token limits (educational, technical, factual, conversational)
- Context-aware responses with semantic memory

### RAG (Retrieval-Augmented Generation)
- 24 knowledge categories
- Intent classification with word boundary matching
- Semantic search < 100ms @ 1,401 documents

### Privacy Dashboard
- Zero bytes transmitted verification
- Local-only processing confirmation
- No network permission in manifest

### Eco Metrics
- Carbon savings vs cloud AI
- Water conservation tracking
- Energy efficiency calculations
- Achievement system (Water Bottle → Olympic Pool)

## Project Structure

### Key Directories

```
composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/
├── domain/
│   ├── memory/          # ImportanceCalculator, ConversationContext
│   └── eco/             # EcoCalculator, EcoMetrics
├── memory/              # SemanticChunker, MemoryChunk
├── rag/                 # IntentClassifier, RAGManager
├── ui/                  # Compose screens and components
└── data/                # Repositories, database access
```

### Key Files

| File | Purpose |
|------|---------|
| `IntentClassifier.kt` | RAG query classification (20 intents) |
| `SemanticChunker.kt` | Token-based text chunking with overlap |
| `ImportanceCalculator.kt` | Memory importance scoring |
| `LlamaCppEngine.kt` | Llamatik wrapper for inference |
| `EcoCalculator.kt` | Environmental impact calculations |

## Testing

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :shared:test
./gradlew :composeApp:testDebugUnitTest

# Run with detailed output
./gradlew test --info
```

### Test Categories

- **Unit Tests** - Domain logic (ImportanceCalculator, EcoCalculator)
- **Integration Tests** - Database operations, RAG pipeline
- **UI Tests** - Compose component testing

## Development

### Knowledge Base Versioning

The app uses semantic versioning for knowledge base updates:

```kotlin
// In MainActivity.kt
val currentKbVersion = "1.1.0"  // 1,391 comprehensive + 10 system = 1,401 docs
```

Increment version when updating KB content - the app auto-reimports on mismatch.

### Adding New Intents

1. Add enum entry to `IntentClassifier.Intent`
2. Define keywords (order matters - specific before general)
3. Update `getRetrievalLimit()` if needed
4. Add tests in `IntentClassifierTest.kt`

## Philosophy: 間 (Ma)

### Core Principles

1. **Wabi-Sabi** - Beauty in imperfection
2. **Computational Sufficiency** - SmolLM2-135M is enough
3. **Privacy First** - No network permission
4. **Mindful Design** - Negative space in UI
5. **Local Everything** - AI, memory, analytics on-device

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Full project documentation
- [PROJECT_MANAGEMENT.md](PROJECT_MANAGEMENT.md) - Development roadmap
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) - AI system design
- [Phase Docs](docs/phases/) - Detailed phase planning

## License

Part of the M1K3 project. See root LICENSE file.

---

**Last Updated:** 2025-12-22
