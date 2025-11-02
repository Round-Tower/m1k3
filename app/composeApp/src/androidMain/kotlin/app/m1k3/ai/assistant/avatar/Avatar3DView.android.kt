package app.m1k3.ai.assistant.avatar

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.delay

/**
 * 間 AI Avatar 3D View (Android)
 *
 * Renders Colobus monkey GLB model with SceneView.
 * Supports emotion-based animations and real-time state updates.
 *
 * Architecture:
 * - SceneView (Filament wrapper for Compose)
 * - Colobus_LOD1.glb (74KB base model with animations)
 * - Avatar3DEngine (animation mapping logic)
 */

/**
 * 3D avatar view with SceneView
 *
 * Renders the Colobus monkey model with animations based on AvatarState.
 *
 * @param state Current avatar state (emotion + activity)
 * @param modifier Optional modifier
 * @param autoRotate Whether to slowly rotate the model
 */
@Composable
fun Avatar3DView(
    state: AvatarState,
    modifier: Modifier = Modifier,
    autoRotate: Boolean = false
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    // Track current animation name (simpler than comparing AnimationInfo objects)
    var currentAnimationName by remember { mutableStateOf<String?>(null) }
    var timeInState by remember { mutableFloatStateOf(0f) }

    // Get target animation when state changes
    val targetAnimation = remember(state.emotion, state.activity) {
        Avatar3DEngine.getAnimation(state)
    }

    // Track time in current state for idle variants
    LaunchedEffect(state) {
        while (true) {
            delay(100)  // Update every 100ms
            timeInState += 0.1f
        }
    }

    // Create model nodes
    val childNodes = rememberNodes {
        add(
            ModelNode(
                // Use the combined animations file which includes both model and all 18 animations
                modelInstance = modelLoader.createModelInstance("models/Colobus_Animations.glb"),
                scaleToUnits = 1.2f  // Reduced from 1.5f to fit better
            ).apply {
                // Position the model - centered and visible
                // y = 0 for center, z = -4 to move back more
                position = io.github.sceneview.math.Position(x = 0f, y = 0f, z = -4.0f)

                // Play initial animation
                try {
                    val animIndex = getAnimationIndex(targetAnimation.name)
                    playAnimation(
                        animationIndex = animIndex,
                        loop = targetAnimation.loopable,
                        speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
                    )
                    currentAnimationName = targetAnimation.name
                    println("✅ Initial animation: ${targetAnimation.name}")
                } catch (e: Exception) {
                    println("⚠️ Failed to play animation '${targetAnimation.name}': ${e.message}")
                    e.printStackTrace()
                    // Fallback to first animation (Idle_A)
                    try {
                        playAnimation(animationIndex = 0, loop = true, speed = 1.0f)
                        currentAnimationName = "Idle_A"
                    } catch (fallbackError: Exception) {
                        println("❌ Failed to play fallback animation: ${fallbackError.message}")
                    }
                }
            }
        )
    }

    Box(modifier = modifier.size(200.dp)) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = childNodes,
            onFrame = { frameTimeNanos ->
                // Update animation if state changed
                val node = childNodes.firstOrNull() as? ModelNode

                // Check if animation needs to change (compare by name)
                if (node != null && currentAnimationName != targetAnimation.name) {
                    try {
                        val animIndex = getAnimationIndex(targetAnimation.name)
                        println("🔄 Changing animation: $currentAnimationName → ${targetAnimation.name} (index=$animIndex)")
                        node.playAnimation(
                            animationIndex = animIndex,
                            loop = targetAnimation.loopable,
                            speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
                        )
                        currentAnimationName = targetAnimation.name
                        println("✅ Animation changed successfully")
                    } catch (e: Exception) {
                        println("⚠️ Failed to update animation '${targetAnimation.name}': ${e.message}")
                        e.printStackTrace()
                    }
                }

                // Optional: Rotate idle animations every 10 seconds
                if (state.activity == AvatarActivity.IDLE && timeInState >= 10f) {
                    val idleVariant = Avatar3DEngine.getIdleVariant(timeInState)
                    if (currentAnimationName != idleVariant) {
                        try {
                            val animIndex = getAnimationIndex(idleVariant)
                            println("🔄 Rotating to idle variant: $idleVariant")
                            node?.playAnimation(
                                animationIndex = animIndex,
                                loop = true,
                                speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
                            )
                            currentAnimationName = idleVariant
                            timeInState = 0f
                        } catch (e: Exception) {
                            println("⚠️ Failed to play idle variant: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }
        )
    }
}

/**
 * Map animation name to index in GLB file
 *
 * Colobus_Animations.glb contains all 18 animations.
 * This function maps animation names to their indices in the file.
 *
 * @param animationName Animation name (e.g., "Idle_A", "Run")
 * @return Animation index (0-17)
 */
private fun getAnimationIndex(animationName: String): Int {
    return when (animationName) {
        ColobusAnimations.IDLE_A -> 0
        ColobusAnimations.IDLE_B -> 1
        ColobusAnimations.IDLE_C -> 2
        ColobusAnimations.FEAR -> 3
        ColobusAnimations.BOUNCE -> 4
        ColobusAnimations.DEATH -> 5
        ColobusAnimations.CLICKED -> 6
        ColobusAnimations.EAT -> 7
        ColobusAnimations.SIT -> 8
        ColobusAnimations.WALK -> 9
        ColobusAnimations.RUN -> 10
        ColobusAnimations.FLY -> 11
        ColobusAnimations.SWIM -> 12
        ColobusAnimations.ATTACK -> 13
        ColobusAnimations.HIT -> 14
        ColobusAnimations.ROLL -> 15
        ColobusAnimations.SPIN -> 16
        ColobusAnimations.JUMP -> 17
        else -> 2  // Default to Idle_C
    }
}

/**
 * 3D avatar view compact variant
 *
 * Smaller 3D avatar for headers/toolbars (80dp).
 *
 * @param state Current avatar state
 * @param modifier Optional modifier
 */
@Composable
fun Avatar3DViewCompact(
    state: AvatarState,
    modifier: Modifier = Modifier
) {
    Avatar3DView(
        state = state,
        modifier = modifier.size(80.dp),
        autoRotate = false
    )
}

/**
 * Actual implementation of AvatarViewContent3D for Android
 *
 * Renders the 3D Colobus model using SceneView.
 * Called from commonMain AvatarView when use3D=true.
 *
 * @param state Avatar state to render
 */
@Composable
actual fun AvatarViewContent3D(state: AvatarState) {
    Avatar3DView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        autoRotate = false
    )
}

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun ChatScreenWith3DAvatar() {
 *     val avatarVM = rememberAvatarViewModel()
 *     val state by avatarVM.avatarState.collectAsState()
 *
 *     Column {
 *         // Full-size 3D avatar
 *         Avatar3DView(
 *             state = state,
 *             modifier = Modifier.size(280.dp),
 *             autoRotate = false
 *         )
 *
 *         // Compact 3D avatar for top bar
 *         Avatar3DViewCompact(
 *             state = state,
 *             modifier = Modifier.size(80.dp)
 *         )
 *     }
 * }
 *
 * @Composable
 * fun AvatarToggleExample() {
 *     var use3D by remember { mutableStateOf(true) }
 *     val state = AvatarState(emotion = AvatarEmotion.HAPPY)
 *
 *     if (use3D) {
 *         Avatar3DView(state = state)
 *     } else {
 *         AvatarView(state = state)  // 2D fallback
 *     }
 * }
 * ```
 */
