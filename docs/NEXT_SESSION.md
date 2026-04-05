# Next Session — 2026-04-05

## Completed

### Chat UX Redesign
- **ChatInputBar**: Glassmorphic pill-shaped input with animated send button, gradient fade, model chip
- **Drawer navigation**: Primary nav (Chat, History, Eco, Settings) moved from bottom bar to drawer
- **Bottom nav removed**: Chat screen is now full-screen
- **Fixed ecostats routing bug**: Drawer was using "ecostats" instead of "eco_stats" route

### Bug Fixes
- **ClearConversationDialogTest**: Fixed import `eco.SessionEcoStats` → `chat.SessionEcoStats`
- **LlamaCppEngine null safety**: `stripStopTokens` now handles null JNI returns (was NPE crash)
- **Empty response guard**: `generate()` and `generateStreaming()` return `Result.failure` for empty responses

### Llamatik Upgrade
- Upgraded from 0.13.0 → 0.18.2 (API backwards-compatible, no code changes needed)
- Gets KV cache support, generation default fixes, JVM artifact completion

### Research & Architecture
- **AICore**: Pixel 9a NOT on ML Kit Prompt API device list (only 9/Pro/XL/Fold)
- **LiteRT-LM**: Evaluated — good Android perf (NPU), but proprietary format, no GGUF, no desktop
- **llama.cpp Gemma 4**: Requires b8637+ (April 2, 2026). Active bugs being patched.
- **ADR-0001**: "Build Our Own KMP Inference Library" — accepted, signed with MurphySig
- **Decision doc**: `docs/MA_INFERENCE_LIBRARY_DECISION.md` — full options analysis

## Pending Decision

**Ma (間) inference library** — build our own vs fork Llamatik. See `docs/MA_INFERENCE_LIBRARY_DECISION.md`.

Kev is leaning toward owning the stack ("Ma") but wants to sit with it. The name Ma is liked.

## Next Up

1. **Decide on Ma vs Fork** — review the decision doc, make the call
2. **If Ma**: Set up module, NDK/CMake, JNI bridge (~3 days)
3. **If Fork**: Clone Llamatik, bump llama.cpp submodule, fix JNI bridge (~4-5 days)
4. **Commit today's work** — UI redesign + Llamatik upgrade + bug fixes (5 files changed)
5. **Gemma 4 download polish** — cancel button, retry, storage check
6. **Test drawer nav + chat input** on Pixel 9a (USB connected, app running)

## Gotchas & Blockers

- **Pixel 9a USB**: Connected as `59021JEBF12282` — stable, use this over WiFi ADB
- **Emulator**: `Pixel_9_Pro` AVD available, ~2GB RAM so model inference fails but UI testing works
- **Gemma 4 GGUF on device**: Downloaded but won't load — Llamatik's llama.cpp (b7815) doesn't support Gemma 4 architecture
- **Gemma 4 in llama.cpp**: Bleeding edge, active bugs (segfault #21336, tokenizer #21343). Wait ~2-4 weeks to stabilize.
- **Pre-existing test failures**: 13 tests in GenerationConstantsTest etc. — pre-existing, not from our changes

## Modified Files (This Session)

### UI
- `composeApp/src/androidMain/.../ui/components/ChatInputBar.kt` — Glassmorphic pill redesign
- `composeApp/src/commonMain/.../navigation/SidebarItemData.kt` — Added primaryNavItems
- `composeApp/src/commonMain/.../ui/drawer/DrawerContent.kt` — Two-section drawer layout
- `composeApp/src/androidMain/.../MainActivity.kt` — Removed bottom bar, fixed drawer routing

### Engine
- `composeApp/src/androidMain/.../ai/LlamaCppEngine.kt` — Null-safe stripStopTokens, empty-response guards

### Config
- `composeApp/build.gradle.kts` — Llamatik 0.13.0 → 0.18.2

### Tests
- `composeApp/src/androidUnitTest/.../ClearConversationDialogTest.kt` — Fixed SessionEcoStats import

### Docs
- `docs/adr/0001-own-inference-library.md` — ADR + MurphySig
- `docs/MA_INFERENCE_LIBRARY_DECISION.md` — Full decision analysis

## Continuation Prompt

> We're working on the 間 AI mobile app (KMP Compose, `/Users/kevinmurphy/Development/m1k3/app/`). Last session we redesigned the chat input (glassmorphic pill), migrated bottom nav to drawer, upgraded Llamatik to 0.18.2, fixed LlamaCppEngine null safety bugs, and deeply researched Gemma 4 inference options.
>
> **Pending decision**: Build "Ma" (our own KMP inference library wrapping llama.cpp) vs fork Llamatik. Decision doc at `docs/MA_INFERENCE_LIBRARY_DECISION.md`, ADR at `docs/adr/0001-own-inference-library.md`.
>
> Key context:
> - Pixel 9a connected via USB (`59021JEBF12282`)
> - Llamatik 0.18.2 running, Gemma 3 270M works
> - Gemma 4 GGUF on device but can't load (llama.cpp too old)
> - AICore NOT viable on Pixel 9a
> - Today's changes not yet committed
> - Pre-existing 13 test failures in GenerationConstantsTest (not ours)
