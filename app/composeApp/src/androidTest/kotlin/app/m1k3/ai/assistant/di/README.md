# Koin DI Tests

Comprehensive test suite for validating dependency injection configuration.
Catches missing dependencies, circular references, and scope issues **before runtime**.

## Why DI Tests Matter

Koin is a runtime DI framework - errors only appear when dependencies are resolved.
These tests catch configuration issues at build time:

- ✅ **NoDefinitionFoundException** - Missing dependency registrations
- ✅ **StackOverflowError** - Circular dependency chains
- ✅ **Type mismatches** - Wrong interface implementations
- ✅ **Scope issues** - Lifecycle management problems

## Test Coverage

### AppModuleTest (`appModule` validation)
Tests all common (shared) dependencies:
- Database layer (MaDatabase)
- Repositories (Conversation, EcoMetrics, Memory)
- Domain services (IntentClassifier, SemanticChunker)
- Use cases (EnrichPromptWithRAG, ExecuteTool)
- Tool calling infrastructure

### PlatformModuleTest (`platformModule` validation)
Tests Android-specific dependencies:
- **EmbeddingEngine** (critical fix - was missing)
- Platform abstractions (DeviceInfo, DateTime, Preferences)
- AI engines (LlamaCpp, ML Kit, OnDeviceAi)
- RAGManager (requires EmbeddingEngine)
- Tool registry
- Database initializer

### ViewModelDITest (ViewModel creation)
Tests all ViewModels can be instantiated:
- InitializationViewModel
- ChatScreenViewModel (with `parametersOf`)
- CodeGenerationViewModel
- EcoStatsViewModel
- HistoryViewModel

### KoinIntegrationTest (Full dependency graph)
Integration tests for complete DI configuration:
- Full module validation with `checkModules()`
- Circular dependency detection
- Singleton vs factory scoping
- Koin startup performance

## Running Tests

### All DI Tests
```bash
./gradlew :composeApp:connectedDebugAndroidTest
```

### Single Test Class
```bash
# Note: Android instrumented tests don't support --tests flag
# Filter tests by modifying AndroidManifest.xml or using Android Studio

# Via Android Studio:
# 1. Right-click on test file
# 2. Select "Run 'AppModuleTest'"
```

### Build Validation (No Device Required)
```bash
# Compile tests to check for syntax errors
./gradlew :composeApp:compileDebugAndroidTestKotlin

# Full build with DI validation
./gradlew :composeApp:assembleDebug
```

## Test Output

### Success
```
AppModuleTest > appModule provides MaDatabase PASSED
AppModuleTest > appModule provides EmbeddingEngine PASSED
PlatformModuleTest > EmbeddingEngine is registered PASSED
```

### Failure (Missing Dependency)
```
org.koin.core.error.NoDefinitionFoundException:
No definition found for type 'EmbeddingEngine'.
Check your Koin configuration!
```

## Key Regression Tests

### `EmbeddingEngine is registered` (PlatformModuleTest)
- **Before Fix**: `getOrNull<EmbeddingEngine>()` returned null
- **After Fix**: Properly registered in `platformModule`
- **Impact**: RAGManager and SemanticRetrievalService now work

### `ChatScreenViewModel dependencies` (ViewModelDITest)
- **Before Fix**: `getOrNull<RAGManager>()` (nullable)
- **After Fix**: `get<RAGManager>()` (required)
- **Impact**: Tool calling and RAG always available

## Troubleshooting

### Test Won't Compile
```bash
# Clean build and sync
./gradlew clean
./gradlew :composeApp:assemble DebugAndroidTest
```

### Test Hangs or Times Out
```bash
# Check connected device
adb devices

# Clear app data
adb shell pm clear app.m1k3.ai.assistant.debug
```

### Koin Start Fails in Test
```kotlin
// Verify Android Context is available
startKoin {
    androidContext(InstrumentationRegistry.getInstrumentation().targetContext)
    modules(appModule, platformModule)
}
```

## Adding New Dependencies

When adding new Koin dependencies, follow this workflow:

1. **Register in Module** (`AppModule.kt` or `PlatformModule.android.kt`)
```kotlin
single<NewDependency> {
    NewDependencyImpl(get())
}
```

2. **Add Test** (e.g., `AppModuleTest.kt`)
```kotlin
@Test
fun `appModule provides NewDependency`() {
    val dependency = getKoin().get<NewDependency>()
    assertNotNull(dependency)
}
```

3. **Run Tests**
```bash
./gradlew :composeApp:connectedDebugAndroidTest
```

4. **Update Integration Test** (`KoinIntegrationTest.kt`)
```kotlin
val allDeps = listOf(
    // ... existing
    getKoin().get<NewDependency>()
)
```

## Best Practices

### ✅ DO
- Test every registered dependency
- Use `checkModules()` for full graph validation
- Test ViewModels with `parametersOf()`
- Add regression tests for fixed bugs
- Keep tests fast (mock heavy dependencies)

### ❌ DON'T
- Skip testing optional dependencies (`getOrNull`)
- Test implementation details (private methods)
- Use production database in tests
- Ignore test failures (they catch real issues)

## Related Files

- `/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/di/AppModule.kt`
- `/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/di/PlatformModule.android.kt`
- `/gradle/libs.versions.toml` (Koin version: 4.1.0)

## References

- [Koin Testing Docs](https://insert-koin.io/docs/reference/koin-test/testing)
- [Koin KMP Best Practices](https://insert-koin.io/docs/reference/koin-mp/kmp/)
- [Android Instrumented Tests](https://developer.android.com/training/testing/instrumented-tests)
