# 間 AI - Compose Multiplatform Mobile App

> Privacy-first on-device AI assistant for Android & iOS

## Overview

This is the Kotlin Multiplatform (KMP) mobile application for 間 AI (pronounced "ma"), featuring:
- **100% on-device AI** - Gemma 3 270M via Llamatik (llama.cpp binding)
- **Platform-native engines** - ML Kit GenAI (Android) + Apple Foundation Models (iOS, planned)
- **RAG knowledge system** - 1,401 documents across 24 categories
- **Semantic memory** - Embeddings + vector search for context-aware conversations
- **Privacy-first** - Zero network permission, all processing local

## Project Structure

```
composeApp/
├── src/
│   ├── androidMain/kotlin/          # Android-specific implementations
│   │   └── app/m1k3/ai/assistant/
│   │       ├── MainActivity.kt      # App entry point, Koin DI, Filament 3D
│   │       ├── ai/                  # AI engines (LlamaCpp, OnDeviceAi)
│   │       ├── ui/                  # Compose screens (Chat, History, Settings)
│   │       ├── avatar/              # 3D avatar rendering (Filament)
│   │       ├── memory/              # Embedding engines, vector search
│   │       └── embedding/           # MiniLM-L6, Gemma embedding
│   │
│   ├── commonMain/kotlin/           # Shared business logic
│   │   └── app/m1k3/ai/assistant/
│   │       ├── ai/                  # BaseLlmEngine interface, OnDeviceAi abstraction
│   │       ├── chat/                # ChatViewModel, state management
│   │       ├── memory/              # MemoryManager, chunking, importance
│   │       ├── rag/                 # RAG retrieval, intent classification
│   │       ├── repository/          # Data layer (Conversation, EcoMetrics)
│   │       ├── domain/              # Business entities
│   │       └── database/            # SQLDelight schemas
│   │
│   ├── commonTest/kotlin/           # Shared unit tests
│   │   └── app/m1k3/ai/assistant/
│   │       ├── ai/                  # AI engine tests (MockLlmEngine)
│   │       ├── memory/              # Memory system tests (341 passing!)
│   │       ├── rag/                 # RAG retrieval quality tests
│   │       └── repository/          # Repository tests
│   │
│   └── androidTest/kotlin/          # Android instrumentation tests
│
├── build.gradle.kts                 # Project dependencies & build config
└── README.md                        # This file
```

## Architecture

### Multi-Layer Architecture (Clean Architecture)

```
┌─────────────────────────────────────────────────┐
│              UI Layer (Compose)                 │
│  ChatScreen, HistoryScreen, SettingsScreen      │
└─────────────────┬───────────────────────────────┘
                  │ ViewModels
┌─────────────────▼───────────────────────────────┐
│          Domain Layer (Business Logic)          │
│  ChatViewModel, MemoryManager, RAGManager       │
└─────────────────┬───────────────────────────────┘
                  │ Repositories
┌─────────────────▼───────────────────────────────┐
│            Data Layer (Persistence)             │
│  ConversationRepo, MemoryRepo, EcoMetricsRepo   │
└─────────────────┬───────────────────────────────┘
                  │ Database
┌─────────────────▼───────────────────────────────┐
│        SQLDelight + SQLCipher (planned)         │
│  4 tables: Project, Message, Memory, EcoMetrics │
└─────────────────────────────────────────────────┘
```

### AI Engine Architecture

**OnDeviceAi Interface** (Platform-Agnostic)
```
OnDeviceAi (commonMain)
├─> AndroidOnDeviceAi (Android)
│   ├─> MlKitGenAiEngine (Gemini Nano via Google Play)
│   └─> LlamaCppFallbackEngine (Gemma 3 270M GGUF)
│
└─> IosOnDeviceAi (iOS, planned)
    ├─> AppleFoundationModels (iOS 26+)
    └─> LlamaCppFallbackEngine (Gemma 3 270M GGUF)
```

**BaseLlmEngine Interface** (Direct LLM Inference)
```
BaseLlmEngine (commonMain)
├─> LlamaCppEngine (Android) - Llamatik 0.9.0 binding
└─> MockLlmEngine (Testing) - Deterministic responses
```

**Why Two Interfaces?**
- **OnDeviceAi**: High-level, platform-aware (checks availability, handles downloads)
- **BaseLlmEngine**: Low-level, direct GGUF inference (always available)

### Memory & RAG System

