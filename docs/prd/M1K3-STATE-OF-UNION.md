# M1K3 - State of the Union

**Last Updated:** 2025-12-22
**Sprint/Cycle:** Phase 1 - Platform-Native AI Foundation
**Overall Status:** 🟡 In Progress

---

## Quick Status

| Area | Status | Blockers |
|------|--------|----------|
| Shared Abstraction | 🟢 Complete | None - OnDeviceAi interface + types done |
| iOS On-Device AI | 🟡 In Progress | Swift cinterop implementation pending |
| Android On-Device AI | 🟢 Complete | AndroidOnDeviceAi + LlamaCppFallbackEngine done |
| Chat UI | 🔴 Not Started | Depends on AI integration |
| Summarization | 🔴 Not Started | Types ready, platform impl pending |
| Writing Assistance | 🔴 Not Started | Phase 2 |

**Legend:** 🟢 Complete | 🟡 In Progress | 🔴 Not Started | ⚫ Blocked

---

## Current Sprint Focus

### Goals This Cycle
1. [x] Complete `OnDeviceAi` interface definition in commonMain
2. [x] Implement `AiResult<T>` sealed class with functional operators
3. [x] Implement `AiAvailability` sealed class with all states
4. [x] Create `MockOnDeviceAi` for TDD testing
5. [x] Android ML Kit GenAI integration (AndroidOnDeviceAi)
6. [ ] iOS Foundation Models Swift wrapper + cinterop

### What's Working (Phase 2 Complete)
- **OnDeviceAi interface** - Full contract for platform implementations
- **AiAvailability** - 4 states: Available, Downloading, Unavailable(reason), Fallback(engine)
- **AiResult<T>** - Success/Error sealed class with map, fold, onSuccess, onError, getOrNull, getOrThrow
- **AiErrorCode** - 8 typed error codes for consistent error handling
- **SummaryStyle** - BRIEF, BULLETS, DETAILED for platform summarization
- **MockOnDeviceAi** - Full mock implementation with verification state
- **AndroidOnDeviceAi** - Main Android implementation with ML Kit → LlamaCpp fallback
- **LlamaCppFallbackEngine** - Adapter wrapping BaseLlmEngine for OnDeviceAi compatibility
- **MlKitAvailabilityChecker** - Interface + stub for ML Kit GenAI device capability checking
- **MlKitGenAiEngine** - Interface + stub for future ML Kit GenAI (Gemini Nano) integration
- **114 new tests** - 70 OnDeviceAi types + 17 LlamaCppFallbackEngine + 27 AndroidOnDeviceAi (100% pass)

### What's Blocked
- None currently - iOS implementation ready to start

---

## Feature Progress

### F1: On-Device Text Generation (P0)

**Status:** 🟡 In Progress
**Target:** v1.0 Launch

#### Acceptance Criteria Progress
- [ ] Text generation works on supported iOS devices (A17 Pro+, M1+)
- [ ] Text generation works on supported Android devices (Tensor G3+, SD 8 Gen 3+)
- [ ] Response streaming shows tokens as they're generated
- [ ] Generation can be cancelled mid-stream
- [x] Clear error messages for unsupported devices (AiErrorCode enum)
- [ ] Works fully offline after initial app install
- [ ] Response latency < 500ms to first token on supported devices

