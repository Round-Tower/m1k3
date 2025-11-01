# 間 AI Project Management - Master Overview

## Executive Summary

**Project:** 間 AI - Privacy-First On-Device AI Companion
**Timeline:** 16 weeks (Phases 0-6)
**Total Tickets:** 135 tickets across 6 phases
**Model:** SmolLM2-360M (180MB quantized) + MiniLM-L6 (90MB)
**Target:** Android API 27+ (mid-range devices, 6GB RAM)
**Philosophy:** 間 (ma) - negative space, wabi-sabi, computational sufficiency

---

## Project Structure

This project is managed through phase-specific documentation:

- **[Phase 0: Foundation & Infrastructure](docs/phases/PHASE0.md)** (15 tickets, Weeks 1-2)
- **[Phase 1: Core AI Engine](docs/phases/PHASE1.md)** (20 tickets, Weeks 3-5)
- **[Phase 2: Memory & Embedding System](docs/phases/PHASE2.md)** (25 tickets, Weeks 6-8)
- **[Phase 3: Knowledge Systems](docs/phases/PHASE3.md)** (15 tickets, Weeks 9-10)
- **[Phase 4: Multi-Modal & Projects](docs/phases/PHASE4.md)** (20 tickets, Weeks 11-12)
- **[Phase 5: Advanced Features & Polish](docs/phases/PHASE5.md)** (30 tickets, Weeks 13-15)
- **[Phase 6: Integration Testing & Release](docs/phases/PHASE6.md)** (10 tickets, Week 16)

---

## Progress Tracking

### Overall Status

**Current Phase:** Phase 0 - Foundation & Infrastructure
**Overall Progress:** 0/135 tickets (0%)
**Started:** [TBD]
**Target Completion:** [TBD + 16 weeks]

### Phase Completion

| Phase | Name | Duration | Tickets | Status | Complete |
|-------|------|----------|---------|--------|----------|
| **0** | **Foundation & Infrastructure** | Weeks 1-2 | 15 | 🔴 Not Started | 0/15 (0%) |
| 1 | Core AI Engine | Weeks 3-5 | 20 | ⚪ Pending | 0/20 (0%) |
| 2 | Memory & Embedding System | Weeks 6-8 | 25 | ⚪ Pending | 0/25 (0%) |
| 3 | Knowledge Systems | Weeks 9-10 | 15 | ⚪ Pending | 0/15 (0%) |
| 4 | Multi-Modal & Projects | Weeks 11-12 | 20 | ⚪ Pending | 0/20 (0%) |
| 5 | Advanced Features & Polish | Weeks 13-15 | 30 | ⚪ Pending | 0/30 (0%) |
| 6 | Integration Testing & Release | Week 16 | 10 | ⚪ Pending | 0/10 (0%) |

**Legend:** 🔴 In Progress | 🟢 Complete | ⚪ Not Started | 🟡 Blocked

---

## Quick Navigation

### By Priority
- **P0 (Critical Path):** 45 tickets - Must complete for MVP
- **P1 (Important):** 60 tickets - Core features
- **P2 (Enhancement):** 30 tickets - Polish and optimization

### By Type
- **Infrastructure:** Phase 0 (15 tickets)
- **AI/ML:** Phases 1-2 (45 tickets)
- **Features:** Phases 3-4 (35 tickets)
- **Quality/Polish:** Phase 5 (30 tickets)
- **Release:** Phase 6 (10 tickets)

### Key Milestones
- **Week 2:** Database ready, privacy enforced, knowledge base imported
- **Week 5:** SmolLM2-360M running, basic chat working
- **Week 8:** Memory system functional, context-aware conversations
- **Week 10:** Trivia engine live, device intelligence integrated
- **Week 12:** Multi-modal support, project management complete
- **Week 15:** Emotional intelligence, analytics, accessibility validated
- **Week 16:** Beta release ready, all tests passing

---

## Architecture Overview

### Core Components

