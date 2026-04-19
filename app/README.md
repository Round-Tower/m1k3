# 間 AI - Privacy-First On-Device AI Companion

<p align="center">
  <strong>間</strong> (pronounced "ma" - meaning "negative space")
</p>

> A mobile AI assistant that never sends data to the cloud. Embracing wabi-sabi philosophy and computational sufficiency.

## Overview

**間 AI** is the mobile companion to M1K3, bringing privacy-first on-device AI to Android (and future iOS) through Kotlin Multiplatform. Every inference, every memory, every conversation stays on your device.

### Key Features

- **On-Device Chat** — llama.cpp via our own Ma JNI bridge. Three model tiers:
  Qwen3 0.6B (Mini), Qwen3 1.7B (Lil), Gemma 4 E2B (Big).
- **Your device is the cloud** — network is user-initiated only (model
  downloads + web-search tool). See `docs/adr/0006-user-initiated-network.md`.
- **Native Tool Calling** — GBNF grammar-constrained decoding for reliable
  JSON tool calls across any GGUF with a chat template.
- **Semantic Memory + RAG** — embedding-based vector search with importance
  scoring. Knowledge base import on first launch.
- **Eco Metrics** — tracks `cloudBytesAvoided` vs what a cloud LLM round-trip
  would have cost, plus real network bytes for the two user-initiated paths.
- **3D Avatar** — Filament-based SceneView with per-surface instances,
  emotion-driven animations.

## Current Status

**Active development** — see `.claude/project-memory.md` for the current
session log. Some rough coordinates:

- **Models**: Mini (Qwen3-0.6B Q4_K_M, ~484MB), Lil (Qwen3-1.7B Q4_K_M,
  ~1.28GB), Big (Gemma 4 E2B GGUF, ~2.4GB) — all downloaded on first launch
  from HuggingFace.
- **Inference**: Ma (our JNI bridge to llama.cpp), with native tool calling
  via GBNF grammar samplers.
- **Device**: Primary target Pixel 9a (Tensor G4, arm64-v8a + i8mm).

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

Current versions live in `gradle/libs.versions.toml` — treat that as canonical.
At time of writing:

| Layer | Technology |
|-------|------------|
| **UI** | Compose Multiplatform 1.9.2 |
| **AI Engine** | Ma — our JNI bridge to llama.cpp (submodule, `composeApp/src/androidMain/cpp/llama.cpp`) |
| **Models** | Qwen3 0.6B / 1.7B + Gemma 4 E2B (GGUF, Q4_K_M) |
| **Database** | SQLDelight 2.0.2 (+ SQLCipher parked — see SQLCipher TODO) |
| **Embeddings** | MiniLM-L6 (384-dim, ONNX) |
| **Vector Search** | Linear fallback (JVector HNSW gated on Maven Central availability) |
| **Native tool calling** | GBNF grammar-constrained decoding |
| **Build** | AGP 9.0.1, Kotlin 2.2.20, NDK 28.2.13676358 (pinned) |

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
- Chat inference stays 100% on-device.
- Network is user-initiated only: model downloads + web-search tool (see ADR-0006).
- `ManifestPrivacyTest` enforces: no analytics SDKs, no Firebase, network callers
  constrained to a small allow-list.

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
| `LlamaCppEngine.kt` | Ma (our llama.cpp JNI bridge) wrapper for inference |
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

1. **Wabi-Sabi** — beauty in imperfection.
2. **Computational Sufficiency** — a 0.6B–2B model on your phone is enough.
3. **Your Device Is the Cloud** — network is user-initiated only.
4. **Mindful Design** — negative space in UI.
5. **Local Everything** — AI, memory, analytics on-device.

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Full project documentation
- [PROJECT_MANAGEMENT.md](PROJECT_MANAGEMENT.md) - Development roadmap
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) - AI system design
- [Phase Docs](docs/phases/) - Detailed phase planning

## License

Part of the M1K3 project. See root LICENSE file.

---

**Last meaningful refresh:** 2026-04-19. For current state, prefer
`.claude/project-memory.md` and `git log` over this file.
