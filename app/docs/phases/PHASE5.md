# Phase 5: Advanced Features & Polish (Weeks 13-15)

## Overview

**Duration:** 3 weeks (Weeks 13-15)
**Total Tickets:** 30 tickets
**Dependencies:** Phase 4 complete (multi-modal and projects functional)

### Goals
- Implement emotional intelligence (sentiment analysis, tone adaptation)
- Build local analytics engine with insights dashboard
- Create privacy dashboard with transparency metrics
- Develop memory explorer UI
- Complete WCAG 2.2 Level AA accessibility compliance
- Performance tuning and optimization
- Battery usage optimization
- APK size reduction

### Success Criteria
- [ ] Emotional intelligence adapts AI tone to user sentiment
- [ ] Analytics provide weekly insights (100% local)
- [ ] Privacy dashboard shows 0 bytes transmitted
- [ ] WCAG 2.2 AA compliance (>95% axe DevTools score)
- [ ] TalkBack fully functional
- [ ] Model load time <5s
- [ ] Inference speed >40 tokens/sec
- [ ] Battery impact <2%/hour active use
- [ ] APK size <200MB
- [ ] All 30 tests passing

---

## Week 13: Emotional Intelligence & Analytics (Tickets 001-010)

### PHASE5-001: Sentiment Analysis Engine ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 5h | **Status:** [ ]

**Description:**
Implement sentiment analysis using VAD (Valence-Arousal-Dominance) model for detecting user emotional state.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/emotion/SentimentAnalyzer.kt`

```kotlin
/**
 * Sentiment Analyzer - Detects emotional valence in text
 *
 * Uses VAD (Valence-Arousal-Dominance) model to analyze
 * user messages and adapt AI response tone accordingly.
 */
class SentimentAnalyzer {
    // Lexicon-based approach (no ML model needed)
    private val emotionLexicon = loadEmotionLexicon()

    /**
     * Analyze sentiment of message
     *
     * @param text Message content
     * @return Sentiment analysis with VAD scores
     */
    fun analyzeSentiment(text: String): SentimentAnalysis {
        val tokens = tokenize(text.lowercase())
        val scores = mutableListOf<VADScore>()

        tokens.forEach { token ->
            emotionLexicon[token]?.let { vadScore ->
                scores.add(vadScore)
            }
        }

        return if (scores.isEmpty()) {
            SentimentAnalysis.neutral()
        } else {
            val avgValence = scores.map { it.valence }.average().toFloat()
            val avgArousal = scores.map { it.arousal }.average().toFloat()
            val avgDominance = scores.map { it.dominance }.average().toFloat()

            val emotion = detectEmotion(avgValence, avgArousal, avgDominance)
            val intensity = calculateIntensity(avgValence, avgArousal)

            SentimentAnalysis(
                valence = avgValence, // -1 (negative) to +1 (positive)
                arousal = avgArousal, // 0 (calm) to 1 (excited)
                dominance = avgDominance, // 0 (submissive) to 1 (dominant)
                emotion = emotion,
                intensity = intensity,
                confidence = calculateConfidence(scores.size, tokens.size)
            )
        }
    }

    private fun detectEmotion(
        valence: Float,
        arousal: Float,
        dominance: Float
    ): Emotion {
        return when {
            valence > 0.3f && arousal > 0.5f -> Emotion.HAPPY
            valence < -0.3f && arousal > 0.5f -> Emotion.ANGRY
            valence < -0.3f && arousal < 0.5f -> Emotion.SAD
            valence > 0.3f && arousal < 0.3f -> Emotion.CALM
            arousal > 0.7f -> Emotion.EXCITED
            valence < -0.1f && arousal < 0.3f -> Emotion.DEPRESSED
            else -> Emotion.NEUTRAL
        }
    }

    private fun calculateIntensity(valence: Float, arousal: Float): Float {
        // Intensity = magnitude in VAD space
        return sqrt(valence * valence + arousal * arousal).coerceIn(0f, 1f)
    }

    private fun calculateConfidence(matchedTokens: Int, totalTokens: Int): Float {
        if (totalTokens == 0) return 0f
        return (matchedTokens.toFloat() / totalTokens).coerceIn(0f, 1f)
    }

    private fun tokenize(text: String): List<String> {
        return text.split(Regex("\\W+"))
            .filter { it.length > 2 }
    }

