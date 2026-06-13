# 3D Avatar System Research Report
**Date:** 2025-11-02
**Project:** 間 AI Mobile App - Avatar System

---

## Task 1: Slow Down Animations

### Current Implementation

#### Animation Speed Formula
**Location:** `$M1K3_ROOT/app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/Avatar3DEngine.kt`

```kotlin
fun getAnimationSpeed(intensity: Float): Float {
    return 0.5f + intensity  // Maps 0.0-1.0 to 0.5-1.5
}
```

**Speed Range:**
- intensity = 0.0 → 0.5x speed (very slow)
- intensity = 0.5 → 1.0x speed (normal) ← **DEFAULT**
- intensity = 1.0 → 1.5x speed (very fast)

#### Where Speed is Applied
**Location:** `$M1K3_ROOT/app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/avatar/Avatar3DView.android.kt`

```kotlin
// Line 228: Initial animation playback
playAnimation(
    animationIndex = randomStartingAnim.index,
    loop = randomStartingAnim.isLoopable,
    speed = Avatar3DEngine.getAnimationSpeed(state.intensity) // ← Speed applied here
)

// Line 395: Frame-by-frame animation (custom implementation)
animator.applyAnimation(playbackInfo.currentAnimIndex, animTime)
animator.updateBoneMatrices()
```

**Current Default Intensity:** 0.5 (from `AvatarState` data class in `AvatarModels.kt`)

**Critical Finding:** The system uses TWO animation approaches:
1. **Initial playback** (line 225-229): Uses SceneView's `playAnimation()` with speed parameter
2. **Manual frame updates** (line 363-400): Custom `onFrame` callback that manually applies animation time

The manual frame update system does NOT use the speed parameter! This explains why animations might not respect the speed setting during runtime.

---

### Solution Options

#### Option A: Change Speed Multiplier Formula (Simplest)
**Impact:** Global change, affects all animations
**Downtime:** 5 minutes

```kotlin
// In Avatar3DEngine.kt, change line 217:

fun getAnimationSpeed(intensity: Float): Float {
    return 0.3f + intensity * 0.7f  // Maps 0.0-1.0 to 0.3-1.0x
}
```

**New Speed Range:**
- intensity = 0.0 → 0.3x speed (very slow)
- intensity = 0.5 → 0.65x speed (moderately slow) ← **NEW DEFAULT**
- intensity = 1.0 → 1.0x speed (normal)

**Pros:**
- One-line change
- Proportional to intensity (maintains emotion expressiveness)
- No new state variables needed

**Cons:**
- All animations slowed (may make excited emotions feel sluggish)
- Default intensity 0.5 → 0.65x speed (only 35% slower)

---

#### Option B: Add Global Speed Scale Factor (Most Flexible)
**Impact:** Configurable, can be adjusted per-model or in debug settings
**Downtime:** 15 minutes

```kotlin
// In Avatar3DEngine.kt, add:

object Avatar3DEngine {
    /**
     * Global animation speed scale (0.1-2.0)
     * Applied to all animations for fine-tuning
     */
    var globalSpeedScale: Float = 0.7f  // Slow down by 30%
    
    fun getAnimationSpeed(intensity: Float): Float {
        return (0.5f + intensity) * globalSpeedScale
    }
}

// In Avatar3DView.android.kt, line 228:
playAnimation(
    animationIndex = randomStartingAnim.index,
    loop = randomStartingAnim.isLoopable,
    speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
)
```

**New Speed Range (with globalSpeedScale = 0.7):**
- intensity = 0.0 → 0.35x speed (very slow)
- intensity = 0.5 → 0.7x speed (slow) ← **NEW DEFAULT**
- intensity = 1.0 → 1.05x speed (normal)

**Pros:**
- Highly configurable (can adjust without code changes)
- Can be exposed in debug menu
- Can be different per model (e.g., Colobus 0.7x, Sparrow 0.9x)

**Cons:**
- Global mutable state (not ideal for Kotlin/Compose)
- Need to consider thread safety if changed during playback

---

#### Option C: Lower Default Intensity (Least Impactful)
**Impact:** Only changes default state, user actions can still speed up
**Downtime:** 2 minutes

