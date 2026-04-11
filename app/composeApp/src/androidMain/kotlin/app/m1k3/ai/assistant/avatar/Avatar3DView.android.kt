package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.utils.Logger
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberCameraNode
import kotlinx.coroutines.delay

// Logger instance for Avatar3D
private val logger = Logger.withTag("Avatar3D")

/**
 * Avatar 3D View (Android) - GENERIC GLB LOADER
 *
 * **UPDATED:** Universal 3D model renderer with:
 * - Auto-centering camera (CameraAutoFit)
 * - Interactive controls (pinch-zoom, orbit, pan)
 * - Dynamic animation mapping (AnimationIntrospector)
 * - Multi-model support (ModelRegistry)
 * - Reference-counted Filament engine (prevents multi-screen crashes)
 *
 * Renders any GLB/GLTF model with intelligent defaults.
 * Supports emotion-based animations and real-time state updates.
 *
 * Architecture:
 * - SceneView (Filament wrapper for Compose)
 * - GLBModelLoader (metadata extraction)
 * - CameraAutoFit (optimal camera positioning)
 * - InteractiveCameraController (touch gestures)
 * - AnimationIntrospector (fuzzy animation matching)
 * - FilamentEngineManager (reference-counted engine lifecycle)
 *
 * **Multi-Screen Crash Fix:**
 * - ✅ FIXED: Reference-counted engine manager prevents "Engine destroyed × 2" crashes
 * - Manual engine lifecycle (not rememberEngine()) with acquire/release pattern
 * - Engine survives screen navigation, destroyed only in MainActivity.onDestroy()
 * - Each 3D view calls AcquireEngine() to increment ref count
 * - Safe for multiple screens with 3D avatars (MainActivity + ChatScreen)
 */

/**
 * Generic 3D avatar view
 *
 * Renders any GLB model with automatic camera fitting and animation.
 *
 * @param state Current avatar state (emotion + activity)
 * @param modifier Optional modifier
 * @param modelConfig Model configuration (default: Colobus)
 * @param enableInteraction Whether to enable touch controls
 * @param autoRotate Whether to slowly rotate the model
 * @param showLoadingIndicator Whether to show loading spinner
 */