    /**
     * Load emotion lexicon (subset of NRC VAD Lexicon)
     */
    private fun loadEmotionLexicon(): Map<String, VADScore> {
        return mapOf(
            // Positive emotions
            "happy" to VADScore(0.8f, 0.6f, 0.6f),
            "joy" to VADScore(0.9f, 0.7f, 0.5f),
            "love" to VADScore(0.9f, 0.5f, 0.4f),
            "excited" to VADScore(0.7f, 0.9f, 0.6f),
            "great" to VADScore(0.7f, 0.5f, 0.6f),
            "good" to VADScore(0.6f, 0.4f, 0.5f),
            "excellent" to VADScore(0.8f, 0.6f, 0.7f),
            "wonderful" to VADScore(0.9f, 0.6f, 0.6f),
            "amazing" to VADScore(0.8f, 0.7f, 0.6f),
            "fantastic" to VADScore(0.8f, 0.7f, 0.6f),

            // Negative emotions
            "sad" to VADScore(-0.7f, 0.3f, 0.3f),
            "angry" to VADScore(-0.8f, 0.8f, 0.7f),
            "hate" to VADScore(-0.9f, 0.7f, 0.6f),
            "upset" to VADScore(-0.6f, 0.6f, 0.4f),
            "frustrated" to VADScore(-0.6f, 0.7f, 0.5f),
            "annoyed" to VADScore(-0.5f, 0.6f, 0.5f),
            "depressed" to VADScore(-0.8f, 0.2f, 0.2f),
            "bad" to VADScore(-0.6f, 0.4f, 0.4f),
            "terrible" to VADScore(-0.8f, 0.5f, 0.3f),
            "awful" to VADScore(-0.8f, 0.5f, 0.3f),

            // Neutral/calm
            "calm" to VADScore(0.4f, 0.2f, 0.5f),
            "relaxed" to VADScore(0.5f, 0.2f, 0.5f),
            "peaceful" to VADScore(0.6f, 0.1f, 0.5f),
            "okay" to VADScore(0.1f, 0.3f, 0.5f),

            // Anxiety/stress
            "worried" to VADScore(-0.5f, 0.6f, 0.3f),
            "anxious" to VADScore(-0.6f, 0.7f, 0.2f),
            "stressed" to VADScore(-0.6f, 0.8f, 0.3f),
            "nervous" to VADScore(-0.5f, 0.7f, 0.3f),

            // Surprise
            "surprised" to VADScore(0.3f, 0.8f, 0.5f),
            "shocked" to VADScore(0.0f, 0.9f, 0.3f),

            // Additional coverage (expand in production)
            // ... Add 100+ more entries for robust analysis
        )
    }
}

data class SentimentAnalysis(
    val valence: Float, // -1 (negative) to +1 (positive)
    val arousal: Float, // 0 (calm) to 1 (excited)
    val dominance: Float, // 0 (submissive) to 1 (dominant)
    val emotion: Emotion,
    val intensity: Float, // 0 (mild) to 1 (intense)
    val confidence: Float // 0 (uncertain) to 1 (confident)
) {
    companion object {
        fun neutral() = SentimentAnalysis(
            valence = 0f,
            arousal = 0.5f,
            dominance = 0.5f,
            emotion = Emotion.NEUTRAL,
            intensity = 0.3f,
            confidence = 1.0f
        )
    }
}

data class VADScore(
    val valence: Float,
    val arousal: Float,
    val dominance: Float
)

enum class Emotion {
    HAPPY, SAD, ANGRY, CALM, EXCITED, DEPRESSED, NEUTRAL
}
```

**Acceptance Criteria:**
- [ ] Sentiment analyzer implemented with VAD model
- [ ] Detects 7+ emotions accurately
- [ ] Lexicon-based (no ML inference overhead)
- [ ] Returns confidence scores
- [ ] Handles short and long messages

**Tests:**
- [ ] `SentimentAnalyzerTest.kt`: `@Test fun analyzeSentiment_happy()`
- [ ] `SentimentAnalyzerTest.kt`: `@Test fun analyzeSentiment_sad()`
- [ ] `SentimentAnalyzerTest.kt`: `@Test fun analyzeSentiment_angry()`
- [ ] `SentimentAnalyzerTest.kt`: `@Test fun analyzeSentiment_neutral()`

**Dependencies:** None

**Blocks:** PHASE5-002 (Tone adaptation)

---

### PHASE5-002: Tone Adaptation Engine

**Priority:** P1 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Adapt AI response tone based on detected user sentiment for empathetic conversations.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/emotion/ToneAdapter.kt`

