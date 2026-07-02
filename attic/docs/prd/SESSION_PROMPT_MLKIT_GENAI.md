# Session Prompt: ML Kit GenAI Integration

**Date Created:** 2025-12-22
**Status:** ✅ IMPLEMENTATION COMPLETE - Ready for Device Testing
**Next Session Goal:** Test on Pixel 9 device to validate ML Kit GenAI works

---

## What's Been Implemented (2025-12-22)

### ✅ ML Kit GenAI Dependencies Added
- `com.google.mlkit:genai-prompt:1.0.0-alpha1` - Prompt API for text generation
- `com.google.mlkit:genai-summarization:1.0.0-beta1` - Summarization API (optional)

### ✅ RealMlKitAvailabilityChecker Implemented
Located in: `composeApp/src/androidMain/.../ai/ondevice/MlKitAvailabilityChecker.kt`
- Checks Android API level (requires 34+)
- Uses `Generation.getClient().checkStatus()` for feature availability
- Returns true for AVAILABLE, DOWNLOADABLE, or DOWNLOADING status
- Falls back to false on older devices or errors

### ✅ RealMlKitGenAiEngine Implemented
Located in: `composeApp/src/androidMain/.../ai/ondevice/MlKitGenAiEngine.kt`
- **checkAvailability()** - Maps FeatureStatus to AiAvailability
- **downloadModelIfNeeded()** - Triggers model download via `model.download().collect{}`
- **generate()** - Uses `model.generateContent(prompt)` suspend function
- **generateStream()** - Uses `model.generateContentStream(prompt)` Flow API
- **summarize()** - Uses prompt engineering (Summarization API has complex builder)
- **getModelInfo()** - Returns model name and status
- **release()** - Closes GenerativeModel

### ✅ Koin DI Wired Up
Located in: `composeApp/src/androidMain/.../di/PlatformModule.android.kt`
- `BaseLlmEngine` → `LlamaCppEngine`
- `MlKitAvailabilityChecker` → `RealMlKitAvailabilityChecker`
- `MlKitGenAiEngine` → `RealMlKitGenAiEngine`
- `OnDeviceAi` → `AndroidOnDeviceAi` (with ML Kit → LlamaCpp fallback)

### ✅ Settings Screen ML Kit Status UI
Located in: `composeApp/src/androidMain/.../ui/SettingsScreen.kt`
- **ML Kit GenAI section** in Settings with real-time status display
- Shows availability status: Available, Downloading, Unavailable (with reason), Fallback
- Shows Android version (requires 34+)
- **Test Generation button** - Sends "Hello, what is 2+2?" to AI and displays result
- Model info display

### ✅ Build Compiles Successfully
All OnDeviceAi tests pass (114 tests from previous phase still pass)

---

## How to Test on Device

### 1. Install on Pixel 9 Device
```bash
./gradlew :composeApp:installDebug
```

### 2. Open Settings Screen
Navigate to Settings tab in the app

### 3. Check ML Kit GenAI Section
You should see:
- **Status**: Will show one of:
  - "Available (Gemini Nano)" - ML Kit is ready
  - "Downloading model..." - Model is being downloaded
  - "Unavailable: [reason]" - ML Kit not available (falls back to LlamaCpp)
  - "Fallback: LlamaCpp" - Using fallback engine
- **Android version**: Should show "Android 34" or higher
- **Model Info**: Shows current engine details

### 4. Test Generation
Tap "Test Generation" button to send a test prompt and see the response.

---

## Testing Scenarios

| Scenario | Expected Result |
|----------|----------------|
| Pixel 9 Pro / Pro XL / Pro Fold | ML Kit Available, Gemini Nano generation |
| Pixel 10 series | ML Kit Available, Gemini Nano generation |
| **Pixel 9a** | ❌ **NOT SUPPORTED** - Falls back to LlamaCpp |
| Pixel 7/8 | Unavailable - Falls back to LlamaCpp |
| Pixel 6 | Unavailable - Falls back to LlamaCpp |
| Emulator | Unavailable - Falls back to LlamaCpp |
| Samsung Galaxy S25 | ML Kit Available |
| OnePlus 13 / Xiaomi 15 | ML Kit Available |