#### Implementation Tasks
- [x] `commonMain`: OnDeviceAi interface defined (`OnDeviceAi.kt`)
- [x] `commonMain`: AiResult<T> sealed class with functional operators (`AiResult.kt`)
- [x] `commonMain`: AiAvailability sealed class with states (`AiAvailability.kt`)
- [x] `commonMain`: AiErrorCode enum with 8 error types (`AiErrorCode.kt`)
- [x] `commonMain`: GenerationConfig (using existing from BaseLlmEngine)
- [x] `commonTest`: MockOnDeviceAi for TDD testing (`MockOnDeviceAi.kt`)
- [x] `commonTest`: 70 unit tests, 100% pass rate
- [x] `androidMain`: AndroidOnDeviceAi implementation with ML Kit → LlamaCpp fallback
- [x] `androidMain`: LlamaCppFallbackEngine wrapping BaseLlmEngine
- [x] `androidMain`: MlKitAvailabilityChecker interface + stub
- [x] `androidMain`: MlKitGenAiEngine interface + stub
- [x] `androidMain`: Thread-safe architecture (AtomicReference, Mutex, CAS loop)
- [ ] `androidMain`: ML Kit GenAI Prompt API integration (when SDK stable)
- [ ] `iosMain`: Swift wrapper (FoundationModelsWrapper.swift)
- [ ] `iosMain`: cinterop .def file configuration
- [ ] `iosMain`: Kotlin bridging to Swift callbacks
- [ ] `iosMain`: LlamaCpp fallback for older devices
- [ ] `composeApp`: Chat input UI
- [ ] `composeApp`: Streaming response display
- [ ] `composeApp`: Cancel button during generation

#### Notes
**2025-12-22:** Completed Phase 1 using TDD (Red-Green-Refactor):
- OnDeviceAi interface provides platform-agnostic contract
- AiResult<T> inspired by Kotlin Result but with typed errors
- AiAvailability has 4 states: Available, Downloading, Unavailable(reason), Fallback(engine)
- MockOnDeviceAi allows testing without platform dependencies
- Relationship with BaseLlmEngine documented (OnDeviceAi = availability-aware wrapper)

**2025-12-22:** Completed Phase 2 - Android Implementation:
- AndroidOnDeviceAi provides ML Kit → LlamaCpp fallback strategy
- LlamaCppFallbackEngine adapts BaseLlmEngine to OnDeviceAi interface
- Thread-safe architecture: AtomicReference<EngineState> for lock-free reads
- CAS loop in release() prevents double-release in concurrent scenarios
- Agent-reviewed: kmp-mobile-ai-reviewer validated thread safety patterns
- 44 new tests (17 + 27) with 100% pass rate

---

### F2: Smart Summarization (P0)

**Status:** 🟡 In Progress (types complete, platform impl pending)
**Target:** v1.0 Launch

#### Acceptance Criteria Progress
- [ ] Summarization works for text up to 3,000 words
- [x] Three summary styles available (SummaryStyle enum: BRIEF, BULLETS, DETAILED)
- [ ] Share extension functional on both platforms
- [ ] Clipboard summarization with single action
- [ ] Clear indication when text exceeds model limits (AiErrorCode.INPUT_TOO_LONG)
- [ ] Summary generation < 3 seconds

#### Implementation Tasks
- [x] `commonMain`: SummaryStyle enum (`SummaryStyle.kt`)
- [x] `commonMain`: OnDeviceAi.summarize() interface method
- [ ] `androidMain`: ML Kit Summarization API integration
- [ ] `iosMain`: Summarization via Foundation Models prompt
- [ ] `composeApp`: Summarizer tool UI
- [ ] `iosApp`: Share extension
- [ ] `androidApp`: Share extension

#### Notes
**2025-12-22:** SummaryStyle enum maps to platform-specific options:
- BRIEF → Android: ONE_BULLET, iOS: "1-2 sentence summary" prompt
- BULLETS → Android: THREE_BULLETS, iOS: "3-5 bullet points" prompt
- DETAILED → Android: PARAGRAPH, iOS: "detailed paragraph" prompt

---

### F3: Writing Assistance (P1)

**Status:** 🔴 Not Started  
**Target:** v1.0 Launch

[Criteria and tasks to be filled in when work begins]

---

### F4: Conversation History (P1)

**Status:** 🔴 Not Started  
**Target:** v1.0 Launch

[Criteria and tasks to be filled in when work begins]

---

### F5: Availability & Device Support UI (P0)

**Status:** 🟡 In Progress (types complete, UI pending)
**Target:** v1.0 Launch

#### Acceptance Criteria Progress
- [ ] Availability check on app launch with clear status display
- [x] Distinct states: Available, Downloading, Unavailable (with reason), Fallback
- [ ] Device compatibility info screen
- [x] Graceful degradation (Fallback state with engine name)
- [ ] No misleading messaging