```
間 AI Architecture
├── Data Layer (Phase 0)
│   ├── SQLDelight (4 tables)
│   ├── Vector Database (HNSW)
│   └── Knowledge Base (M1K3 1.6MB JSON)
│
├── AI Engine (Phases 1-2)
│   ├── SmolLM2-360M (ONNX)
│   ├── MiniLM-L6 Embedder
│   └── Memory Manager
│
├── Knowledge Systems (Phase 3)
│   ├── Trivia Engine (1,341+ facts)
│   ├── Device Intelligence
│   └── RAG Integration
│
├── Features (Phases 4-5)
│   ├── Multi-Modal (Text + Images)
│   ├── Project Management
│   ├── Emotional Intelligence
│   └── Local Analytics
│
└── Quality (Phases 5-6)
    ├── Accessibility (WCAG 2.2 AA)
    ├── Performance Optimization
    └── Integration Testing
```

### Technology Stack

**Platform:**
- Kotlin Multiplatform 2.2.20
- Compose Multiplatform 1.9.1
- Gradle 8.14.3

**AI/ML:**
- ONNX Runtime 1.17.0 (Android)
- SmolLM2-360M (180MB, 4-bit quantized)
- MiniLM-L6 (90MB, 8-bit quantized)
- JVector (HNSW index)

**Data:**
- SQLDelight 2.0.0
- SQLCipher (encryption)
- DuckDB principles (from M1K3)

**UI:**
- Jetpack Compose
- Material3 Design
- CameraX (multi-modal)
- ML Kit (vision)

**Testing:**
- JUnit5
- Mockk
- Turbine (Flow testing)
- Compose Test
- LeakCanary

---

## Development Workflow

### Test-Driven Development (TDD)

**Red-Green-Refactor Cycle:**

1. **Red:** Write failing test first
   ```kotlin
   @Test
   fun `memory importance scoring - questions weighted higher`() {
       // Arrange: Create test data
       // Act: Run importance calculator
       // Assert: Verify expected score (FAILS initially)
   }
   ```

2. **Green:** Implement minimum code to pass
   ```kotlin
   fun calculateImportance(content: String): Float {
       var score = 0.5f
       if (content.contains("?")) score += 0.15f
       return score
   }
   ```

3. **Refactor:** Clean up and optimize
   ```kotlin
   fun calculateImportance(content: String, message: Message): Float {
       return ImportanceHeuristics(content, message)
           .apply(QuestionDetector)
           .apply(LengthAnalyzer)
           .calculate()
   }
   ```

### Testing Pyramid

```
        /\
       /  \  E2E Tests (10%)
      /----\  - User flows
     /      \  - Integration
    /--------\  UI Tests (20%)
   /          \  - Compose tests
  /------------\  - Screenshot tests
 /--------------\ Integration Tests (30%)
/________________\ Unit Tests (40%)
                   - Business logic
                   - Data models
```

### Coverage Requirements

| Layer | Minimum Coverage | Target Coverage |
|-------|-----------------|-----------------|
| **Domain Logic** | 80% | 90%+ |
| **Data Layer** | 70% | 80%+ |
| **UI Components** | 60% | 70%+ |
| **Integration** | 50% | 60%+ |
| **Overall** | 70% | 80%+ |

---

## Success Criteria

### MVP Functionality Requirements

- [ ] **Privacy:** Zero network requests (Android Studio profiler validated)
- [ ] **AI Core:** SmolLM2-360M generates coherent responses (>80% quality score)
- [ ] **Memory:** Retrieves relevant context (>70% precision @ k=10)
- [ ] **Knowledge:** RAG integration with M1K3's 1,341+ documents
- [ ] **Multi-Modal:** Text + image support via ML Kit
- [ ] **Projects:** Project management with scoped conversations
- [ ] **Emotional:** Sentiment analysis calibrates responses
- [ ] **Analytics:** Local insights with weekly summaries
- [ ] **APK Size:** <200MB total

### Performance Benchmarks (Mid-Range: 6GB RAM)

