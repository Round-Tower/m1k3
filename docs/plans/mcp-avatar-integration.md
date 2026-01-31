# M1K3 Avatar + Voice: Claude Desktop & Standalone via Shared Web 3D

> Plan created: 2026-01-30
> Status: **In Progress** - Milestones 1-3 Complete
> Last updated: 2026-01-30

## Summary

Build a **shared web 3D avatar system** (THREE.js) that powers both:
1. **Claude Desktop** via MCP Apps (iframe)
2. **Standalone popover app** (Electron/Tauri)

Two-way voice with full 3D animated avatars (animals, masks, custom characters).

---

## Architecture

```
                    ┌─────────────────────────────────────────────┐
                    │         SHARED WEB AVATAR SYSTEM            │
                    │  ┌───────────────────────────────────────┐  │
                    │  │  THREE.js Renderer                    │  │
                    │  │  ├── GLBModelLoader.ts       ✅       │  │
                    │  │  ├── AnimationController.ts  ✅       │  │
                    │  │  └── AvatarRenderer.ts       ✅       │  │
                    │  ├───────────────────────────────────────┤  │
                    │  │  ModelRegistry.ts            ✅       │  │
                    │  │  ├── 8 Quirky Series animals          │  │
                    │  │  ├── Masks                            │  │
                    │  │  └── Custom models (extensible)       │  │
                    │  └───────────────────────────────────────┘  │
                    └─────────────────┬───────────────────────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│   MCP App (iframe)   │  │  Standalone Popover  │  │    KMP Mobile App    │
│   Claude Desktop     │  │  Electron/Tauri      │  │    (existing)        │
│                      │  │                      │  │                      │
│  postMessage ←→ MCP  │  │  WebSocket ←→ M1K3   │  │  Filament/SceneView  │
│  Tools (stdio)       │  │  backend             │  │  Same GLB assets     │
└──────────────────────┘  └──────────────────────┘  └──────────────────────┘
              │                       │
              └───────────┬───────────┘
                          ▼
┌───────────────────────────────────────────────────────────────┐
│                    mcp_unified_server.py                      │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐│
│  │  TTS Tools  │  │  STT Tools  │  │    Avatar Tools        ││
│  │  speak()    │  │  listen()   │  │  get/set_emotion()     ││
│  │  + emotion  │  │  transcribe │  │  set_model()           ││
│  └──────┬──────┘  └──────┬──────┘  └───────────┬────────────┘│
│         ↓                ↓                     ↓              │
│  IntelligentTTS      STTManager         AvatarController     │
│  (Kokoro/Piper)      (Vosk/Whisper)     (emotion mapping)    │
└───────────────────────────────────────────────────────────────┘
```

---

## Progress Summary

| Milestone | Status | Description |
|-----------|--------|-------------|
| 1. Web Avatar Foundation | ✅ Complete | THREE.js + TypeScript setup, model loading |
| 2. Animation System | ✅ Complete | Emotion→animation mapping, controller |
| 3. Unified MCP Server | ✅ Complete | Python backend with TTS + Avatar + STT tools |
| 4. MCP App Integration | 🔲 Next | Claude Desktop iframe UI |
| 5. Standalone Popover | 🔲 Pending | Electron/Tauri wrapper |

---

## Completed: Web Avatar System (`src/web-avatar/`)

```
src/web-avatar/
├── package.json                 ✅ THREE.js + TypeScript + Vite
├── tsconfig.json                ✅
├── vite.config.ts               ✅
├── index.html                   ✅ Test page
└── src/
    ├── index.ts                 ✅ Main exports
    ├── registry/
    │   ├── ModelMetadata.ts     ✅ Position3D, BoundingBox, AnimationMetadata
    │   └── ModelRegistry.ts     ✅ 9 models (8 animals + Mask)
    ├── animation/
    │   ├── AvatarState.ts       ✅ 9 emotions, 6 activities
    │   ├── AnimationIntrospector.ts  ✅ Fuzzy emotion→animation mapping
    │   └── AnimationController.ts    ✅ THREE.js AnimationMixer wrapper
    ├── renderer/
    │   ├── GLBModelLoader.ts    ✅ Load GLB with metadata extraction
    │   ├── CameraAutoFit.ts     ✅ Auto-frame models
    │   └── AvatarRenderer.ts    ✅ Main THREE.js scene + controls
    └── communication/
        └── MCPAppBridge.ts      ✅ postMessage for Claude Desktop
```

### To Test Web Avatar

```bash
cd src/web-avatar
npm install
npm run dev
# Open http://localhost:5173
```

---

## Completed: Milestone 3 - Unified MCP Server

### Implemented MCP Tools

```python
# TTS Tools
"speak"              # Enhanced with emotion_hint for avatar sync
"list_voices"        # List Kokoro + Piper voices
"set_voice"          # Set default voice
"get_voice_status"   # TTS system status

# Avatar Tools (NEW)
"get_avatar_state"   # Returns emotion, state, model_id, intensity
"set_avatar_emotion" # emotion + intensity (0-100)
"set_avatar_state"   # idle/thinking/speaking/listening/error/loading
"set_avatar_model"   # Switch model (colobus, sparrow, mask, etc.)
"list_avatar_models" # Returns 9 Quirky Series models

# STT Tools (NEW)
"start_voice_input"  # Listen via mic, returns transcribed text
"get_stt_status"     # STT engine info
```

