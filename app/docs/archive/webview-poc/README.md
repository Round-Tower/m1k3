# WebView 3D Avatar POC - Archived

**Status:** Archived (2025-11-08)
**Reason:** Replaced by native Filament 3D rendering

## Summary

This directory contains the archived WebView-based 3D avatar proof of concept that used Three.js for rendering 3D models with morph targets.

## Why It Was Archived

### Technical Issues
1. **Morph targets not working** - Shape keys for facial expressions failed to animate
2. **WebView CORS restrictions** - Required Base64 encoding of models (memory intensive)
3. **Performance overhead** - JavaScript bridge communication added latency
4. **Limited debugging** - WebView environment harder to debug than native code

### Superior Alternative
Native Filament rendering (Avatar3DView.android.kt) proved superior:
- ✅ Native performance (no JavaScript bridge overhead)
- ✅ Direct asset loading (no Base64 encoding)
- ✅ Better debugging (native stack traces)
- ✅ Reference-counted engine (no multi-screen crashes)
- ✅ Full animation support (18 skeletal animations per model)
- ✅ Interactive camera controls (pinch-zoom, orbit, pan)

## What Was Archived

### Source Files
- `avatar/webview-android/` - Android WebView implementation (225 lines)
  - AvatarWebViewScreen.android.kt
- `avatar/webview-common/` - Common WebView interface (491 lines)
  - AvatarWebViewScreen.kt
  - WebViewDemoControls composable
  - JavaScript bridge data classes

### Assets
- `assets/avatar3d/` - Three.js HTML/JS (1,200+ lines)
  - index.html - Three.js r128 scene
  - Morph target test implementations
  - Animation systems

**Total Archived:** ~1,916 lines of code

## Timeline

- **2025-11-07:** WebView POC created, morph targets investigated
- **2025-11-08:** Morph targets confirmed non-functional
- **2025-11-08:** Native Filament prioritized, WebView archived

## Lessons Learned

1. **Native first:** For performance-critical 3D rendering, native is always better
2. **Morph targets complex:** GLB morph targets require careful export pipeline
3. **Skeletal animations sufficient:** 18 animations provide rich expressiveness
4. **WebView has limits:** Good for web content, not optimal for 3D rendering

## References

- Native implementation: `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/avatar/Avatar3DView.android.kt`
- Model registry: `composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/ModelRegistry.kt`
- Research doc: `FORMAT_RESEARCH_3D_MORPHS_2025_11_08.md`

## Could This Be Revived?

Possibly, if:
- Morph target export pipeline fixed (Blender → GLB validation)
- WebView performance acceptable for use case
- Need for Three.js-specific features (post-processing, custom shaders)

For now, native Filament rendering is the production solution.

---

**Archived by:** Claude Code
**Date:** 2025-11-08
**Commit:** Phase 3 - Archive WebView POC
