# 3D Format Research: Morph Target Alternatives - 2025-11-08

## Context
GLB files validated with 29 morph targets present, but morphs not rendering correctly in Android WebView with Three.js. Researching alternative formats and diagnostic approaches.

---

## Format Evaluation Matrix

| Format | Morph Support | Complexity | File Size | Performance | Success Likelihood |
|--------|---------------|------------|-----------|-------------|-------------------|
| **GLB (Current)** | ✅ Yes | Low (1/10) | Baseline | Good | Unknown (needs test) |
| **FBX** | ✅ Yes | High (7/10) | +50-100% | Similar | Low (3/10) - Known bugs |
| **VRM** | ✅ Yes | High (8/10) | Similar | Slightly slower | Good (7/10) - Avatar-specific |
| **glTF JSON** | ✅ Yes | Low (1/10) | +200-300% | Slower parse | Low (2/10) - Same code path |
| **Three.js Upgrade** | N/A | Medium (4/10) | Minimal | Better | Medium (6/10) - Mixed results |
| **VAT** | ⚠️ Baked | Very High (9/10) | Variable | Much faster | High (9/10) - Major change |
| **Minimal Test** | ✅ Yes | Very Low (1/10) | ~1KB | N/A | **Perfect (10/10) - Diagnostic** |

---

## 1. FBX Direct Loading ❌ NOT RECOMMENDED

### Pros
- Native DCC format
- FBXLoader supports morph targets
- Rich metadata

### Cons
- **Known bugs in Three.js r158-r162** with morph targets causing mesh breakage
- Morph target transformation issues (Oct 2024)
- Historical 8 blend shape GPU limit on some devices
- Naming mismatches between apps
- 50-100% larger file size
- Slower parsing than GLB

### Verdict
**Don't use.** Active morph target bugs in recent Three.js versions make FBX unreliable.

---

## 2. VRM Format ⭐ GOOD ALTERNATIVE

### Overview
Purpose-built avatar format based on glTF 2.0 with standardized blend shapes.

### Pros
- **Designed specifically for avatars** with morph targets
- Standardized blend shape names (BlendShapeGroups)
- Active library: `@pixiv/three-vrm` (maintained 2024)
- Built-in methods: `setBlendShapeWeight(name, value)`
- Used by VRoid, VSeeFace, VTuber apps
- Should work if GLB works (same base format)

### Cons
- Requires converting Quirky Series models to VRM
- Additional library dependency
- Learning curve for VRM authoring
- May be overkill for use case

### Implementation
```javascript
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
import { VRMLoaderPlugin } from '@pixiv/three-vrm';

const loader = new GLTFLoader();
loader.register((parser) => new VRMLoaderPlugin(parser));

loader.load('avatar.vrm', (gltf) => {
    const vrm = gltf.userData.vrm;

    // Standardized blend shape API
    vrm.expressionManager.setValue('happy', 1.0);
    vrm.expressionManager.setValue('blink', 0.5);

    scene.add(vrm.scene);
});
```

### Verdict
**Good long-term solution** if conversion workflow is acceptable. Best for avatar-focused applications.

---

## 3. JSON glTF (Not Binary) 🔍 DIAGNOSTIC TOOL

### Pros
- Human-readable JSON
- Easy to debug morph target data
- Can inspect exact structure
- Same loader as GLB
- No API changes

### Cons
- 200-300% larger file size
- Slower parsing
- **Same rendering code** - unlikely to fix issue

### Use Case
Export one model as `.gltf` to inspect JSON structure:
```json
{
  "meshes": [{
    "primitives": [{
      "targets": [
        { "POSITION": 0 },  // Morph target 0
        { "POSITION": 1 }   // Morph target 1
      ]
    }]
  }]
}
```

### Verdict
**Use for debugging only.** Won't fix rendering, but helps understand data structure.

---

## 4. Three.js Version Issues ⚠️ CRITICAL FINDINGS

### Current Version
Likely **r128 or older** (based on code analysis).

### Known Issues
- **r128 → r145:** `Material.morphTargets` property removed (breaking change)
- **WebGL 2 texture-based morph system** introduced with many bugs
- **Issue #24545:** Morphs broken on mobile with 241+ morphs (uniform vector limit of 256)
- **Issue #24243:** MorphAttributes can't be updated once set
- Performance issues with morphAttribute iteration

