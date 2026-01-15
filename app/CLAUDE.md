# 間 AI - On-Device Mobile AI

Privacy-first AI companion via Kotlin Multiplatform. Zero network permission.

## Commands
```bash
# Build
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug

# Test
./gradlew :composeApp:testDebugUnitTest           # Unit tests
./gradlew :composeApp:connectedDebugAndroidTest   # Instrumented

# Single test class
./gradlew :composeApp:testDebugUnitTest --tests "*.MemoryRepositoryTest"
```

## Test Performance

**gradle.properties is optimized for fast test execution:**
- Parallel builds (6 workers)
- Kotlin incremental compilation + caching
- ParallelGC for faster builds
- Kapt optimization for annotation processing

**TDD Workflow:**
1. Write failing test first (Red)
2. Implement minimal code to pass (Green)
3. Refactor while keeping tests green
4. Use `runCurrent()` in coroutine tests to avoid `advanceUntilIdle()` cleanup issues
5. Disable background jobs in ViewModels during tests with constructor params

## Structure
```
├── composeApp/src/
│   ├── commonMain/kotlin/app/m1k3/ai/assistant/
│   │   ├── ai/              # AI interfaces, availability
│   │   ├── embedding/       # EmbeddingEngine, repositories
│   │   ├── memory/          # ContextAssembler, MemoryRepository
│   │   ├── knowledge/       # SemanticRetrievalService
│   │   ├── config/          # GenerationConstants
│   │   └── di/              # Koin modules
│   ├── androidMain/         # Android implementations
│   │   ├── ai/ondevice/     # LlamaCpp, MlKitGenAi engines
│   │   └── embedding/       # MiniLM, Gemma engines
│   └── iosMain/             # iOS (future)
├── codingModule/            # Dynamic feature (Qwen code gen)
└── docs/phases/             # Phase documentation
```

## Stack (from libs.versions.toml)
- **Kotlin**: 2.2.20
- **Compose Multiplatform**: 1.9.2
- **SQLDelight**: 2.0.2
- **ONNX Runtime**: 1.23.2
- **Target**: Android API 27+, iOS 15+ (future)

## AI Models
- **SmolLM2-360M**: Primary LLM (LlamaCpp engine)
- **MlKitGenAi**: Google's on-device AI (fallback)
- **Gemma/MiniLM**: Embeddings (384-dim)
- **Qwen2.5-Coder**: Code generation (dynamic module)

## Key Patterns
- Sealed classes for UI state
- `expect`/`actual` for platform code
- Koin for DI (`di/` modules)
- Flow for async streams

## Privacy
- **Zero INTERNET permission** in manifest
- SQLCipher for encrypted storage

## Docs
- Architecture: `ARCHITECTURE.md`
- AI details: `AI_ARCHITECTURE.md`
- Phases: `docs/phases/PHASE*.md`
- Qwen: `docs/phases/QWEN_SUMMARY.md`