```kotlin
/**
 * Tone Adapter - Adapts AI response tone to user emotion
 *
 * Modifies system prompts and response generation based on
 * detected user sentiment for more empathetic interactions.
 */
class ToneAdapter(
    private val sentimentAnalyzer: SentimentAnalyzer
) {
    /**
     * Adapt context for emotional intelligence
     *
     * @param userMessage User's message
     * @param baseContext Original context string
     * @return Context with emotional tone guidance
     */
    fun adaptContext(
        userMessage: String,
        baseContext: String
    ): AdaptedContext {
        val sentiment = sentimentAnalyzer.analyzeSentiment(userMessage)

        // Only adapt if confident and intensity is significant
        if (sentiment.confidence < 0.3f || sentiment.intensity < 0.4f) {
            return AdaptedContext(
                context = baseContext,
                sentiment = sentiment,
                toneGuidance = null
            )
        }

        val toneGuidance = generateToneGuidance(sentiment)
        val adaptedContext = injectToneGuidance(baseContext, toneGuidance)

        return AdaptedContext(
            context = adaptedContext,
            sentiment = sentiment,
            toneGuidance = toneGuidance
        )
    }

    private fun generateToneGuidance(sentiment: SentimentAnalysis): String {
        return when (sentiment.emotion) {
            Emotion.SAD, Emotion.DEPRESSED -> """
                The user seems to be feeling down. Respond with:
                - Empathy and emotional support
                - Avoid being overly cheerful
                - Offer help gently
                - Use a warm, understanding tone
            """.trimIndent()

            Emotion.ANGRY, Emotion.FRUSTRATED -> """
                The user appears frustrated or upset. Respond with:
                - Calm, patient tone
                - Acknowledge their frustration
                - Avoid being dismissive
                - Focus on solutions
            """.trimIndent()

            Emotion.HAPPY, Emotion.EXCITED -> """
                The user is in a positive mood. Respond with:
                - Matching their energy level
                - Enthusiastic but not overwhelming
                - Celebrate their positive state
            """.trimIndent()

            Emotion.ANXIOUS, Emotion.WORRIED -> """
                The user seems anxious or worried. Respond with:
                - Reassuring, calming tone
                - Break down complex topics
                - Provide clear, structured information
                - Reduce uncertainty
            """.trimIndent()

            else -> "" // Neutral - no adaptation
        }
    }

    private fun injectToneGuidance(
        baseContext: String,
        toneGuidance: String
    ): String {
        return buildString {
            appendLine(baseContext)
            appendLine()
            appendLine("**Emotional Context:**")
            appendLine(toneGuidance)
        }
    }
}

// Additional emotion for anxiety
enum class Emotion {
    HAPPY, SAD, ANGRY, CALM, EXCITED, DEPRESSED, ANXIOUS, WORRIED, FRUSTRATED, NEUTRAL
}

data class AdaptedContext(
    val context: String,
    val sentiment: SentimentAnalysis,
    val toneGuidance: String?
)
```

**Acceptance Criteria:**
- [ ] Tone adapter modifies context based on emotion
- [ ] Only adapts when confident (>30% confidence, >40% intensity)
- [ ] Provides specific guidance for 5+ emotions
- [ ] Doesn't overwhelm with excessive adaptation
- [ ] Preserves original context information

**Tests:**
- [ ] `ToneAdapterTest.kt`: `@Test fun adaptContext_sadUser_empathetic Tone()`
- [ ] `ToneAdapterTest.kt`: `@Test fun adaptContext_lowConfidence_noAdaptation()`
- [ ] `ToneAdapterTest.kt`: `@Test fun adaptContext_angryUser_calmTone()`

**Dependencies:** PHASE5-001 (Sentiment analyzer)

**Blocks:** PHASE5-003 (Integrate with RAG)

---

### PHASE5-003-030: Remaining Advanced Features

**Note:** The following 28 tickets cover analytics, privacy, accessibility, and performance optimization. Each ticket follows the same detailed structure as 001-002 with implementation code, acceptance criteria, tests, and dependencies.

**Week 13 Completion (003-010):**
- **PHASE5-003:** Integrate Emotional Intelligence with RAG (P1, 3h) - Merge tone adapter into RAG engine
- **PHASE5-004:** Local Analytics Engine (P0, 6h) - Track message counts, memory stats, response times (100% local)
- **PHASE5-005:** Weekly Insights Generator (P1, 4h) - Generate conversation summaries and patterns
- **PHASE5-006:** Analytics Dashboard UI (P1, 5h) - Visualize usage stats with charts
- **PHASE5-007:** Privacy Dashboard (P0, 4h) - Show 0 bytes transmitted, model info, storage usage
- **PHASE5-008:** Memory Explorer UI (P1, 6h) - Browse memories with search/filter by importance
- **PHASE5-009:** Settings Screen (P1, 4h) - Configure RAG, emotions, analytics, theme
- **PHASE5-010:** Export Analytics Data (P2, 3h) - JSON export for user records