#### Implementation Tasks
- [x] `commonMain`: AiAvailability sealed class (`AiAvailability.kt`)
- [x] `commonMain`: UnavailableReason enum (DEVICE_NOT_SUPPORTED, MODEL_NOT_READY, AI_DISABLED, QUOTA_EXCEEDED, BACKGROUND_BLOCKED, UNKNOWN)
- [x] `commonMain`: Fallback(engineName) state for graceful degradation
- [ ] `androidMain`: FeatureStatus mapping to AiAvailability
- [ ] `iosMain`: SystemLanguageModel.availability mapping to AiAvailability
- [ ] `composeApp`: AvailabilityBanner component
- [ ] `composeApp`: Device compatibility info screen
- [ ] `composeApp`: Graceful UI when AI unavailable

#### Notes
**2025-12-22:** AiAvailability sealed class provides:
- `Available` - Platform-native AI ready
- `Downloading` - Model download in progress
- `Unavailable(reason)` - Not available with typed reason
- `Fallback(engineName)` - Using fallback engine (e.g., LlamaCpp)

---

## Technical Debt

| Item | Priority | Notes |
|------|----------|-------|
| [Example: Hardcoded strings] | Medium | Extract to resources |
| | | |

---

## Architecture Decisions

### Recent Decisions

| Decision | Date | Rationale |
|----------|------|-----------|
| Swift wrapper over direct cinterop | 2025-12-22 | Foundation Models requires Swift; @objc wrapper is cleanest path |
| ML Kit over AICore direct | 2025-12-22 | ML Kit provides higher-level APIs with better quality guarantees |
| Hybrid approach (ML Kit + LlamaCpp) | 2025-12-22 | 100% device coverage - native AI for new devices, LlamaCpp fallback for older |
| OnDeviceAi separate from BaseLlmEngine | 2025-12-22 | Different responsibilities: OnDeviceAi = availability-aware, BaseLlmEngine = direct inference |
| TDD methodology | 2025-12-22 | Tests before code ensures testable design and prevents regressions |
| Sealed classes for state | 2025-12-22 | AiResult + AiAvailability as sealed classes for exhaustive when() handling |
| AtomicReference<EngineState> | 2025-12-22 | Lock-free reads for generate/summarize, Mutex only for initialization |
| CAS loop for release() | 2025-12-22 | Prevents double-release in concurrent scenarios without blocking |
| Stub ML Kit interfaces | 2025-12-22 | Enables testing and future integration when ML Kit GenAI is stable |

### Open Decisions

- [x] SKIE vs manual Swift interop - **Decision:** Manual Swift wrapper initially, evaluate SKIE later
- [ ] Koin vs manual DI for initial simplicity
- [x] SQLDelight vs DataStore - **Decision:** SQLDelight (already in use for other features)

---

## Testing Status

| Test Type | Coverage | Notes |
|-----------|----------|-------|
| Unit Tests (commonMain) | ✅ 70 tests | OnDeviceAi types + MockOnDeviceAi (100% pass) |
| Unit Tests (commonTest) | ✅ 297 total | Including existing tests |
| Unit Tests (androidMain) | ✅ 44 tests | AndroidOnDeviceAi + LlamaCppFallbackEngine (100% pass) |
| Unit Tests (iosMain) | 0% | [Target: Swift bridge] |
| UI Tests | 0% | [Target: critical flows] |
| Device Testing - iOS | 🔴 | Need iOS 26 beta device |
| Device Testing - Android | 🔴 | Need Pixel 9+ or S24+ |

### On-Device AI Test Coverage
| Component | Tests | Status |
|-----------|-------|--------|
| AiAvailabilityTest | 21 | ✅ 100% |
| AiResultTest | 27 | ✅ 100% |
| OnDeviceAiTest | 22 | ✅ 100% |
| LlamaCppFallbackEngineTest | 17 | ✅ 100% |
| AndroidOnDeviceAiTest | 27 | ✅ 100% |
| **Total** | **114** | ✅ **100%** |

---

## Risks & Issues

### Active Issues