@Composable
fun Avatar3DView(
    state: AvatarState,
    modifier: Modifier = Modifier,
    modelConfig: ModelConfig = ModelRegistry.getDefault(),
    enableInteraction: Boolean = false,
    autoRotate: Boolean = false,
    showLoadingIndicator: Boolean = true
) {
    // Use shared engine from CompositionLocal (one engine for entire app)
    val engine = LocalSharedEngine.current!!

    // Acquire engine reference (increment ref count, auto-release on dispose)
    AcquireEngine()

    val modelLoader = rememberModelLoader(engine)

    // Load model metadata
    var metadata by remember(modelConfig) { mutableStateOf<ModelMetadata?>(null) }
    var isLoading by remember(modelConfig) { mutableStateOf(true) }
    var error by remember(modelConfig) { mutableStateOf<String?>(null) }

    LaunchedEffect(modelConfig) {
        isLoading = true
        error = null
        try {
            metadata = GLBModelLoader.loadMetadata(modelLoader, modelConfig.path)
            isLoading = false
        } catch (e: Exception) {
            error = "Failed to load model: ${e.message}"
            isLoading = false
        }
    }

    // Handle loading/error states — no hardcoded size, parent controls layout
    Box(modifier = modifier) {
        when {
            isLoading && showLoadingIndicator -> {
                // Loading state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaColors.Orange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading ${modelConfig.name}...",
                        style = MaTypography.labelSmall,
                        color = MaColors.textSecondary()
                    )
                }
            }

            error != null -> {
                // Error state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error",
                        style = MaTypography.titleMedium,
                        color = MaColors.Error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error!!,
                        style = MaTypography.labelSmall,
                        color = MaColors.textSecondary()
                    )
                }
            }

            metadata != null -> {
                // Render 3D model
                Avatar3DViewContent(
                    state = state,
                    metadata = metadata!!,
                    modelConfig = modelConfig,
                    enableInteraction = enableInteraction,
                    autoRotate = autoRotate,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 3D avatar view content (actual rendering)
 *
 * Separated from Avatar3DView for cleaner loading state handling.
 * Branches to static or animated rendering based on model type.
 */
@Composable
private fun Avatar3DViewContent(
    state: AvatarState,
    metadata: ModelMetadata,
    modelConfig: ModelConfig,
    enableInteraction: Boolean,
    autoRotate: Boolean,
    modifier: Modifier = Modifier
) {
    // Detect model type
    val isStaticModel = modelConfig.modelType == ModelType.STATIC || metadata.animations.isEmpty()

    if (isStaticModel) {
        // Render static model with procedural animations (mask)
        RenderStaticModel(
            state = state,
            metadata = metadata,
            modelConfig = modelConfig,
            enableInteraction = enableInteraction,
            modifier = modifier
        )
    } else {
        // Render animated model with skeleton animations (animals)
        RenderAnimatedModel(
            state = state,
            metadata = metadata,
            modelConfig = modelConfig,
            enableInteraction = enableInteraction,
            autoRotate = autoRotate,
            modifier = modifier
        )
    }
}

/**
 * Render static model with procedural animations
 *
 * For models without skeleton/bones (e.g., Mask).
 * Uses ProceduralAnimator for code-based animations:
 * - Rotation (emotion-based speed)
 * - Scale pulse (activity-based)
 * - Color tint (emotion-based)
 */
@Composable
private fun RenderStaticModel(
    state: AvatarState,
    metadata: ModelMetadata,
    modelConfig: ModelConfig,
    enableInteraction: Boolean,
    modifier: Modifier = Modifier
) {
    // Use shared engine from CompositionLocal (one engine for entire app)
    val engine = LocalSharedEngine.current!!
    val modelLoader = rememberModelLoader(engine)

    // Create procedural animator
    val animator = remember(state.emotion, state.activity) {
        ProceduralAnimator(state)
    }

    // Calculate optimal camera
    val optimalCamera = remember(metadata) {
        CameraAutoFit.calculate(
            metadata = metadata,
            cameraAngle = 15f
        )
    }

    val cameraState = rememberCameraControllerState(optimalCamera)
    val cameraNode = rememberCameraNode(engine).apply {
        cameraState.applyCameraNode(this)
    }

    // Track animation start time
    var startTime by remember { mutableLongStateOf(System.nanoTime()) }

    // Reset start time when state changes
    LaunchedEffect(state) {
        startTime = System.nanoTime()
    }

    // Emotion-triggered head turn animation
    val initialRotation = remember { animator.getEmotionAngle() }
    var previousEmotion by remember { mutableStateOf(state.emotion) }
    var currentRotation by remember { mutableFloatStateOf(initialRotation) }
    var targetRotation by remember { mutableFloatStateOf(initialRotation) }
    var rotationAnimProgress by remember { mutableFloatStateOf(1f) }

    // Trigger head turn when emotion changes
    LaunchedEffect(state.emotion) {
        if (state.emotion != previousEmotion) {
            targetRotation = animator.getEmotionAngle()
            rotationAnimProgress = 0f
            previousEmotion = state.emotion

            logger.d { "Emotion changed: ${state.emotion}, rotating from ${currentRotation.toInt()}° → ${targetRotation.toInt()}°" }

            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            ) { value, _ ->
                rotationAnimProgress = value
            }
        }
    }

    // Create model node
    val childNodes = rememberNodes {
        add(
            ModelNode(
                modelInstance = modelLoader.createModelInstance(modelConfig.path),
                scaleToUnits = 1.0f
            ).apply {
                position = io.github.sceneview.math.Position(0f, 0f, 0f)
                logger.i { "Static model loaded: ${modelConfig.name}" }
            }
        )
    }

    Box(
        modifier = modifier
            .then(
                if (enableInteraction) {
                    Modifier.interactiveCamera(
                        state = cameraState,
                        enabled = true
                    )
                } else Modifier
            )
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes,
            isOpaque = false,
            onFrame = { frameTimeNanos ->
                // Update camera
                cameraState.applyCameraNode(cameraNode)

                // Apply procedural animations
                val node = childNodes.firstOrNull() as? ModelNode
                if (node != null) {
                    val elapsedSeconds = (frameTimeNanos - startTime) / 1_000_000_000.0f

                    // Emotion-triggered head turn (smooth rotation when emotion changes)
                    if (rotationAnimProgress < 1f) {
                        val startAngle = currentRotation
                        currentRotation = startAngle + (targetRotation - startAngle) * rotationAnimProgress
                    }
                    node.rotation = io.github.sceneview.math.Rotation(0f, currentRotation, 0f)

                    // Scale pulse (activity-based breathing)
                    val scale = animator.getScale(elapsedSeconds)
                    node.scale = io.github.sceneview.math.Scale(scale, scale, scale)

                    // TODO: Color tint (if material API available)
                    // val colorTint = animator.getColorTintWithActivity()
                    // node.materialInstance?.setParameter("baseColorTint", colorTint)
                }
            }
        )
    }
}