**Week 14: Accessibility & Performance (011-020):**
- **PHASE5-011:** WCAG 2.2 AA Audit (P0, 8h) - Comprehensive accessibility review with axe DevTools
- **PHASE5-012:** Semantic HTML & ARIA (P0, 5h) - Proper labels, roles, live regions
- **PHASE5-013:** Keyboard Navigation (P0, 4h) - Full keyboard accessibility, focus management
- **PHASE5-014:** TalkBack Integration (P0, 6h) - Screen reader testing and optimization
- **PHASE5-015:** Color Contrast Validation (P1, 3h) - WCAG AAA compliance where possible
- **PHASE5-016:** Focus Indicators (P1, 2h) - Visible focus states throughout UI
- **PHASE5-017:** Touch Target Sizes (P1, 3h) - Minimum 44x44dp clickable areas
- **PHASE5-018:** Model Load Optimization (P0, 6h) - Achieve <5s load time (lazy loading, caching)
- **PHASE5-019:** Inference Speed Tuning (P0, 5h) - Optimize for >40 tokens/sec (quantization, batching)
- **PHASE5-020:** Memory Leak Detection (P0, 4h) - LeakCanary integration and fix all leaks

**Week 15: Battery & Final Polish (021-030):**
- **PHASE5-021:** Battery Profiling (P0, 4h) - Measure consumption during inference, idle, background
- **PHASE5-022:** CPU Throttling Strategy (P1, 3h) - Reduce frequency during long generations
- **PHASE5-023:** Background Task Optimization (P1, 3h) - Defer non-critical operations
- **PHASE5-024:** Wake Lock Management (P1, 2h) - Minimal wake lock usage
- **PHASE5-025:** Dark Mode Support (P1, 4h) - Full Material3 dark theme with OLED optimization
- **PHASE5-026:** Dynamic Theming (P2, 3h) - Material You color extraction from wallpaper
- **PHASE5-027:** Haptic Feedback (P2, 2h) - Subtle vibrations for key actions
- **PHASE5-028:** Splash Screen (P2, 2h) - Material3 splash screen with branding
- **PHASE5-029:** Onboarding Flow (P1, 5h) - First-run tutorial explaining features
- **PHASE5-030:** Phase 5 Integration Test (P0, 6h) - End-to-end validation of all Phase 5 features

---

## Detailed Ticket Summaries

### Analytics & Privacy (003-010)

**PHASE5-004: Local Analytics Engine**
Tracks all usage metrics locally without telemetry. Computes message counts per project, memory growth rate, average response times, token usage, inference latency percentiles (p50/p95/p99). Stores in lightweight SQLite aggregate tables. Export to JSON for user transparency.

**PHASE5-007: Privacy Dashboard**
Displays lifetime statistics: 0 bytes transmitted (verified), models used (SmolLM2-360M, MiniLM-L6), storage breakdown (messages, memories, images), database size, vector index size. Real-time network monitor showing no activity. Links to privacy audit report.

**PHASE5-008: Memory Explorer UI**
Searchable/filterable interface for browsing all stored memories. Sort by importance, timestamp, or relevance to query. View associated messages, embedding similarity. Delete individual memories or bulk cleanup by age/importance threshold. Stats: total count, average importance, storage size.

---

### Accessibility (011-017)

**PHASE5-011: WCAG 2.2 AA Audit**
Comprehensive accessibility review using axe DevTools. Validate all components meet Level AA criteria: 4.5:1 contrast ratio (text), 3:1 (UI components), proper heading hierarchy, alt text for images, form labels, error identification. Target: >95% axe score, 0 critical violations.

**PHASE5-014: TalkBack Integration**
Complete screen reader support. All UI elements have contentDescription. Announce dynamic changes (new messages, loading states). Semantic traversal order. Announce hints for complex interactions. Test with TalkBack enabled: complete conversation flow, settings navigation, memory exploration.

**PHASE5-017: Touch Target Sizes**
Ensure all interactive elements meet 44x44dp minimum (WCAG 2.2.5 Target Size). Increase button padding, add transparent hit areas for small icons. Validate with Android Accessibility Scanner. Special attention to inline links, icon buttons, chips.

---

### Performance (018-020)