| Metric | Target | Validation |
|--------|--------|------------|
| **Model Load Time** | <5 seconds | Stopwatch measurement |
| **Inference Speed** | 40+ tokens/sec | Benchmark test |
| **Memory Retrieval** | <100ms @ 10K | Performance test |
| **Battery Impact** | <2%/hour active | Battery profiler |
| **APK Size** | <200MB | Build output |
| **Startup Time** | <3 seconds | Cold start measurement |
| **UI Frame Time** | <16ms (60fps) | GPU profiler |

### Quality Gates

- [ ] **Tests:** 135+ tests passing (unit + integration + UI + performance)
- [ ] **Coverage:** >70% overall code coverage
- [ ] **Accessibility:** WCAG 2.2 Level AA (95%+ axe DevTools score)
- [ ] **TalkBack:** Fully functional screen reader support
- [ ] **Memory:** Zero memory leaks (LeakCanary validation)
- [ ] **Privacy:** No network activity (manual code review + profiler)
- [ ] **Documentation:** All public APIs documented
- [ ] **Performance:** All benchmarks meet targets

---

## Risk Management

### Critical Risks (P0)

| Risk | Impact | Probability | Mitigation | Status |
|------|--------|-------------|------------|--------|
| **APK size exceeds 200MB** | Blocks release | Medium | 4-bit quantization, GZIP compression, optional downloads | ⚠️ Monitor |
| **Mid-range performance insufficient** | Poor UX | Medium | Model caching, lazy loading, background inference | ⚠️ Monitor |
| **HNSW index build slow (>5s)** | Bad startup | Low | Incremental updates, background thread, optimize M | ⚠️ Monitor |
| **Battery drain high (>2%/hr)** | User complaints | Medium | Profile early, optimize inference, use NPU | ⚠️ Monitor |
| **Privacy breach (network call)** | Fatal to vision | Low | Remove permission, ProGuard, manual review | ✅ Mitigated |

### Medium Risks (P1)

| Risk | Impact | Probability | Mitigation | Status |
|------|--------|-------------|------------|--------|
| **RAG integration complex** | Feature delay | High | Start simple (keyword), iterate to semantic | ⚠️ Monitor |
| **TDD slows velocity** | Timeline slip | Medium | Front-load infrastructure, gain speed later | ⚠️ Accept |
| **Accessibility gaps** | App Store rejection | Low | Early audit, axe DevTools, TalkBack testing | ⚠️ Monitor |
| **Model quality insufficient** | Poor conversations | Low | Benchmark early, fallback to Gemma 3 if needed | ⚠️ Monitor |

---

## Integration with M1K3 Ecosystem

### Shared Resources

**Knowledge Base:**
- **M1K3:** `knowledge/comprehensive_knowledge_base.json` (1.6MB, 37,350 lines)
- **間 AI:** Port to SQLDelight at build time
- **Categories:** 20 categories, 1,341+ documents
- **Usage:** Trivia engine, RAG, educational content

**Database Schema:**
- **M1K3:** DuckDB with VSS extension
- **間 AI:** SQLDelight with HNSW (similar structure)
- **Vector:** 384-dimensional embeddings (compatible)
- **Tables:** Projects, Messages, MemoryMetadata, Settings

**Embedding Model:**
- **Both:** MiniLM-L6 (all-MiniLM-L6-v2)
- **M1K3:** SentenceTransformers (Python)
- **間 AI:** ONNX Runtime (Kotlin/Android)
- **Dimensions:** 384 (fully compatible)

### Divergent Components

| Component | M1K3 (Python) | 間 AI (Kotlin) | Reason |
|-----------|---------------|----------------|---------|
| **AI Model** | TinyLlama-1.1B-Chat | SmolLM2-360M | Mobile size constraint |
| **TTS** | KittenTTS, VibeVoice | Android TTS API | Platform optimization |
| **STT** | Vosk, Whisper, macOS | Android SpeechRecognizer | Platform native |
| **UI** | CLI, TUI, Web | Compose Multiplatform | Touch interface |
| **Vector DB** | DuckDB VSS | JVector HNSW | Mobile-optimized |

---

## Development Guidelines

### Code Style

**Kotlin:**
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use ktlint for formatting
- Max line length: 120 characters
- Use meaningful variable names (avoid abbreviations)