/**
 * Render animated model with skeleton animations
 *
 * For models with baked animations (e.g., Quirky Series animals).
 * Uses animation state machine for lifelike behavior.
 */
@Composable
private fun RenderAnimatedModel(
    state: AvatarState,
    metadata: ModelMetadata,
    modelConfig: ModelConfig,
    enableInteraction: Boolean,
    autoRotate: Boolean,
    modifier: Modifier = Modifier
) {
    // Use shared engine from CompositionLocal (one engine for entire app)
    val engine = LocalSharedEngine.current!!
    val modelLoader = rememberModelLoader(engine)

    // Calculate optimal camera using UNIT bounding box — scaleToUnits=1.0 normalizes all
    // models to ~1m, so camera distance must be consistent regardless of native geometry.
    // Without this, small-bbox models clip and large-bbox models appear tiny.
    val normalizedMetadata = remember(metadata) {
        metadata.copy(boundingBox = BoundingBox.UNIT)
    }
    val optimalCamera = remember(normalizedMetadata) {
        CameraAutoFit.calculate(
            metadata = normalizedMetadata,
            cameraAngle = 18f,      // Slightly overhead — "pet on desk" perspective
            focusOffset = 0f,       // centerOrigin handles vertical placement
            horizontalAngle = -20f, // Gentle 3/4 view
            padding = 2.0f         // Extra room for animation limb extension
        )
    }

    // Camera controller state
    val cameraState = rememberCameraControllerState(optimalCamera)

    // Create camera node
    val cameraNode = rememberCameraNode(engine).apply {
        // Apply initial camera position
        cameraState.applyCameraNode(this)
    }

    // Get target animation when state changes
    // FIXED: Use remember with keys instead of derivedStateOf
    // derivedStateOf only tracks Compose State objects, but 'state' is a regular parameter
    val targetAnimation = remember(state.emotion, state.activity, metadata.animations) {
        val anim = Avatar3DEngine.getAnimation(state, metadata.animations)
        logger.v { "Target animation computed: ${anim.name} (emotion=${state.emotion}, activity=${state.activity})" }
        anim
    }

    // Track animation playback with state machine
    var playbackInfo by remember {
        mutableStateOf(
            AnimationPlaybackInfo(
                currentAnimIndex = targetAnimation.index,
                currentAnimName = targetAnimation.name,
                playbackState = AnimationPlaybackState.USER_DIRECTED,
                startTime = System.nanoTime(),
                trigger = AnimationTrigger.INIT
            )
        )
    }
    var timeInState by remember { mutableFloatStateOf(0f) }

    // Track time in current state for idle variants
    LaunchedEffect(state) {
        timeInState = 0f
        while (true) {
            delay(100)  // Update every 100ms
            timeInState += 0.1f
        }
    }

    // Subtle continuous Y rotation — lazy susan effect (8°/sec, ~45s per revolution)
    // Captured from first frame to align clock source with Filament's frameTimeNanos
    var rotationStartTime by remember { mutableLongStateOf(-1L) }

    // Disposed guard — prevents onFrame from touching destroyed Filament entities
    // during avatar model switches (render thread races compose thread).
    // AtomicBoolean (not mutableStateOf) for cross-thread visibility:
    // Compose thread writes in onDispose, Filament render thread reads in onFrame.
    val isDisposed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    DisposableEffect(modelConfig) {
        isDisposed.set(false)
        onDispose { isDisposed.set(true) }
    }

    // Start with a calm, emotion-appropriate idle (not random — random felt aggressive)
    val startingAnim = remember(metadata) {
        AnimationStateManager.getEmotionAppropriateIdle(
            emotion = state.emotion,
            availableAnimations = metadata.animations
        ).also {
            logger.d { "Starting with idle animation: ${it.name} (index=${it.index})" }
        }
    }

    // Create model node
    val childNodes = rememberNodes {
        add(
            ModelNode(
                modelInstance = modelLoader.createModelInstance(modelConfig.path),
                scaleToUnits = 1.0f,
                // Center model bounding box at origin — fixes models with offset
                // geometry (e.g. dinosaurs authored at non-zero positions).
                centerOrigin = io.github.sceneview.math.Position(0f, 0f, 0f)
            ).apply {
                position = io.github.sceneview.math.Position(0f, 0f, 0f)

                // Play initial idle animation (emotion-appropriate)
                // Clamp to actual model animation count — metadata may assume more
                // animations than the GLB actually contains (e.g. Quaternius models)
                val safeIndex = if (startingAnim.index < animationCount) {
                    startingAnim.index
                } else {
                    0.coerceAtMost(animationCount - 1)
                }
                try {
                    if (animationCount > 0) {
                        playAnimation(
                            animationIndex = safeIndex,
                            loop = true,
                            speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
                        )
                    }
                    playbackInfo = playbackInfo.copy(
                        currentAnimIndex = safeIndex,
                        currentAnimName = startingAnim.name,
                        startTime = System.nanoTime()
                    )
                    logger.i { "Initial animation: ${startingAnim.name} (index=$safeIndex, actual count=$animationCount)" }
                } catch (e: Exception) {
                    logger.w(e) { "Failed to play initial animation" }
                }
            }
        )
    }

    // Handle user-directed animation changes
    // User tapping emotions/activities triggers immediate animation switch
    LaunchedEffect(targetAnimation) {
        if (playbackInfo.currentAnimName != targetAnimation.name) {
            logger.d { "[USER_ACTION] ${playbackInfo.currentAnimName} → ${targetAnimation.name} (emotion=${state.emotion}, activity=${state.activity})" }

            playbackInfo = AnimationPlaybackInfo(
                currentAnimIndex = targetAnimation.index,
                currentAnimName = targetAnimation.name,
                playbackState = AnimationPlaybackState.USER_DIRECTED,
                startTime = System.nanoTime(),
                trigger = AnimationTrigger.USER_ACTION,
                previousAnimName = playbackInfo.currentAnimName
            )
        }
    }

    // Auto-return to idle after non-loopable animations complete
    // Triggered when playbackState transitions to AUTO_IDLE
    LaunchedEffect(playbackInfo.playbackState) {
        if (playbackInfo.playbackState == AnimationPlaybackState.AUTO_IDLE) {
            // Select emotion-appropriate idle animation
            val idleAnimation = AnimationStateManager.getEmotionAppropriateIdle(
                emotion = state.emotion,
                availableAnimations = metadata.animations
            )

            // Only transition if we're not already playing this idle
            if (playbackInfo.currentAnimName != idleAnimation.name) {
                logger.d { "[AUTO_IDLE] Returning to ${idleAnimation.name} (emotion=${state.emotion})" }

                playbackInfo = AnimationPlaybackInfo(
                    currentAnimIndex = idleAnimation.index,
                    currentAnimName = idleAnimation.name,
                    playbackState = AnimationPlaybackState.AUTO_IDLE,
                    startTime = System.nanoTime(),
                    trigger = AnimationTrigger.COMPLETION,
                    previousAnimName = playbackInfo.currentAnimName
                )
            }
        }
    }

    // Idle variation system - makes avatar feel alive
    // Randomly cycles through idle animations every 10-15 seconds
    LaunchedEffect(playbackInfo.playbackState, state.emotion) {
        while (playbackInfo.playbackState == AnimationPlaybackState.AUTO_IDLE) {
            // Random delay between 10-15 seconds
            val delayMs = (10_000..15_000).random()
            delay(delayMs.toLong())

            // Still in AUTO_IDLE? Trigger idle variation
            if (playbackInfo.playbackState == AnimationPlaybackState.AUTO_IDLE) {
                val idleVariant = AnimationStateManager.getRandomIdleVariant(
                    currentAnimName = playbackInfo.currentAnimName,
                    availableAnimations = metadata.animations
                )

                if (idleVariant != null) {
                    logger.d { "[IDLE_VARIANT] Switching to ${idleVariant.name} for liveliness" }

                    playbackInfo = AnimationPlaybackInfo(
                        currentAnimIndex = idleVariant.index,
                        currentAnimName = idleVariant.name,
                        playbackState = AnimationPlaybackState.IDLE_VARIANT,
                        startTime = System.nanoTime(),
                        trigger = AnimationTrigger.IDLE_TIMEOUT,
                        previousAnimName = playbackInfo.currentAnimName
                    )

                    // Play variant for 2 seconds, then return to AUTO_IDLE
                    delay(2000)
                    if (playbackInfo.playbackState == AnimationPlaybackState.IDLE_VARIANT) {
                        playbackInfo = playbackInfo.copy(
                            playbackState = AnimationPlaybackState.AUTO_IDLE,
                            startTime = System.nanoTime()
                        )
                        logger.d { "[IDLE_VARIANT] Returning to AUTO_IDLE" }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (enableInteraction) {
                    Modifier.interactiveCamera(
                        state = cameraState,
                        enabled = true
                    )
                } else Modifier
            )
    ) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes,
            isOpaque = false,
            onFrame = { frameTimeNanos ->
                // Bail if model is being disposed (avatar switch race condition)
                if (isDisposed.get()) return@Scene

                // Update camera from controller state
                cameraState.applyCameraNode(cameraNode)

                // Try-catch: safety net for TOCTOU race between isDisposed check
                // and Filament native calls. If model is destroyed between those
                // two points, we absorb rather than SIGSEGV.
                try {
                    val node = childNodes.firstOrNull() as? ModelNode

                    // Gentle ±15° Y oscillation — animal "looking around" effect
                    // Sine wave: 15° amplitude, ~8 second full cycle (0.125 Hz)
                    if (autoRotate && node != null) {
                        if (rotationStartTime < 0L) rotationStartTime = frameTimeNanos
                        val rotElapsed = (frameTimeNanos - rotationStartTime) / 1_000_000_000.0f
                        val rotationY = 15f * kotlin.math.sin(rotElapsed * 0.125f * 2f * kotlin.math.PI.toFloat())
                        node.rotation = io.github.sceneview.math.Rotation(0f, rotationY, 0f)
                    }

                    val animator = node?.modelInstance?.animator
                    val actualAnimCount = node?.animationCount ?: 0

                    if (animator != null && playbackInfo.startTime > 0L) {
                        val realElapsedSeconds = playbackInfo.getElapsedSeconds(frameTimeNanos)
                        val elapsedSeconds = realElapsedSeconds * Avatar3DEngine.ANIMATION_SPEED_SCALE
                        val currentAnim = metadata.animations.getOrNull(playbackInfo.currentAnimIndex)

                        // Guard: metadata animation index must be within actual model's range
                        if (currentAnim != null && playbackInfo.currentAnimIndex < actualAnimCount) {
                            val scaledDuration = currentAnim.duration / Avatar3DEngine.ANIMATION_SPEED_SCALE
                            if (playbackInfo.playbackState == AnimationPlaybackState.USER_DIRECTED &&
                                !currentAnim.isLoopable &&
                                realElapsedSeconds >= scaledDuration) {
                                playbackInfo = playbackInfo.copy(
                                    playbackState = AnimationPlaybackState.AUTO_IDLE
                                )
                                logger.d { "[COMPLETION] ${currentAnim.name} finished → AUTO_IDLE" }
                            }

                            val animTime = if (currentAnim.isLoopable && currentAnim.duration > 0f) {
                                elapsedSeconds % currentAnim.duration
                            } else {
                                elapsedSeconds.coerceAtMost(currentAnim.duration)
                            }

                            animator.applyAnimation(playbackInfo.currentAnimIndex, animTime)
                            animator.updateBoneMatrices()
                        } else if (currentAnim != null && actualAnimCount > 0) {
                            animator.applyAnimation(0, elapsedSeconds % 2.0f)
                            animator.updateBoneMatrices()
                        }
                    }
                } catch (_: Exception) {
                    // Absorbed: Filament entity destroyed during avatar model switch.
                    // Expected during the brief TOCTOU window — not a bug.
                }
            }
        )
    }
}

/**
 * 3D avatar view compact variant
 *
 * Smaller 3D avatar for headers/toolbars (80dp).
 *
 * @param state Current avatar state
 * @param modifier Optional modifier
 * @param modelConfig Model configuration (default: Colobus)
 */
@Composable
fun Avatar3DViewCompact(
    state: AvatarState,
    modifier: Modifier = Modifier,
    modelConfig: ModelConfig = ModelRegistry.getDefault()
) {
    Avatar3DView(
        state = state,
        modifier = modifier.size(80.dp),
        modelConfig = modelConfig,
        enableInteraction = false,
        autoRotate = false,
        showLoadingIndicator = false
    )
}

/**
 * Actual implementation of AvatarViewContent3D for Android
 *
 * Renders the 3D model using SceneView with generic GLB loader.
 * Called from commonMain AvatarView when use3D=true.
 * Reads the user's selected avatar from preferences.
 *
 * @param state Avatar state to render
 */
@Composable
actual fun AvatarViewContent3D(state: AvatarState) {
    val modelId by LocalSelectedAvatarId.current
    val modelConfig = ModelRegistry.getById(modelId) ?: ModelRegistry.getDefault()
    android.util.Log.d("Avatar3D", "Rendering model: id=$modelId config=${modelConfig.id} path=${modelConfig.path}")

    // Cel-shading + pixelation post-process (API 33+, no-op on older)
    CelShaderEffect(
        pixelSize = 4f,     // Subtle pixel grid (increase for chunkier retro feel)
        colorLevels = 6f,   // 6 color bands per channel (lower = more cartoony)
        modifier = Modifier.fillMaxSize()
    ) {
        Avatar3DView(
            state = state,
            modifier = Modifier.fillMaxSize(),
            modelConfig = modelConfig,
            enableInteraction = false,
            autoRotate = true
        )
    }
}
