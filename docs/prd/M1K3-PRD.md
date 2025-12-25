# M1K3 (Mike) - Product Requirements Document

**Version:** 1.0  
**Author:** Kev Murphy, Round Tower Software Studios Limited  
**Last Updated:** December 2024  
**Status:** Active Development

---

## Executive Summary

M1K3 (pronounced "Mike") is a privacy-focused edge AI assistant that runs entirely on-device, leveraging Apple's Foundation Models framework on iOS and Google's ML Kit GenAI APIs on Android. Unlike cloud-dependent assistants that harvest user data, M1K3 processes everything locally—your thoughts, queries, and conversations never leave your device.

Built with Kotlin Multiplatform and Compose Multiplatform, M1K3 embodies a philosophy of technology that serves users without extracting value from them.

---

## Product Vision

### The Problem

Current AI assistants require constant cloud connectivity, creating three fundamental issues:

1. **Privacy Erosion**: Every query is logged, analyzed, and often used for training or advertising
2. **Connectivity Dependence**: No internet means no AI assistance
3. **Latency & Cost**: Round-trips to servers add delay; API costs get passed to users via subscriptions

### The Solution

M1K3 brings genuinely intelligent assistance to your device with:

- **Complete Privacy**: Zero data transmission—inference happens on your silicon
- **Offline-First**: Full functionality without network connectivity
- **Zero Inference Cost**: No API fees, no subscriptions for core AI features
- **Transparent Operation**: Users understand exactly what the AI can and cannot do

### Design Philosophy

M1K3 follows these core principles:

| Principle | Implementation |
|-----------|----------------|
| **Human, Not Product** | Named "Mike" not "AI Assistant Pro™" |
| **Privacy by Architecture** | On-device only; no cloud fallback for personal data |
| **Accessible by Default** | Built with neurodiversity in mind (dyslexia-friendly, clear UI) |
| **Honest Limitations** | Clear about what on-device models can/cannot do |
| **No Dark Patterns** | No upsells, no data harvesting, no engagement manipulation |

---

## Target Users

### Primary Persona: Privacy-Conscious Professional

- Values data sovereignty
- Uses AI for productivity but uncomfortable with cloud services seeing their work
- Willing to accept capability tradeoffs for privacy guarantees
- Often in regulated industries (healthcare, legal, finance) or handling sensitive information

### Secondary Persona: Offline-First User

- Frequently in low/no connectivity environments (travel, rural, secure facilities)
- Needs reliable AI assistance regardless of network status
- Values consistency of experience

### Tertiary Persona: Tech-Savvy Parent

- Wants AI assistance for family without exposing children's data
- Concerned about AI interaction history being stored/profiled
- Values educational assistance that doesn't require account creation

---

## Core Features (v1.0 - Launch)

### F1: On-Device Text Generation

**Priority:** P0 (Launch Critical)  
**Status:** In Development

**Description:**  
Natural language text generation powered entirely by on-device foundation models. Users can ask questions, request help with writing, brainstorm ideas, and get intelligent responses without any data leaving their device.

**User Stories:**
- As a user, I can ask Mike a question and receive a helpful response generated on my device
- As a user, I can see my query being processed locally with a clear indicator
- As a user, I can interrupt/cancel generation at any time
- As a user, I receive clear feedback if the AI cannot help with my request (safety filters, capability limits)

**Acceptance Criteria:**
- [ ] Text generation works on supported iOS devices (A17 Pro+, M1+)
- [ ] Text generation works on supported Android devices (Tensor G3+, SD 8 Gen 3+)
- [ ] Response streaming shows tokens as they're generated
- [ ] Generation can be cancelled mid-stream
- [ ] Clear error messages for unsupported devices
- [ ] Works fully offline after initial app install
- [ ] Response latency < 500ms to first token on supported devices