```kotlin
// In AvatarModels.kt, change line 134:

data class AvatarState(
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val activity: AvatarActivity = AvatarActivity.IDLE,
    val intensity: Float = 0.3f,  // Changed from 0.5f → 0.8x speed
    ...
)
```

**New Default Speed:** 0.8x (20% slower)

**Pros:**
- Minimal code change
- Doesn't affect high-energy emotions (they stay fast)
- Maintains intensity scaling behavior

**Cons:**
- Only affects default state (user-driven emotions may still be fast)
- Doesn't solve the core issue if ALL animations feel too fast

---

### Recommended Solution: **Option B + Fix Manual Animation Speed**

**Why:** The current system has a critical bug - manual frame updates don't respect speed!

**Implementation Plan:**

1. **Add global speed scale** (Option B)
2. **Fix manual animation speed** by scaling elapsed time:

```kotlin
// In Avatar3DView.android.kt, line 388-392:

// Calculate animation time with looping AND SPEED
val speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
val scaledElapsedSeconds = elapsedSeconds * speed  // ← ADD THIS

val animTime = if (currentAnim.isLoopable && currentAnim.duration > 0f) {
    scaledElapsedSeconds % currentAnim.duration  // Use scaled time
} else {
    scaledElapsedSeconds.coerceAtMost(currentAnim.duration)
}

animator.applyAnimation(playbackInfo.currentAnimIndex, animTime)
```

**Estimated Implementation Time:** 30 minutes
**Testing Time:** 15 minutes (verify all emotions, idle variants, speed transitions)

---

## Task 2: Eyes / Shape Keys (Morph Targets)

### Research Findings

#### 1. Does the Colobus Model Have Morph Targets?

**Answer: NO** ❌

**Evidence:**
```bash
$ python3 -c "inspect GLB file..."
GLB Magic: b'glTF'
GLB Version: 2
Animations: 18
Meshes: 1

# NO MORPH TARGETS FOUND
```

The Colobus_Animations.glb file has:
- ✅ 18 skeletal animations
- ✅ 1 mesh with skeleton
- ❌ **0 morph targets** (no shape keys)

**Conclusion:** The Quirky Series models do NOT include morph targets for facial expressions or eye blinking.

---

#### 2. Does SceneView/Filament Support Morph Targets?

**Answer: YES** ✅ (with limitations)

**Evidence from Web Research:**

