# Refactor Domain Layer to Shared Module

## Goal
Move all domain layer code from `composeApp/src/commonMain/kotlin/.../domain/` to `shared/src/commonMain/kotlin/` for proper KMP architecture.

## Why
- `shared/` is the conventional KMP location for platform-agnostic business logic
- Enables true code sharing across Android, iOS, Desktop
- Cleaner dependency graph: `shared` → pure Kotlin, `composeApp` → UI only
- Tests run faster without Compose/Android dependencies

## Current State

Domain code is in `composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/domain/`:
```
domain/
├── ai/services/           # GenerationConfigService
├── chat/                  # ChatFormat, ContextAssembler, ChatFormatter
├── memory/                # MemoryChunk, SemanticChunker, ImportanceCalculator
├── rag/                   # Intent, IntentClassifier, RetrievedFact
├── repositories/          # KnowledgeRepository, MemoryRepository, EmbeddingRepository
├── tools/                 # Tool, ToolCall, ToolResult, ToolRegistry, executors
└── usecases/              # All use cases (chat, memory, rag, tools)
```

Tests in `composeApp/src/commonTest/kotlin/.../domain/`

## Target State

Move to `shared/src/commonMain/kotlin/app/m1k3/ai/domain/`:
```
shared/src/
├── commonMain/kotlin/app/m1k3/ai/domain/
│   ├── ai/
│   ├── chat/
│   ├── memory/
│   ├── rag/
│   ├── repositories/
│   ├── tools/
│   └── usecases/
└── commonTest/kotlin/app/m1k3/ai/domain/
    └── (all domain tests)
```

## Steps

### 1. Update shared module build.gradle.kts
```kotlin
// Add dependencies needed by domain layer
commonMain.dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}
commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

### 2. Update composeApp build.gradle.kts
```kotlin
// Add dependency on shared module
commonMain.dependencies {
    implementation(project(":shared"))
}
```

### 3. Move domain files
```bash
# Create target directories
mkdir -p shared/src/commonMain/kotlin/app/m1k3/ai/domain
mkdir -p shared/src/commonTest/kotlin/app/m1k3/ai/domain

# Move domain code
mv composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/domain/* \
   shared/src/commonMain/kotlin/app/m1k3/ai/domain/

# Move domain tests
mv composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/domain/* \
   shared/src/commonTest/kotlin/app/m1k3/ai/domain/
```

### 4. Update package declarations
Change all files from:
```kotlin
package app.m1k3.ai.assistant.domain.tools
```
To:
```kotlin
package app.m1k3.ai.domain.tools
```

### 5. Update imports in composeApp
Search and replace in `composeApp/`:
```
app.m1k3.ai.assistant.domain → app.m1k3.ai.domain
```

### 6. Update Koin modules
- `AppModule.kt` imports will need updating
- Domain registrations stay in `appModule`
- Platform implementations stay in `platformModule`

### 7. Verify
```bash
./gradlew :shared:testDebugUnitTest      # Domain tests
./gradlew :composeApp:testDebugUnitTest  # Integration tests
./gradlew :composeApp:assembleDebug      # Full build
```

## Files to Move (~50 files)

### Domain Entities
- `tools/Tool.kt`, `ToolCall.kt`, `ToolResult.kt`, `ToolError.kt`, `ToolParameter.kt`, `ToolCategory.kt`
- `chat/format/ChatFormat.kt`, `MessageRole.kt`
- `chat/GenerationStats.kt`, `QueryType.kt`
- `memory/MemoryChunk.kt`, `MemorySearchResult.kt`, `MemoryStats.kt`
- `rag/Intent.kt`, `RetrievedFact.kt`

### Domain Services
- `tools/services/ToolCallParser.kt`, `DefaultToolCallParser.kt`, `ToolExecutor.kt`, `ToolRegistry.kt`
- `chat/services/ChatFormatter.kt`, `DefaultChatFormatter.kt`, `ContextAssembler.kt`
- `memory/services/SemanticChunker.kt`
- `rag/services/IntentClassifier.kt`
- `ai/services/GenerationConfigService.kt`

### Domain Repositories (Interfaces)
- `repositories/KnowledgeRepository.kt`
- `repositories/MemoryRepository.kt`
- `repositories/EmbeddingRepository.kt`

### Domain Use Cases
- `usecases/chat/ProcessLlmOutputUseCase.kt`, `ProcessedOutput.kt`
- `usecases/memory/CreateMemoryUseCase.kt`, `SearchMemoriesUseCase.kt`
- `usecases/rag/EnrichPromptWithRAGUseCase.kt`
- `usecases/tools/ExecuteToolUseCase.kt`, `ParseToolCallUseCase.kt`

### Domain Tests (~25 files)
All tests in `composeApp/src/commonTest/kotlin/.../domain/`

## What Stays in composeApp

- `chat/ChatScreenViewModel.kt`, `ChatUiState.kt`
- `chat/usecase/ChatWithToolsUseCase.kt`, `ContextRetrievalUseCase.kt`
- `embedding/EmbeddingEngine.kt`, `EmbeddingsViewModel.kt`
- `memory/MemoryManager.kt`, `MemoryDataSource.kt`
- `knowledge/SemanticRetrievalService.kt`, `KnowledgeRepositoryImpl.kt`
- `di/AppModule.kt`
- All platform implementations in `androidMain/`, `iosMain/`

## Risks
- Import errors if any file missed
- Koin registration order issues
- Test discovery if package structure changes

## Success Criteria
- [ ] All tests pass in both modules
- [ ] App builds and runs
- [ ] No domain code imports Android/iOS/Compose
- [ ] `shared/` has zero platform dependencies