```
User Query
    │
    ├─> Intent Classification (IntentClassifier)
    │   └─> 24 categories (MATH, CODE, HISTORY, etc.)
    │
    ├─> RAG Retrieval (RAGManager)
    │   ├─> Semantic search via embeddings
    │   └─> Top-K relevant documents
    │
    ├─> Memory Retrieval (MemoryManager)
    │   ├─> Vector search (MiniLM-L6 embeddings)
    │   └─> Context assembly (ContextAssembler)
    │
    └─> LLM Generation (BaseLlmEngine)
        └─> Gemma 3 270M @ 10+ tok/s
```

## Key Technologies

### AI/ML Stack
- **Llamatik 0.9.0** - Kotlin llama.cpp binding for GGUF models
- **Gemma 3 270M IQ3_XXS** - 176MB quantized model (10+ tok/s on emulator)
- **ML Kit GenAI** - Google Play AI Core (Gemini Nano, Android 14+)
- **MiniLM-L6** - Sentence embeddings (384-dim, planned)
- **JVector** - HNSW vector similarity search (planned)

### Platform
- **Kotlin Multiplatform 2.2.20** - Shared business logic
- **Compose Multiplatform 1.9.1** - Declarative UI
- **SQLDelight 2.0.0** - Type-safe SQL
- **Koin 4.0** - Dependency injection
- **Filament** - 3D avatar rendering (Android)

### Testing
- **341 passing tests** - Comprehensive coverage
- **Kotlin Test** - Multiplatform testing framework
- **MockK** - Mocking (Android tests)
- **TDD methodology** - Red-Green-Refactor

## Development Workflow

### Prerequisites
```bash
# Required
- Android Studio Ladybug (2024.2.1+)
- JDK 17+
- Android SDK 34+

# Optional
- Physical Android device (for ML Kit GenAI testing)
```

### Setup
```bash
# Clone repo
git clone https://github.com/Round-Tower/m1k3.git
cd m1k3/app

# Open in Android Studio
open -a "Android Studio" .

# Sync Gradle
./gradlew :composeApp:assemble
```

### Running the App
```bash
# Android emulator
./gradlew :composeApp:installDebug

# Physical device (USB debugging enabled)
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Running Tests
```bash
# All tests
./gradlew test

# Common (shared) tests only
./gradlew :composeApp:testDebugUnitTest

# Android instrumentation tests
./gradlew :composeApp:connectedAndroidTest

# Specific test class
./gradlew test --tests "MemoryManagerTest"
```

### Code Quality
```bash
# Kotlin linter
./gradlew ktlintCheck

# Fix formatting
./gradlew ktlintFormat

# Detekt static analysis
./gradlew detekt
```

## Common Development Tasks

### Adding a New Screen
1. Create Composable in `commonMain/ui/` (or `androidMain/ui/` if Android-specific)
2. Add ViewModel in `commonMain/` for business logic
3. Register route in `MainActivity.kt` NavHost
4. Add navigation from existing screen

Example:
```kotlin
// commonMain/ui/MyNewScreen.kt
@Composable
fun MyNewScreen(navController: NavController) {
    // UI implementation
}

// MainActivity.kt
composable("my-route") { MyNewScreen(navController) }
```

### Adding a New Repository
1. Define interface in `commonMain/repository/`
2. Implement in `commonMain/repository/` using SQLDelight
3. Add to Koin DI module (`androidMain/di/AppModule.kt`)
4. Write tests in `commonTest/repository/`

### Adding Platform-Specific Code
```kotlin
// commonMain/MyFeature.kt
expect fun platformSpecificFunction(): String

// androidMain/MyFeature.android.kt
actual fun platformSpecificFunction(): String = "Android implementation"