**Filament RenderableManager API:**
- `getMorphTargetCount(instance)` - Returns number of morph targets
- `setMorphWeights(instance, weights, count, offset)` - Updates morph target weights
- `morphTargetNames` - Gets names of all morph targets
- Supports up to **4 morph targets simultaneously** in legacy mode
- Modern Filament supports more (issues #1487, #4772 discuss increasing limits)

**SceneView API Access:**
```kotlin
// Access via ModelInstance
val modelInstance: ModelInstance = modelNode.modelInstance
val morphTargetNames = modelInstance.morphTargetNames  // List<String>
modelInstance.setMorphWeights(weights)  // Updates first 4 weights
```

**Filament Documentation:**
> "Morph targets can have multiple targets (like a friendly face and an angry face), 
> with tracks holding information about how the influence of each morph target changes 
> during animation."

---

#### 3. Can We Access Morph Targets in Current Codebase?

**Answer: YES** ✅ (if the models had them)

**Access Pattern:**
```kotlin
// In Avatar3DView.android.kt, within onFrame callback:

val node = childNodes.firstOrNull() as? ModelNode
val modelInstance = node?.modelInstance

if (modelInstance != null) {
    // Check if model has morph targets
    val morphTargetNames = modelInstance.morphTargetNames
    val morphTargetCount = morphTargetNames.size
    
    if (morphTargetCount > 0) {
        // Set morph weights (0.0 = no influence, 1.0 = full influence)
        val weights = floatArrayOf(
            0.0f,  // Morph target 0
            0.5f,  // Morph target 1 (50% influence)
            1.0f,  // Morph target 2 (100% influence)
            0.0f   // Morph target 3
        )
        modelInstance.setMorphWeights(weights)
    }
}
```

**Limitations:**
- SceneView exposes `setMorphWeights()` for first 4 weights only (legacy API)
- Need Filament's `RenderableManager` directly for more control
- Can access via `modelInstance.renderableManager` (confirmed in source)

---

### Solution Pathways

Since the Colobus model **lacks morph targets**, we have 3 options:

---

#### Pathway 1: Pre-Bake Blinking Into Idle Animations (Recommended)
**Feasibility:** ✅ HIGH
**Effort:** Moderate (requires Blender workflow)
**Quality:** ★★★★★ (best integration with existing animations)

**Approach:**
1. Open Colobus_Animations.glb in Blender 3.6+
2. Add morph targets (shape keys) for eyes:
   - `EyesClosed` (fully closed eyelids)
   - `EyesHalfOpen` (squinting)
   - `EyeBlink_L` / `EyeBlink_R` (independent eyes)
3. Bake blinking into Idle_A/B/C animations:
   - Random blinks every 3-6 seconds
   - 0.15s blink duration (3-4 frames @ 24fps)
4. Export new GLB with morph targets + animations

**Pros:**
- Seamless integration (no runtime logic needed)
- Natural variation (different blink timing per idle animation)
- Can add other expressions (surprise, happy, angry eye shapes)
- Works with existing SceneView API

**Cons:**
- Requires 3D modeling skills (Blender)
- Need access to source FBX/Blend file (Quirky Series provides FBX)
- File size increase (~5-10% for morph target data)
- One-time effort per model

**Implementation Steps:**
1. Download Quirky Series FBX source files
2. Import Colobus into Blender
3. Add shape keys for eye blink (duplicate eyelid vertices, move to closed position)
4. Keyframe blinks in Idle animations (f-curve interpolation)
5. Export GLB with `+Y Up`, `Apply Transforms`, `Include Animations`
6. Test in Android Studio

**Estimated Time:** 3-6 hours (first model), 1 hour (subsequent models)

---

#### Pathway 2: Runtime Morph Target Animation (If Models Had Morph Targets)
**Feasibility:** ✅ MEDIUM (requires model updates first)
**Effort:** Low (after models have morph targets)
**Quality:** ★★★★☆ (dynamic, but may not sync perfectly with skeletal animations)

**Approach:**
1. Add morph targets to models (see Pathway 1, step 1-5)
2. Implement runtime blinking system:

```kotlin
// In Avatar3DView.android.kt, new component:

/**
 * Blinking system for morph target-based eye animation
 */
class BlinkController(
    private val modelInstance: ModelInstance,
    private val morphTargetName: String = "EyesClosed"
) {
    private var lastBlinkTime = 0L
    private var nextBlinkDelay = randomBlinkDelay()
    private var blinkStartTime = 0L
    private var isBlinking = false
    
    companion object {
        const val BLINK_DURATION_MS = 150L  // 0.15 seconds
        const val MIN_BLINK_INTERVAL_MS = 2000L  // 2 seconds
        const val MAX_BLINK_INTERVAL_MS = 5000L  // 5 seconds
    }
    
    fun update(frameTimeNanos: Long) {
        val currentTimeMs = frameTimeNanos / 1_000_000
        
        if (isBlinking) {
            // Blinking in progress
            val blinkProgress = (currentTimeMs - blinkStartTime).toFloat() / BLINK_DURATION_MS
            
            if (blinkProgress >= 1.0f) {
                // Blink complete
                isBlinking = false
                lastBlinkTime = currentTimeMs
                nextBlinkDelay = randomBlinkDelay()
                setEyeBlinkWeight(0.0f)
            } else {
                // Blink curve (ease-in-out for smooth blink)
                val weight = blinkCurve(blinkProgress)
                setEyeBlinkWeight(weight)
            }
        } else {
            // Waiting for next blink
            if (currentTimeMs - lastBlinkTime >= nextBlinkDelay) {
                // Start new blink
                isBlinking = true
                blinkStartTime = currentTimeMs
            }
        }
    }
    
    private fun blinkCurve(progress: Float): Float {
        // Smooth ease-in-out curve (closes then opens)
        return if (progress < 0.5f) {
            // Closing (0.0 → 1.0)
            (progress * 2).pow(2)
        } else {
            // Opening (1.0 → 0.0)
            1.0f - ((progress - 0.5f) * 2).pow(2)
        }
    }
    
    private fun setEyeBlinkWeight(weight: Float) {
        try {
            // Find morph target index
            val morphIndex = modelInstance.morphTargetNames.indexOf(morphTargetName)
            if (morphIndex >= 0) {
                val weights = FloatArray(4) { 0.0f }
                weights[morphIndex.coerceIn(0, 3)] = weight
                modelInstance.setMorphWeights(weights)
            }
        } catch (e: Exception) {
            println("⚠️ Failed to set blink weight: ${e.message}")
        }
    }
    
    private fun randomBlinkDelay(): Long {
        return (MIN_BLINK_INTERVAL_MS..MAX_BLINK_INTERVAL_MS).random()
    }
}

// Usage in Avatar3DViewContent:
val blinkController = remember(metadata) {
    val node = childNodes.firstOrNull() as? ModelNode
    node?.modelInstance?.let { BlinkController(it) }
}

// In onFrame callback (line 363):
onFrame = { frameTimeNanos ->
    cameraState.applyCameraNode(cameraNode)
    
    // Update blinking
    blinkController?.update(frameTimeNanos)
    
    // Update skeletal animation
    val node = childNodes.firstOrNull() as? ModelNode
    // ... rest of animation code
}
```

**Pros:**
- Dynamic blinking (adapts to any animation state)
- Natural variation (random intervals)
- No animation re-baking needed (after initial model update)
- Can be paused/resumed based on activity (e.g., no blinking during SURPRISED)

**Cons:**
- Requires model updates first (same as Pathway 1, steps 1-4)
- Additional runtime logic
- May not sync perfectly with head movements (skeletal animation runs independently)

**Estimated Time:** 4 hours (model update) + 2 hours (runtime logic) = 6 hours

---

#### Pathway 3: Shader-Based Eye Animation (No Morph Targets Needed)
**Feasibility:** ⚠️ LOW-MEDIUM (requires custom Filament materials)
**Effort:** High (advanced graphics programming)
**Quality:** ★★☆☆☆ (hacky, may not look natural)

**Approach:**
1. Create custom Filament material shader for eyes
2. Use UV scrolling or texture alpha manipulation
3. Animate eye "closing" via shader parameters

**Example Concept:**
```glsl
// Custom fragment shader for eye material

uniform float blinkProgress;  // 0.0 = open, 1.0 = closed

void material(inout MaterialInputs material) {
    // Get UV coordinates
    vec2 uv = getUV0();
    
    // Vertical gradient for eyelid effect
    float eyelidGradient = smoothstep(0.5 - blinkProgress * 0.5, 0.5 + blinkProgress * 0.5, uv.y);
    
    // Darken top/bottom of eye texture
    float eyeAlpha = mix(1.0, 0.0, blinkProgress * (1.0 - eyelidGradient));
    
    material.baseColor.a *= eyeAlpha;
}
```

**Implementation:**
```kotlin
// Update shader parameter every frame
val eyeMaterialInstance = modelInstance.materialInstances.find { 
    it.name.contains("Eye", ignoreCase = true) 
}

eyeMaterialInstance?.setParameter("blinkProgress", blinkWeight)
```

**Pros:**
- No model updates needed (works with existing Colobus)
- Lightweight (no additional geometry data)
- Can be very flexible (e.g., eye dilation, color changes)

**Cons:**
- Requires custom Filament material creation (complex)
- May not look realistic (no actual geometry deformation)
- Shader must be written in Filament's material system (not standard GLSL)
- Limited by eye texture/geometry (may not work if eyes are simple spheres)

**Estimated Time:** 8-12 hours (shader development + integration)

**NOT RECOMMENDED** due to complexity vs. quality tradeoff.

---

### Recommended Solution: **Pathway 1 (Pre-Baked Blinking)**

**Rationale:**
1. ✅ Best visual quality (seamless integration with skeletal animations)
2. ✅ No runtime performance cost (blinks are part of animation data)
3. ✅ Works with existing SceneView API (no shader hacks)
4. ✅ Can add other facial expressions (surprise eyes, angry eyebrows)
5. ⚠️ Requires 3D modeling skills, but Quirky Series provides FBX source

**Step-by-Step Implementation:**

1. **Obtain Source Files** (5 minutes)
   - Download Quirky Series FBX from asset store
   - Locate `Colobus.fbx` or `Colobus_Animations.fbx`

2. **Import to Blender** (10 minutes)
   - File → Import → FBX
   - Check armature, mesh, materials imported correctly
   - Verify 18 animations present

3. **Add Eye Morph Targets** (60 minutes)
   - Select Colobus mesh
   - Tab into Edit Mode
   - Select eyelid vertices (top/bottom of eye spheres)
   - Add Shape Key: "EyesClosed"
   - Move eyelid vertices to closed position
   - Return to Object Mode
   - Optional: Add "EyesHalfOpen", "EyesWide" for other expressions

4. **Animate Blinks in Idle Animations** (90 minutes)
   - Switch to Animation workspace
   - Select Idle_A animation
   - Add keyframes for "EyesClosed" shape key:
     - Frame 0: value = 0.0 (open)
     - Frame 60: value = 0.0 (open)
     - Frame 61: value = 1.0 (closed) - blink start
     - Frame 64: value = 0.0 (open) - blink end (3 frames @ 24fps = 0.125s)
     - Frame 120: value = 0.0 (open)
   - Set interpolation to "Bezier" for smooth blink curve
   - Add 2-3 random blinks per idle animation (every 3-5 seconds)
   - Repeat for Idle_B, Idle_C

5. **Export GLB** (10 minutes)
   - File → Export → glTF 2.0 (.glb)
   - Settings:
     - Format: GLB
     - Include: Selected Objects (Colobus mesh + armature)
     - Transform: +Y Up
     - Geometry: Apply Modifiers
     - Animation: Include Animations (all 18)
     - Compression: None (preserve quality)
   - Export as `Colobus_Animations_v2.glb`

6. **Test in Android** (30 minutes)
   - Copy to `composeApp/src/androidMain/assets/models/`
   - Update `ModelRegistry.kt` to use new file
   - Run app, observe blinking in idle states
   - Verify other animations still work
   - Check morph target names: `modelInstance.morphTargetNames.forEach { println(it) }`

**Total Estimated Time:** 3.5 hours (first model)

**Future Models:** Once workflow established, subsequent models take ~1 hour each.

---

## Summary & Recommendations

### Animation Speed (Task 1)

**Recommended:** Option B (Global Speed Scale) + Fix Manual Animation Speed

**Code Changes:**
1. Add `globalSpeedScale` to `Avatar3DEngine` (1 line)
2. Multiply in `getAnimationSpeed()` (1 line)
3. Fix manual animation speed scaling (3 lines in `onFrame` callback)

**Estimated Time:** 30 minutes implementation + 15 minutes testing

**Result:** All animations 30% slower (0.7x), configurable via debug menu

---

### Blinking System (Task 2)

**Recommended:** Pathway 1 (Pre-Baked Blinking)

**Workflow:**
1. Export Colobus FBX → Blender
2. Add "EyesClosed" morph target
3. Keyframe random blinks in Idle animations
4. Export enhanced GLB
5. Test in Android app

**Estimated Time:** 3.5 hours (first model), 1 hour (subsequent models)

**Result:** Natural blinking integrated with idle animations

---

**Alternative (If Blender not available):** Pathway 2 (Runtime Morph Targets)
- Still requires model updates, but blinks are procedural
- More code, but more flexible (can disable during SURPRISED emotion)
- Estimated time: 6 hours total

---

**NOT RECOMMENDED:** Pathway 3 (Shader-Based)
- Too complex, mediocre results
- Better to invest time in proper morph targets

---

## Next Steps

1. **Implement animation speed fix** (30 min) ← Do this first, quick win
2. **Decide on blinking approach** (Pathway 1 vs 2)
3. **If Pathway 1:** Learn Blender basics, obtain FBX source files
4. **If Pathway 2:** Implement model updates first, then runtime logic

---

**Questions for User:**
1. Do you have access to Blender (free) and willing to learn basic morph target workflow?
2. Do you have the Quirky Series FBX source files?
3. Should blinking be present in ALL emotions, or disabled for some (e.g., SURPRISED = wide eyes)?
4. Do you want debug UI for animation speed testing? (slider in avatar lab)

---