**Technical Requirements:**
- iOS: Foundation Models framework, iOS 26+
- Android: ML Kit GenAI Prompt API (1.0.0-alpha1+), API 26+
- Shared: Kotlin Multiplatform expect/actual pattern
- UI: Compose Multiplatform with streaming text display

---

### F2: Smart Summarization

**Priority:** P0 (Launch Critical)  
**Status:** In Development

**Description:**  
Quickly summarize articles, documents, notes, or any text content. Leverages platform-optimized summarization (ML Kit's fine-tuned Summarization API on Android, prompted summarization on iOS).

**User Stories:**
- As a user, I can paste or share text to Mike and get a concise summary
- As a user, I can choose summary style (brief, bullet points, detailed)
- As a user, I can summarize content from other apps via share sheet
- As a user, I can summarize clipboard content with one tap

**Acceptance Criteria:**
- [ ] Summarization works for text up to 3,000 words (~4K tokens)
- [ ] Three summary styles available: Brief (1-2 sentences), Bullets (3-5 points), Detailed
- [ ] Share extension functional on both platforms
- [ ] Clipboard summarization with single action
- [ ] Clear indication when text exceeds model limits
- [ ] Summary generation < 3 seconds for typical article length

**Technical Requirements:**
- Android: ML Kit Summarization API (beta) with InputType/OutputType configuration
- iOS: Foundation Models with summarization system prompt
- Share Extension: Platform-specific implementations calling shared KMP logic

---

### F3: Writing Assistance

**Priority:** P1 (Launch Important)  
**Status:** Planned

**Description:**  
Help users improve their writing with proofreading, tone adjustment, and rewriting capabilities. Essential for accessibility users who benefit from writing support.

**User Stories:**
- As a user, I can check my text for grammar and spelling errors
- As a user, I can adjust the tone of my writing (formal, casual, friendly, professional)
- As a user, I can get suggestions to make my writing clearer
- As a dyslexic user, I can get help identifying and fixing common error patterns

**Acceptance Criteria:**
- [ ] Proofreading identifies grammar, spelling, and punctuation issues
- [ ] Tone adjustment offers at least 4 presets (formal, casual, friendly, professional)
- [ ] Rewriting preserves core meaning while improving clarity
- [ ] Works on text up to 256 tokens (ML Kit limit) with graceful chunking for longer text
- [ ] Dyslexia-friendly mode highlights common substitution patterns

**Technical Requirements:**
- Android: ML Kit Proofreading API + Rewriting API (beta)
- iOS: Foundation Models with writing assistance prompts
- Accessibility: High-contrast highlighting, dyslexia-friendly fonts option

---

### F4: Conversation History (Local Only)

**Priority:** P1 (Launch Important)  
**Status:** Planned

**Description:**  
Maintain conversation context within a session and optionally persist conversations locally for future reference. All data stored on-device only.

**User Stories:**
- As a user, my conversation context is maintained during a session
- As a user, I can optionally save conversations for later reference
- As a user, I can delete any or all conversation history
- As a user, I can export my conversations in standard formats
- As a user, I'm confident my conversations never leave my device

**Acceptance Criteria:**
- [ ] Multi-turn conversation within token limits (~4K combined)
- [ ] Optional local persistence using platform-secure storage
- [ ] Delete individual conversations or clear all
- [ ] Export to Markdown/JSON
- [ ] No analytics or telemetry on conversation content
- [ ] Automatic context management when approaching token limits

**Technical Requirements:**
- Storage: SQLDelight for cross-platform local database
- Security: iOS Keychain / Android EncryptedSharedPreferences for sensitive data
- Context Window: Intelligent truncation/summarization of old messages

---

### F5: Availability & Device Support UI

**Priority:** P0 (Launch Critical)  
**Status:** In Development

**Description:**  
Clear, honest communication about device compatibility and AI availability. Users should never be confused about why features aren't working.

**User Stories:**
- As a user on an unsupported device, I immediately understand what's not available and why
- As a user, I can see when the AI model is downloading/initializing
- As a user, I understand the difference between "not supported" and "temporarily unavailable"
- As a user, I can check detailed compatibility information

**Acceptance Criteria:**
- [ ] Availability check on app launch with clear status display
- [ ] Distinct states: Available, Downloading, Unavailable (with reason)
- [ ] Device compatibility info screen with supported device list
- [ ] Graceful degradation—app remains useful even without AI features
- [ ] No misleading "coming soon" messaging for unsupported hardware

**Technical Requirements:**
- iOS: SystemLanguageModel.default.availability checks
- Android: FeatureStatus checks via ML Kit
- UI: Non-blocking availability check with loading state

---

## Technical Architecture

### Platform Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose Multiplatform UI                  │
│              (Material 3, Adaptive Layouts)                  │
├─────────────────────────────────────────────────────────────┤
│                      Shared Business Logic                   │
│                         (commonMain)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  OnDeviceAI │  │ Conversation│  │  Settings/Prefs     │  │
│  │  Interface  │  │  Repository │  │  Repository         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│         expect/actual Platform Implementations               │
├──────────────────────────┬──────────────────────────────────┤
│       androidMain        │            iosMain               │
│  ┌────────────────────┐  │  ┌────────────────────────────┐  │
│  │   ML Kit GenAI     │  │  │  Foundation Models         │  │
│  │   - Prompt API     │  │  │  (via Swift cinterop)      │  │
│  │   - Summarization  │  │  │                            │  │
│  │   - Proofreading   │  │  │                            │  │
│  │   - Rewriting      │  │  │                            │  │
│  └────────────────────┘  │  └────────────────────────────┘  │
├──────────────────────────┼──────────────────────────────────┤
│      Android Runtime     │         iOS Runtime              │
│  ┌────────────────────┐  │  ┌────────────────────────────┐  │
│  │   AICore Service   │  │  │  Apple Intelligence        │  │
│  │   Gemini Nano      │  │  │  ~3B On-Device Model       │  │
│  └────────────────────┘  │  └────────────────────────────┘  │
└──────────────────────────┴──────────────────────────────────┘
```

### Key Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Cross-Platform Framework** | Kotlin Multiplatform + Compose | Maximum code sharing, native performance, modern UI toolkit |
| **iOS AI Integration** | Swift wrapper + cinterop | Foundation Models is Swift-only; @objc wrapper enables Kotlin access |
| **Android AI Integration** | Direct ML Kit APIs | Native Kotlin support, no bridging needed |
| **Local Storage** | SQLDelight | Cross-platform SQLite with type-safe Kotlin APIs |
| **Async Handling** | Kotlin Coroutines + Flow | Unified async model, streaming support, cancellation |
| **DI Framework** | Koin | Lightweight, KMP-native, simple setup |

### Supported Devices

**iOS (requires iOS 26+):**
- iPhone 15 Pro, 15 Pro Max (A17 Pro)
- iPhone 16, 16 Plus, 16 Pro, 16 Pro Max (A18/A18 Pro)
- iPad mini (7th gen), iPad Air/Pro with M1+
- All Apple Silicon Macs

**Android (requires API 26+, specific chipsets):**
- Google Pixel 9 series, Pixel 10 series (Tensor G3/G4)
- Samsung Galaxy S24/S25 series, Z Fold 6/7 (Snapdragon 8 Gen 3+)
- OnePlus 13, Xiaomi 15, OPPO Find X8, vivo X200 (various flagship chips)
- Other devices with Snapdragon 8 Gen 3+, Dimensity 9400, or equivalent

---

## User Experience

### Information Architecture

```
M1K3 App
├── Home (Chat Interface)
│   ├── New Conversation
│   ├── Active Conversation
│   │   ├── Message Input
│   │   ├── Message Stream
│   │   └── Quick Actions (Summarize, Rewrite, etc.)
│   └── AI Status Indicator
├── History
│   ├── Saved Conversations
│   ├── Search
│   └── Export/Delete
├── Tools
│   ├── Summarizer
│   ├── Writing Assistant
│   └── [Future: More Tools]
└── Settings
    ├── AI Settings
    │   ├── Availability Status
    │   └── Model Info
    ├── Accessibility
    │   ├── Text Size
    │   ├── Dyslexia-Friendly Mode
    │   └── High Contrast
    ├── Privacy
    │   ├── Data Storage Info
    │   └── Clear All Data
    └── About
        ├── Device Compatibility
        └── Privacy Policy
```

### Key Screens

**1. Onboarding (First Launch)**
- Explain M1K3's privacy-first approach
- Check device compatibility with clear results
- Request no permissions (emphasize this!)
- Optional: Enable share extension

**2. Chat Interface (Primary)**
- Clean, distraction-free design
- Clear AI status indicator (ready/thinking/unavailable)
- Streaming response display
- Quick action buttons for common tasks

**3. Unavailable State**
- Honest explanation of why AI isn't available
- No fake "upgrading" messaging
- Alternative suggestions (web version, compatible devices)
- App remains functional for viewing history, settings

### Accessibility Requirements

- **VoiceOver/TalkBack**: Full support with meaningful labels
- **Dynamic Type**: Scales appropriately to system settings
- **Dyslexia Mode**: OpenDyslexic font option, increased spacing
- **Reduced Motion**: Respect system preference
- **Color Contrast**: WCAG AA minimum, AAA for critical elements
- **Screen Reader Announcements**: AI status changes announced

---

## Privacy & Security

### Data Handling

| Data Type | Storage | Transmission | User Control |
|-----------|---------|--------------|--------------|
| Conversations | Local only (encrypted) | Never | Full delete |
| AI Queries | Memory only during inference | Never | Auto-cleared |
| Settings | Local only | Never | Export/clear |
| Analytics | None collected | N/A | N/A |
| Crash Reports | Optional, anonymized | If opted-in | Disable option |

### Privacy Guarantees

1. **No Cloud Processing**: All AI inference happens on-device
2. **No Data Collection**: No analytics on conversation content
3. **No Account Required**: Full functionality without sign-up
4. **No Advertising**: No ads, no ad tracking, no data sales
5. **Transparent Storage**: Users can inspect/export all stored data
6. **Complete Deletion**: "Delete All" removes everything, no hidden retention

### Security Measures

- Local database encryption using platform security (iOS Keychain, Android Keystore)
- No network requests except optional: app updates, crash reports (opt-in)
- No third-party SDKs with network access
- Certificate pinning if any network features added in future

---

## Success Metrics

### Launch Criteria (v1.0)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Core Feature Completion | 100% of P0 features | Acceptance criteria met |
| Crash-Free Rate | > 99.5% | Platform crash reporting |
| AI Response Success Rate | > 95% on supported devices | Local logging |
| Accessibility Audit | Pass WCAG AA | Automated + manual audit |
| App Size | < 50MB (excluding system AI) | Build output |

### Post-Launch KPIs

| Metric | Target | Notes |
|--------|--------|-------|
| App Store Rating | > 4.5 | Focus on quality over growth |
| Privacy-Related Reviews | Net Positive | Monitor sentiment |
| Feature Usage | Summarization > 30% of sessions | Validate feature value |
| Retention (D7) | > 40% | Without engagement tricks |

### Non-Goals (Intentionally)

- ❌ Daily Active Users as primary metric
- ❌ Session length maximization
- ❌ Push notification engagement
- ❌ Social features / sharing
- ❌ Monetization through data

---

## Roadmap

### Phase 1: Foundation (Current)

**Timeline:** Q4 2024 - Q1 2025

- [x] Project setup (KMP + Compose Multiplatform)
- [x] On-device AI research and architecture
- [ ] Core AI abstraction layer (expect/actual)
- [ ] iOS Foundation Models integration (Swift wrapper)
- [ ] Android ML Kit GenAI integration
- [ ] Basic chat UI with streaming
- [ ] Availability checking and device support UI

### Phase 2: Launch (v1.0)

**Timeline:** Q2 2025

- [ ] Summarization feature
- [ ] Writing assistance (proofreading, rewriting)
- [ ] Conversation history (local persistence)
- [ ] Share extension (both platforms)
- [ ] Accessibility audit and improvements
- [ ] App Store / Play Store submission
- [ ] Privacy policy and marketing site

### Phase 3: Enhancement (v1.x)

**Timeline:** Q3-Q4 2025

- [ ] Image understanding (when platform APIs support)
- [ ] Document processing (PDF summarization)
- [ ] Widgets (iOS/Android home screen)
- [ ] Watch companion (basic queries)
- [ ] Siri/Google Assistant integration
- [ ] Localization (based on platform model language support)

### Phase 4: Future Considerations

- [ ] macOS native app (shared codebase)
- [ ] Custom on-device model fine-tuning (if platforms allow)
- [ ] Offline document RAG (local knowledge base)
- [ ] Accessibility-focused features (live transcription assistance, reading aid)

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| iOS 26 delayed or limited rollout | Medium | High | Launch Android-first if needed; clear device requirements |
| ML Kit APIs remain in alpha/beta | Medium | Medium | Build abstraction allowing fallback; monitor Google roadmap |
| Platform models have quality issues | Medium | Medium | Clear capability communication; avoid over-promising |
| Limited supported device market | High | Medium | Target early adopters; grow with device market |
| Apple/Google restrict API access | Low | High | No mitigation possible; pivot to alternative approach |
| Swift interop complexity | Medium | Medium | Consider SKIE; allocate time for iOS-specific work |

---

## Open Questions

1. **Monetization**: Premium features? One-time purchase? Free with donations? 
   - *Current thinking: Free core, potential premium for future advanced features*

2. **Cloud Fallback**: Should there be an opt-in cloud option for unsupported devices?
   - *Current thinking: No - maintain pure privacy positioning*

3. **Model Updates**: How to handle platform model updates that change behavior?
   - *Current thinking: Document version behavior; test on updates*

4. **Branding**: M1K3 vs Mike in UI? Personality/tone of the assistant?
   - *Current thinking: "Mike" in UI, warm but not overly anthropomorphized*

---

## Appendix

### A. Competitive Landscape

| Product | On-Device | Privacy | Offline | Open Source |
|---------|-----------|---------|---------|-------------|
| M1K3 | ✅ Full | ✅ Complete | ✅ Full | ⚠️ Considering |
| ChatGPT | ❌ Cloud | ❌ Data used | ❌ No | ❌ No |
| Gemini (Cloud) | ❌ Cloud | ❌ Data used | ❌ No | ❌ No |
| Apple Intelligence | ✅ Partial | ✅ Good | ✅ Partial | ❌ No |
| Private LLM | ✅ Full | ✅ Complete | ✅ Full | ❌ No |

### B. Technical Dependencies

```
# iOS
- Xcode 26+
- iOS 26 SDK
- Foundation Models framework
- Swift 6.0+

# Android
- Android Studio Ladybug+
- API 26+ (target 35)
- ML Kit GenAI 1.0.0+
- Kotlin 2.1.0+

# Shared
- Kotlin Multiplatform 2.1.0+
- Compose Multiplatform 1.7.0+
- Kotlinx Coroutines 1.9.0+
- SQLDelight 2.0+
- Koin 4.0+
```

### C. Reference Documents

- [On-Device AI Implementation Guide](./m1k3-kmp-on-device-ai.md)
- Apple Foundation Models WWDC25 Session
- Google ML Kit GenAI Documentation
- Kotlin Multiplatform Documentation

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Dec 2024 | Kev Murphy | Initial PRD |

---

*"Technology should serve humans, not extract from them."*  
*— M1K3 Design Philosophy*