// iosMain/MyFeature.ios.kt (future)
actual fun platformSpecificFunction(): String = "iOS implementation"
```

## Current Status (as of 2025-12-25)

### ✅ Completed
- **Phase 0 (Foundation)**: 93% - Database, knowledge import, privacy
- **Phase 1 (Core AI)**: 100% - Gemma 3 integration, chat UI, streaming
- **Phase 3 (Knowledge)**: 100% - RAG system, 1,401 documents, intent classification

### 🟡 In Progress
- **Phase 2 (Memory)**: 48% - Embeddings working, HNSW pending
- **Phase 4 (Multi-Modal)**: 40% - ML Kit GenAI ready, CameraX pending
- **Phase 5 (Polish)**: 30% - Avatar system done, accessibility pending

### ⚪ Planned
- **Phase 6 (Release)**: 0% - Integration testing, APK optimization

**Overall Progress**: 78/135 tickets (58%)

## Testing Strategy

### Test Pyramid
- **Unit Tests (40%)**: 227 passing - Core logic, ViewModels, utilities
- **Integration Tests (30%)**: Memory system, RAG retrieval, repositories
- **UI Tests (20%)**: Compose snapshot testing (planned)
- **E2E Tests (10%)**: Full user flows (planned)

### Key Test Suites
- `MemoryManagerTest` - Memory creation, retrieval, importance scoring
- `RAGManagerTest` - Document retrieval, semantic search
- `IntentClassifierTest` - Intent detection accuracy
- `ConversationRepositoryTest` - Chat history persistence
- `EcoMetricsRepositoryTest` - Environmental impact tracking

### Running Specific Tests
```bash
# Memory system tests
./gradlew test --tests "*MemoryManagerTest"

# RAG tests
./gradlew test --tests "*RAGManagerTest"

# All repository tests
./gradlew test --tests "*Repository*"
```

## Troubleshooting

### Build Failures
```bash
# Clean build
./gradlew clean :composeApp:assemble

# Clear Gradle cache
rm -rf ~/.gradle/caches/
./gradlew --refresh-dependencies
```

### Test Failures
```bash
# Run with stack traces
./gradlew test --stacktrace

# Run specific failing test
./gradlew test --tests "MyTest.myFailingTest" --info
```

### ML Kit GenAI Not Working
- Requires Android 14+ (API 34+)
- Requires Google Play Services
- Check device eligibility: Settings → Google → Services
- Fallback to LlamaCpp is automatic

### App Crashes on Startup
- Check logcat: `adb logcat | grep M1K3`
- Verify Filament 3D assets exist
- Clear app data: Settings → Apps → 間 AI → Clear Data

## Code Style Guidelines

### Kotlin Conventions
- **Naming**: PascalCase classes, camelCase functions/properties
- **Line length**: 120 characters max
- **Indentation**: 4 spaces (no tabs)
- **Imports**: Use explicit imports, no wildcards

### Architecture Principles
- **Single Responsibility**: One class, one purpose
- **Dependency Injection**: Use Koin, avoid manual instantiation
- **Immutability**: Prefer `val` over `var`, use data classes
- **Error Handling**: Use `Result<T>` for recoverable errors
- **Testing**: Write tests first (TDD), aim for >70% coverage

### Documentation
- **KDoc**: Document public APIs, complex algorithms
- **Comments**: Explain *why*, not *what*
- **TODOs**: Link to GitHub issues, include context

Example:
```kotlin
/**
 * Calculates importance score for memory retention.
 *
 * Uses heuristics based on:
 * - Message length (longer = more important)
 * - Question marks (questions are important)
 * - User vs assistant role
 *
 * @param content Message text
 * @param context Conversation context
 * @return Importance score [0.0, 1.0]
 */
fun calculateImportance(content: String, context: ConversationContext): Float {
    // Implementation
}
```

## Contributing

### Pull Request Checklist
- [ ] Tests pass: `./gradlew test`
- [ ] Code formatted: `./gradlew ktlintFormat`
- [ ] No lint errors: `./gradlew ktlintCheck`
- [ ] Documentation updated (if needed)
- [ ] Tested on Android emulator/device
- [ ] Reviewed by kmp-mobile-ai-reviewer agent (for complex changes)

### Branching Strategy
- **main** - Production-ready code
- **develop** - Integration branch
- **feature/X** - New features
- **fix/X** - Bug fixes
- **refactor/X** - Code improvements

## Resources

### Documentation
- [PROJECT_MANAGEMENT.md](../PROJECT_MANAGEMENT.md) - 16-week roadmap
- [AI_ARCHITECTURE.md](../AI_ARCHITECTURE.md) - System design
- [CLAUDE.md](../../CLAUDE.md) - Project overview

### External Links
- [Kotlin Multiplatform Docs](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Llamatik GitHub](https://github.com/JacobSyndeo/Llamatik)
- [Gemma Models](https://ai.google.dev/gemma)
- [ML Kit GenAI](https://developers.google.com/ml-kit/generative-ai)

---

**Last Updated**: 2025-12-25
**Version**: 0.1.0-alpha
**Status**: Active Development