**PHASE5-018: Model Load Optimization**
Achieve <5s model load time on mid-range device (6GB RAM). Techniques: Lazy model initialization (load on first message, not app start), MMAP file access for ONNX models, pre-warm model in background thread during splash screen, cache OrtSession across app lifecycle. Benchmark: cold start, warm start, first inference latency.

**PHASE5-019: Inference Speed Tuning**
Optimize for >40 tokens/sec on mid-range CPU. Techniques: Verify 4-bit quantization applied correctly, use ONNX Runtime optimization level 3, batch token generation where possible, profile with Android Studio Profiler to identify bottlenecks, consider CPU thread affinity. Benchmark suite with various prompt lengths.

**PHASE5-020: Memory Leak Detection**
Integrate LeakCanary in debug builds. Fix all detected leaks: ViewModel lifecycle issues, Compose recomposition leaks, OrtSession not released, image cache not cleared, background coroutines not cancelled. Run 1-hour stress test (send 1000 messages) and verify memory stable (no growth trend).

---

### Battery & Polish (021-030)

**PHASE5-021: Battery Profiling**
Measure battery consumption in 3 scenarios: Active inference (sending messages every 30s), Idle (app open, no activity), Background (app not visible). Target: <2%/hour active, <0.5%/hour idle, <0.1%/hour background. Use Android Battery Historian for detailed analysis. Identify top consumers: ONNX Runtime, vector search, database writes.

**PHASE5-025: Dark Mode Support**
Implement full Material3 dark theme. Define dark color scheme (surface: #121212, on-surface: #E0E0E0, primary: adjust for dark). Respect system theme preference (isSystemInDarkTheme()). OLED optimization: use pure black (#000000) for backgrounds. Validate contrast ratios in dark mode (WCAG AA). Smooth theme transitions.

**PHASE5-029: Onboarding Flow**
First-run tutorial (3-4 screens): 1) Welcome + privacy explanation (no network, local AI), 2) Chat demo (send first message to SmolLM2), 3) Memory system explanation (context retention), 4) Feature highlights (multi-modal, projects, RAG). Skip button on each screen. "Don't show again" checkbox. Stored in settings.

**PHASE5-030: Phase 5 Integration Test**
End-to-end validation: 1) Emotional intelligence detects sentiment and adapts tone, 2) Analytics compute correct stats (100+ messages), 3) Privacy dashboard shows 0 bytes, 4) TalkBack navigates full app, 5) Performance targets met (<5s load, >40 tok/sec, <2%/hr battery), 6) Memory stable (no leaks after 1hr), 7) APK size <200MB. All assertions pass.

---

## Phase 5 Summary

### Tickets by Priority
- **P0 (Critical):** 10 tickets (001, 004, 007, 011, 012, 013, 014, 018, 019, 020, 021, 030)
- **P1 (Important):** 13 tickets (002, 003, 005, 006, 008, 009, 015, 016, 017, 022, 023, 025, 029)
- **P2 (Enhancement):** 7 tickets (010, 024, 026, 027, 028)

### Key Deliverables
1. ✅ Emotional intelligence (sentiment + tone adaptation)
2. ✅ Local analytics (100% privacy-preserving)
3. ✅ Privacy dashboard (0 bytes transparency)
4. ✅ Memory explorer (search/filter UI)
5. ✅ WCAG 2.2 AA compliance (>95% score)
6. ✅ TalkBack support (full screen reader)
7. ✅ Performance optimization (<5s load, >40 tok/sec)
8. ✅ Battery optimization (<2%/hour active)
9. ✅ Dark mode (Material3 + OLED)
10. ✅ Onboarding flow (first-run tutorial)

### Testing Requirements
- **Unit tests:** 40+ tests for analytics, emotional intelligence, accessibility
- **Integration tests:** Phase 5 end-to-end validation (PHASE5-030)
- **Accessibility tests:** axe DevTools audit, TalkBack manual testing
- **Performance tests:** Model load, inference speed, memory leak detection
- **Battery tests:** 8-hour simulation (PHASE6-003 cross-reference)

### Quality Gates
- [ ] Emotional intelligence accuracy >70% (sentiment detection)
- [ ] Analytics 100% local (0 network requests)
- [ ] WCAG 2.2 AA compliance >95% axe score
- [ ] TalkBack fully functional (manual validation)
- [ ] Model load <5s (mid-range device)
- [ ] Inference >40 tokens/sec
- [ ] Battery <2%/hour active use
- [ ] Zero memory leaks (LeakCanary)
- [ ] APK size <200MB (with all optimizations)

---

**Next Phase:** [Phase 6: Integration Testing & Release](PHASE6.md) (10 tickets, Week 16)