### Files Created/Modified

| File | Action | Description |
|------|--------|-------------|
| `mcp_unified_server.py` | ✅ Created | 450+ lines, TTS + Avatar + STT unified |
| `.mcp.json` | ✅ Updated | Points to unified server |

### Key Features

- **Avatar-TTS Sync**: `speak` with `emotion_hint` sets avatar emotion during speech
- **9 Avatar Models**: Colobus (default), Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish, Mask
- **Multi-engine STT**: Vosk, Whisper, Web Speech fallback
- **11 Audio Effects**: intercom (default), robot, chipmunk, giant, echo, reverb, chorus, lofi, nostalgic, flanger

---

## Milestone 4: MCP App Integration

### Files to Create

```
src/mcp/app_ui/
├── index.html       # MCP App entry point
├── app.ts           # Import web-avatar bundle
└── styles.css       # Compact avatar styles (~300px width)
```

### Tasks

1. Build web-avatar as UMD bundle
2. Create MCP App HTML with iframe-friendly layout
3. Wire `MCPAppBridge` to `AvatarRenderer`
4. Add visual states (speaking glow, listening pulse)
5. Test in Claude Desktop

---

## Milestone 5: Standalone Popover

### Options

| Framework | Pros | Cons |
|-----------|------|------|
| **Electron** | Mature, good THREE.js support | Large bundle (~150MB) |
| **Tauri** | Small (~10MB), Rust backend | Newer, less ecosystem |

### Tasks

1. Choose framework (recommend Tauri for size)
2. Create `WebSocketBridge.ts` for direct M1K3 connection
3. System tray with global hotkey (⌘+Shift+M)
4. Always-on-top floating window
5. Direct microphone access

---

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Web 3D Renderer | THREE.js | Industry standard, GLB support, works in iframes |
| Model Format | GLB (same as KMP) | Reuse existing Quirky Series assets |
| Animation System | Port from KMP | `Avatar3DEngine.kt` logic → TypeScript |
| Model Registry | Port from KMP | `ModelRegistry.kt` already platform-agnostic |
| MCP Server | Unified Python | Avatar must sync with voice activity |
| Voice Input | Backend STT | Iframe sandbox blocks mic |

---

## Ported from KMP (Kotlin → TypeScript) ✅

| Kotlin Source | TypeScript Target | Status |
|---------------|-------------------|--------|
| `ModelRegistry.kt` | `ModelRegistry.ts` | ✅ |
| `ModelMetadata.kt` | `ModelMetadata.ts` | ✅ |
| `AvatarModels.kt` | `AvatarState.ts` | ✅ |
| `Avatar3DEngine.kt` | `AnimationIntrospector.ts` | ✅ |
| `AnimationIntrospector.kt` | `AnimationIntrospector.ts` | ✅ |
| `CameraAutoFit.kt` | `CameraAutoFit.ts` | ✅ |

---

## Risks & Mitigations

| Risk | Probability | Mitigation | Status |
|------|-------------|------------|--------|
| GLB morph targets broken | Medium | Use skeletal animation only | ⚠️ Monitor |
| THREE.js bundle too large | Low | Tree-shake; lazy load | ✅ Vite handles |
| Iframe audio sandbox | High | Audio on Python backend | ✅ Planned |
| Animation mapping feels off | Medium | Tune intensity scaling | ✅ Adjustable |

---

## Model Assets (Ready to Use)

From `app/composeApp/.../avatar/3d/models/`:
- **Colobus** (default) - 18 animations, primate
- **Sparrow** - bird
- **Gecko** - reptile
- **Herring** - fish
- **Muskrat** - rodent
- **Pudu** - deer
- **Taipan** - snake
- **Inkfish** - cephalopod
- **Mask** - static, procedural animation

All **Quirky Series (FREE)** - commercial use OK.

---

## Critical Files Reference

### Web Avatar (NEW - Complete)
- `src/web-avatar/src/index.ts` - Main exports
- `src/web-avatar/src/renderer/AvatarRenderer.ts` - THREE.js scene
- `src/web-avatar/src/animation/AnimationController.ts` - Animation mixer

### Python Backend (To Modify)
- `mcp_tts_server.py` - Base for unified server
- `src/avatar/avatar_controller.py` - Emotion patterns
- `src/engines/stt/stt_manager.py` - STT integration

### GLB Assets
- `app/composeApp/src/commonMain/composeResources/files/avatar/3d/models/`

---

## Research References

### MCP Apps (Jan 2026)
- [MCP Apps announcement](http://blog.modelcontextprotocol.io/posts/2026-01-26-mcp-apps/)
- PopUI for visual faces/avatars
- Supports React, Vue, Svelte, vanilla JS
- Sandboxed iframes, postMessage communication

### MCP Protocol
- Stdio transport for local (recommended)
- Streamable HTTP for cloud/browser
- Python SDK most stable