**Architecture:**
- Clean Architecture (Domain → Data → UI)
- Dependency inversion (interfaces, not implementations)
- Single Responsibility Principle
- Test-friendly design (inject dependencies)

**Git Workflow:**
- Branch naming: `phase0/PHASE0-001-remove-internet-permission`
- Commit format: `[PHASE0-001] Remove internet permission from manifest`
- PR template: Include ticket ID, description, tests, screenshots
- Squash commits before merging

### File Organization

```
app/
├── composeApp/src/
│   ├── commonMain/kotlin/
│   │   ├── ui/              # UI layer (Composables)
│   │   ├── viewmodel/       # ViewModels (state management)
│   │   └── theme/           # Design system
│   ├── androidMain/kotlin/
│   │   ├── ai/              # Android AI engine
│   │   ├── device/          # Device intelligence
│   │   └── MainActivity.kt
│   └── commonTest/kotlin/   # Shared tests
│
├── shared/src/
│   ├── commonMain/kotlin/
│   │   ├── domain/          # Business logic
│   │   ├── data/            # Repositories, data sources
│   │   └── model/           # Data models
│   ├── androidMain/kotlin/  # Platform implementations
│   └── commonTest/kotlin/   # Shared tests
│
└── docs/
    ├── phases/              # Phase-specific tickets
    ├── architecture/        # Architecture decision records
    └── api/                 # API documentation
```

---

## Weekly Milestones

### Week 1-2: Phase 0
**Goal:** Foundation ready, privacy enforced, database operational
**Demo:** Show empty app with SQLDelight database, verify 0 network requests

### Week 3-5: Phase 1
**Goal:** SmolLM2-360M running, basic chat working
**Demo:** Have conversation with AI, show streaming responses

### Week 6-8: Phase 2
**Goal:** Memory system functional, context-aware conversations
**Demo:** Ask about previous conversation, AI remembers context

### Week 9-10: Phase 3
**Goal:** Knowledge integrated, trivia/device facts in responses
**Demo:** AI shares interesting facts contextually, knows device specs

### Week 11-12: Phase 4
**Goal:** Multi-modal working, projects organized
**Demo:** Take photo, AI describes it; switch between projects

### Week 13-15: Phase 5
**Goal:** Emotional intelligence, analytics, accessibility complete
**Demo:** AI adapts tone to emotion, show weekly insights, TalkBack demo

### Week 16: Phase 6
**Goal:** Beta release ready, all tests passing
**Demo:** Full app walkthrough, performance metrics, privacy dashboard

---

## Resources

### Documentation
- [Architecture Guide](ARCHITECTURE.md) - KMP best practices
- [AI Architecture](AI_ARCHITECTURE.md) - System design details
- [Model Selection](OPUS.md) - SmolLM2 rationale

### External References
- [SmolLM2 Paper](https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct)
- [ONNX Runtime Android](https://onnxruntime.ai/docs/tutorials/mobile/android.html)
- [SQLDelight Documentation](https://cashapp.github.io/sqldelight/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [WCAG 2.2 Guidelines](https://www.w3.org/WAI/WCAG22/quickref/)

### Tools
- **IDE:** Android Studio Hedgehog (2023.1.1+)
- **Build:** Gradle 8.14.3, Kotlin 2.2.20
- **Testing:** JUnit5, Mockk, Turbine, Compose Test
- **Profiling:** Android Studio Profiler, LeakCanary
- **Accessibility:** axe DevTools, Accessibility Scanner
- **CI/CD:** GitHub Actions

---

## Contact & Support

**Project Lead:** [Your Name]
**Repository:** https://github.com/Round-Tower/m1k3
**Issues:** Use GitHub Issues with phase label (e.g., `phase:0`)
**Discussions:** GitHub Discussions for questions

---

## Changelog

| Date | Phase | Milestone | Notes |
|------|-------|-----------|-------|
| [TBD] | Setup | Project kickoff | Documentation created, ready to start |

---

**Last Updated:** 2025-01-XX
**Version:** 0.0.1
**Status:** Planning Phase