### Mobile-Specific Limits
- **Uniform vector limit:** 256 on most mobile devices (vs 1024 desktop)
- **241 morph limit** on tested Android devices (ViVO, ASUS, AQUOS)
- **Your model:** 29 morphs ✅ **Well within safe limits**

### Recommendation
1. Check current Three.js version:
```bash
head -1 composeApp/src/androidMain/assets/avatar3d/js/three.min.js
# Should show: // threejs.org/license (r###)
```

2. If r128 or older, upgrade to **r170+**
3. Enable WebGL 2 in WebView:
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    setDomStorageEnabled(true)
    setDatabaseEnabled(true)
    // Additional settings may help
}
```

### Verdict
**Upgrade likely beneficial** but mixed results. Test minimal cube first.

---

## 5. Vertex Animation Textures (VAT) 🎮 MOBILE OPTIMIZATION

### Overview
Bake morph targets into texture data, sample in vertex shader.

### Pros
- **Unlimited animation capacity**
- Much faster GPU rendering
- Smaller file size potential
- Proven for mobile games
- No morph target limits

### Cons
- **Lose real-time morph control**
- Requires complete workflow change
- Must pre-bake all expressions
- Need Blender VAT addon
- Not suitable for dynamic facial animation
- Shader programming required

### Use Case
Best for:
- Pre-baked animation sequences
- Crowd characters
- Looping ambient animations

**Not suitable for:**
- Dynamic expression control
- Real-time emotion mapping
- Interactive facial animation

### Verdict
**Last resort only.** Major workflow change, loses real-time control.

---

## 6. Minimal Test Model ⭐⭐⭐ **DO THIS FIRST**

### Why This Is Critical
Creates a 1KB test cube with 1 morph target to **definitively diagnose** the issue in 5 minutes.

### Test Results Interpretation
- **Cube animates (stretches/shrinks):** ✅ Morphs work! Problem is complex model or GLB export
- **Cube doesn't change:** ❌ Three.js version or WebView configuration issue

### Implementation
Add to `index.html` after line 247:

```javascript
// Diagnostic: Minimal morph test
window.testMinimalMorph = function() {
    console.log('[間 AI 3D] 🧪 Creating minimal morph test...');

    // Remove existing model
    if (currentModel) {
        scene.remove(currentModel);
        currentModel = null;
    }

    // Create test cube with one morph target
    const geometry = new THREE.BoxGeometry(1, 1, 1);
    const positions = geometry.attributes.position.array;

    // Clone positions for morph target
    const morphPositions = new Float32Array(positions.length);
    morphPositions.set(positions);

    // Stretch top 4 vertices in Y-axis (indices 1, 13, 25, 37)
    // This creates a "stretched cube" morph
    [1, 13, 25, 37].forEach(i => morphPositions[i] *= 2);

    // Add morph target to geometry
    geometry.morphAttributes.position = [
        new THREE.BufferAttribute(morphPositions, 3)
    ];

    // Create material (morphTargets: true required for r128)
    const material = new THREE.MeshStandardMaterial({
        color: 0xff0000,
        morphTargets: true  // Required for Three.js r128
    });

    // Create mesh
    currentModel = new THREE.Mesh(geometry, material);
    currentModel.position.set(0, 1, 0);
    currentModel.morphTargetInfluences = [0]; // Start at 0% morph

    scene.add(currentModel);

    // Animate morph 0 → 1 → 0 over 2 seconds
    let startTime = Date.now();
    const animateMorph = () => {
        const elapsed = Date.now() - startTime;
        const cycle = (elapsed % 2000) / 2000; // 0 to 1
        const morph = Math.abs(cycle * 2 - 1); // 0 → 1 → 0

        if (currentModel && currentModel.morphTargetInfluences) {
            currentModel.morphTargetInfluences[0] = morph;

            // Log every 30 frames
            if (Math.random() < 0.033) {
                console.log('[間 AI 3D] 📊 Morph influence:', morph.toFixed(3));
            }
        }
    };

    // Run animation loop
    window.morphTestInterval = setInterval(animateMorph, 16); // ~60fps

    console.log('[間 AI 3D] ✅ Test cube created');
    console.log('[間 AI 3D] 📺 Watch for cube stretching vertically');
    console.log('[間 AI 3D] 📊 Check console for morph values');
};