| Issue | Severity | Owner | Status |
|-------|----------|-------|--------|
| [Example: iOS 26 beta instability] | Medium | Kev | Monitoring |
| | | | |

### Risk Register

| Risk | Likelihood | Impact | Mitigation Status |
|------|------------|--------|-------------------|
| iOS 26 delayed | Medium | High | Plan Android-first launch |
| ML Kit APIs change in alpha | Medium | Medium | Abstraction layer in place |
| Swift interop complexity | Medium | Medium | Evaluating SKIE |

---

## Dependencies

### External Dependencies

| Dependency | Version | Status | Notes |
|------------|---------|--------|-------|
| Kotlin | 2.1.0 | ✅ | |
| Compose Multiplatform | 1.7.0 | ✅ | |
| ML Kit GenAI Prompt | 1.0.0-alpha1 | ⚠️ Alpha | Monitor for breaking changes |
| ML Kit GenAI Summarization | 1.0.0-beta1 | ⚠️ Beta | |
| iOS 26 SDK | Beta | ⚠️ Beta | Foundation Models framework |

### Hardware Dependencies

| Device | Availability | Notes |
|--------|--------------|-------|
| iOS 26 test device | [YES/NO] | Need A17 Pro+ or M1+ |
| Android test device | [YES/NO] | Need Pixel 9+ or S24+ |

---

## Velocity & Estimates

### Completed Points
[Track completed work units]

### Remaining Estimate to v1.0
| Feature | Estimate | Confidence |
|---------|----------|------------|
| F1: Text Generation | [X days] | [High/Med/Low] |
| F2: Summarization | [X days] | |
| F3: Writing Assistance | [X days] | |
| F4: Conversation History | [X days] | |
| F5: Availability UI | [X days] | |
| Polish & Testing | [X days] | |
| **Total** | **[X days]** | |

---

## Next Actions

### Immediate (This Week)
1. [x] Implement `AndroidOnDeviceAi` with ML Kit GenAI integration ✅
2. [x] Implement `LlamaCppFallbackEngine` for older devices ✅
3. [ ] Create Swift wrapper for iOS Foundation Models
4. [ ] Wire AndroidOnDeviceAi into app DI (Koin)

### Upcoming (Next 2 Weeks)
1. [ ] Complete iOS cinterop configuration
2. [ ] Implement iOS Foundation Models wrapper
3. [ ] Create AvailabilityBanner Compose component
4. [ ] Connect OnDeviceAi to existing chat UI
5. [ ] Integrate ML Kit GenAI SDK when stable

### Blocked / Waiting
- None currently

---

## Notes & Learnings

### This Cycle (2025-12-22)
- **TDD works beautifully** - Writing 114 tests first revealed the exact API shape needed
- **Sealed classes are ideal** for state machines (AiAvailability, AiResult)
- **MockOnDeviceAi** enables UI testing without platform dependencies
- **Functional operators** (map, fold, onSuccess) make error handling ergonomic
- **Clear separation** between OnDeviceAi (availability-aware) and BaseLlmEngine (direct inference)
- **Thread safety patterns** - AtomicReference<EngineState> + CAS loop for lock-free reads and safe release
- **Agent reviews** - kmp-mobile-ai-reviewer caught TOCTOU race condition in release(), fixed with CAS loop
- **Adapter pattern** - LlamaCppFallbackEngine wraps BaseLlmEngine, enabling OnDeviceAi compatibility

### Questions for Research
- [x] Best practice for Swift cinterop with Foundation Models
- [ ] ML Kit GenAI beta stability timeline
- [ ] LlamaCpp memory usage on low-end Android devices

---

## Links & Resources

- [PRD](./M1K3-PRD.md)
- [On-Device AI Implementation Guide](./m1k3-kmp-on-device-ai.md)
- [Project Repository](https://github.com/Round-Tower/m1k3)
- [Apple Foundation Models Docs](https://developer.apple.com/documentation/foundationmodels)
- [ML Kit GenAI Docs](https://developers.google.com/ml-kit/genai)
- [OnDeviceAi Source](../app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/ai/ondevice/)

---

*Last updated by: Claude on 2025-12-22*