### ML Kit GenAI Supported Devices (as of Dec 2025)
Per [Android Developers Blog](https://android-developers.googleblog.com/2025/05/on-device-gen-ai-apis-ml-kit-gemini-nano.html):
- **Google:** Pixel 10 series, Pixel 9/9 Pro/9 Pro XL/9 Pro Fold (NOT 9a)
- **Samsung:** Galaxy S25 series
- **Others:** OnePlus 13, Xiaomi 15, Honor Magic 6/7, Motorola Razr 60 Ultra

---

## Context (Previous Phase)

We've completed Phase 2 of the On-Device AI implementation for 間 AI:

### What's Done (114 tests passing)
1. **OnDeviceAi interface** - Platform-agnostic abstraction in commonMain
2. **AiResult<T>** - Sealed class with functional operators (map, fold, onSuccess, onError)
3. **AiAvailability** - 4 states: Available, Downloading, Unavailable(reason), Fallback(engine)
4. **AiErrorCode** - 8 typed error codes
5. **SummaryStyle** - BRIEF, BULLETS, DETAILED
6. **MockOnDeviceAi** - Full mock for testing (70 tests)
7. **LlamaCppFallbackEngine** - Adapter wrapping BaseLlmEngine (17 tests)
8. **AndroidOnDeviceAi** - Main Android impl with ML Kit → LlamaCpp fallback (27 tests)
9. **Thread-safe architecture** - AtomicReference<EngineState>, CAS loop for release()

---

## Key Files

| File | Purpose |
|------|---------|
| `AndroidOnDeviceAi.kt` | Main orchestrator (ML Kit → fallback) |
| `MlKitAvailabilityChecker.kt` | Device capability checking (Real + Default) |
| `MlKitGenAiEngine.kt` | Gemini Nano wrapper (Real + Stub) |
| `LlamaCppFallbackEngine.kt` | Fallback for older devices |
| `OnDeviceAi.kt` | Shared interface (commonMain) |
| `SettingsScreen.kt` | UI for testing ML Kit status |
| `PlatformModule.android.kt` | Koin DI wiring |

---

## Architecture: Current vs Future

### Current Architecture (ChatScreen)
```
ChatScreen → BaseLlmEngine (LlamaCppEngine) → SmolLM2-135M GGUF
```
ChatScreen directly uses `BaseLlmEngine` for AI generation.

### New Architecture (OnDeviceAi)
```
Settings/Future → OnDeviceAi → AndroidOnDeviceAi
                              ↓
                   ML Kit GenAI (Gemini Nano)
                        OR
                   LlamaCppFallbackEngine (SmolLM2)
```

### Future Migration Path
1. Replace `BaseLlmEngine` usage in ChatScreen with `OnDeviceAi`
2. ChatScreen will automatically benefit from ML Kit when available
3. Fallback to LlamaCpp on unsupported devices

---

## Success Criteria

- [x] ML Kit GenAI dependency added and compiles
- [x] RealMlKitAvailabilityChecker implementation complete
- [x] RealMlKitGenAiEngine implementation complete
- [x] Koin DI wired up with all components
- [x] Existing tests pass (114 OnDeviceAi tests)
- [x] Settings UI for testing ML Kit status
- [ ] **PENDING: Device testing** - Validate on Pixel 9 device
- [ ] **PENDING: ML Kit download** - Test model download flow
- [ ] **PENDING: Generation quality** - Compare ML Kit vs LlamaCpp output

---

## Reference Documentation

- [ML Kit GenAI Overview](https://developers.google.com/ml-kit/genai)
- [Prompt API Guide](https://developers.google.com/ml-kit/genai/prompt)
- [Summarization API Guide](https://developers.google.com/ml-kit/genai/summarization)
- [Device Requirements](https://developers.google.com/ml-kit/genai/device-requirements)

---

**Ready for device testing!**