// Cleanup function
window.stopMinimalMorphTest = function() {
    if (window.morphTestInterval) {
        clearInterval(window.morphTestInterval);
        console.log('[間 AI 3D] ⏹️  Test stopped');
    }
};
```

### How to Run Test

**From Kotlin:**
```kotlin
// Add to AvatarWebViewScreen.android.kt
Button(onClick = {
    webViewNavigator.evaluateJavaScript("window.testMinimalMorph();") { result ->
        println("[間 AI] Minimal morph test started")
    }
}) {
    Text("Test Morph Cube")
}
```

**Manual (Chrome DevTools):**
```javascript
// In WebView console (chrome://inspect)
window.testMinimalMorph();
```

### Expected Results

**If morphs work:**
- Red cube appears
- Cube stretches/shrinks vertically
- Console logs show morph values: 0.000 → 1.000 → 0.000
- **Conclusion:** GLB export is the problem, not Three.js

**If morphs don't work:**
- Cube appears but stays same size
- Console may show errors
- **Conclusion:** Three.js version or WebView issue

---

## Recommendations Priority

### 1. ⭐⭐⭐ **Minimal Test (5 minutes)**
Run the cube test to diagnose:
- Three.js capability
- WebView configuration
- Basic morph rendering

### 2. ⭐⭐ **Three.js Version Check (10 minutes)**
```bash
# Check current version
head -1 composeApp/src/androidMain/assets/avatar3d/js/three.min.js

# If r128 or older, upgrade to r170+
# Download: https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.min.js
```

### 3. ⭐ **VRM Format (Long-term, 2-4 hours)**
If GLB morphs fundamentally don't work:
- Convert one model to VRM
- Test with three-vrm library
- Provides standardized avatar workflow

### 4. **JSON glTF Debug (30 minutes)**
Only for understanding data structure:
- Export Sparrow as `.gltf` (not `.glb`)
- Inspect JSON for morph target structure
- Compare with working examples

### 5. ❌ **Don't Bother**
- FBX: Known morph bugs
- VAT: Loses real-time control
- Format changes without testing first

---

## Technical Details

### Mobile WebView Morph Limits
- **Uniform vectors:** 256 max (mobile) vs 1024 (desktop)
- **Your models:** 29 morphs ✅ Safe
- **Tested devices:** ViVO, ASUS, AQUOS (241 morph limit)
- **WebGL 1 vs 2:** WebGL 2 has better morph support

### Three.js Morph Target API (r128)

**Old API (r128):**
```javascript
material.morphTargets = true; // Required!
mesh.morphTargetInfluences[0] = 0.5;
```

**New API (r145+):**
```javascript
// material.morphTargets removed
// Auto-detected from geometry
mesh.morphTargetInfluences[0] = 0.5;
```

### GLB Morph Target Structure
```json
{
  "meshes": [{
    "primitives": [{
      "attributes": {
        "POSITION": 0,
        "NORMAL": 1
      },
      "targets": [
        { "POSITION": 2 },  // Morph 0: Position deltas
        { "POSITION": 3 }   // Morph 1: Position deltas
      ]
    }],
    "extras": {
      "targetNames": ["eyes.blink", "eyes.happy"]
    }
  }]
}
```

---

## Next Steps

1. **Run minimal test** (DO THIS NOW)
2. **Check Three.js version**
3. **Based on test results:**
   - If cube works → Debug GLB export
   - If cube fails → Upgrade Three.js or try VRM

---

## References

- Three.js Morph Targets: https://threejs.org/docs/#api/en/objects/Mesh.morphTargetInfluences
- VRM Specification: https://github.com/vrm-c/vrm-specification
- three-vrm Library: https://github.com/pixiv/three-vrm
- Three.js Issue #24545: Mobile morph limits
- glTF 2.0 Spec: https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#morph-targets

---

**Research Date:** 2025-11-08
**Status:** Awaiting minimal test results
**Recommendation:** Run cube test before any format changes
