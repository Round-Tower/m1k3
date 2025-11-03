package app.m1k3.ai.assistant.avatar

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaTypography
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberCameraNode
import kotlinx.coroutines.delay

/**
 * 間 AI Avatar 3D View (Android) - GENERIC GLB LOADER
 *
 * **UPDATED:** Universal 3D model renderer with:
 * - Auto-centering camera (CameraAutoFit)
 * - Interactive controls (pinch-zoom, orbit, pan)
 * - Dynamic animation mapping (AnimationIntrospector)
 * - Multi-model support (ModelRegistry)
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
    val engine = rememberEngine()
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

    // Handle loading/error states
    Box(modifier = modifier.size(200.dp)) {
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
                        color = MaColors.TextSecondary
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
                        text = "⚠️ Error",
                        style = MaTypography.titleMedium,
                        color = MaColors.Error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error!!,
                        style = MaTypography.labelSmall,
                        color = MaColors.TextSecondary
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
    val engine = rememberEngine()
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

    // Create model node
    val childNodes = rememberNodes {
        add(
            ModelNode(
                modelInstance = modelLoader.createModelInstance(modelConfig.path),
                scaleToUnits = 1.0f
            ).apply {
                position = io.github.sceneview.math.Position(0f, 0f, 0f)
                println("✅ Static model loaded: ${modelConfig.name}")
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
            onFrame = { frameTimeNanos ->
                // Update camera
                cameraState.applyCameraNode(cameraNode)

                // Apply procedural animations
                val node = childNodes.firstOrNull() as? ModelNode
                if (node != null) {
                    val elapsedSeconds = (frameTimeNanos - startTime) / 1_000_000_000.0f

                    // Rotation (emotion-based speed)
                    val rotationY = animator.getRotationY(elapsedSeconds)
                    node.rotation = io.github.sceneview.math.Rotation(0f, rotationY, 0f)

                    // Scale pulse (activity-based)
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
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    // Calculate optimal camera (elevated 15° above model, looking down)
    val optimalCamera = remember(metadata) {
        CameraAutoFit.calculate(
            metadata = metadata,
            cameraAngle = 15f  // Elevate camera 15° above model
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
        println("🎯 [remember] Target animation computed: ${anim.name} (emotion=${state.emotion}, activity=${state.activity})")
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

    // Randomize starting animation for testing
    val randomStartingAnim = remember(metadata) {
        metadata.animations.random().also {
            println("🎲 Randomized starting animation: ${it.name} (index=${it.index})")
        }
    }

    // Create model node
    val childNodes = rememberNodes {
        add(
            ModelNode(
                modelInstance = modelLoader.createModelInstance(modelConfig.path),
                scaleToUnits = 1.0f  // Start at 1:1 scale (auto-fit handles sizing)
            ).apply {
                // Position at metadata center (should be origin for most models)
                position = io.github.sceneview.math.Position(0f, 0f, 0f)

                // Play initial animation (randomized for testing)
                try {
                    playAnimation(
                        animationIndex = randomStartingAnim.index,
                        loop = randomStartingAnim.isLoopable,
                        speed = Avatar3DEngine.getAnimationSpeed(state.intensity)
                    )
                    playbackInfo = playbackInfo.copy(
                        currentAnimIndex = randomStartingAnim.index,
                        currentAnimName = randomStartingAnim.name,
                        startTime = System.nanoTime()
                    )
                    println("✅ Initial animation: ${randomStartingAnim.name} (index=${randomStartingAnim.index})")
                } catch (e: Exception) {
                    println("⚠️ Failed to play animation '${randomStartingAnim.name}': ${e.message}")
                    e.printStackTrace()
                    // Fallback to first animation
                    try {
                        val fallback = metadata.animations.firstOrNull()
                        if (fallback != null) {
                            playAnimation(
                                animationIndex = fallback.index,
                                loop = fallback.isLoopable,
                                speed = 1.0f
                            )
                            playbackInfo = playbackInfo.copy(
                                currentAnimIndex = fallback.index,
                                currentAnimName = fallback.name,
                                startTime = System.nanoTime()
                            )
                        }
                    } catch (fallbackError: Exception) {
                        println("❌ Failed to play fallback animation: ${fallbackError.message}")
                    }
                }
            }
        )
    }

    // Handle user-directed animation changes
    // User tapping emotions/activities triggers immediate animation switch
    LaunchedEffect(targetAnimation) {
        if (playbackInfo.currentAnimName != targetAnimation.name) {
            println("🎭 [USER_ACTION] ${playbackInfo.currentAnimName} → ${targetAnimation.name} (emotion=${state.emotion}, activity=${state.activity})")

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
                println("🏠 [AUTO_IDLE] Returning to ${idleAnimation.name} (emotion=${state.emotion})")

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
                    println("🎲 [IDLE_VARIANT] Switching to ${idleVariant.name} for liveliness")

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
                        println("🔄 [IDLE_VARIANT] Returning to AUTO_IDLE")
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
            onFrame = { frameTimeNanos ->
                // Update camera from controller state
                cameraState.applyCameraNode(cameraNode)

                // CRITICAL FIX: Update animation on every frame
                val node = childNodes.firstOrNull() as? ModelNode
                val animator = node?.modelInstance?.animator

                if (animator != null && playbackInfo.startTime > 0L) {
                    // Calculate elapsed time and apply global speed scale
                    val realElapsedSeconds = playbackInfo.getElapsedSeconds(frameTimeNanos)
                    val elapsedSeconds = realElapsedSeconds * Avatar3DEngine.ANIMATION_SPEED_SCALE

                    // Get current animation metadata
                    val currentAnim = metadata.animations.getOrNull(playbackInfo.currentAnimIndex)

                    if (currentAnim != null) {
                        // Check if non-loopable animation completed → trigger auto-return
                        // Account for speed scale: slower animations take longer real time to complete
                        val scaledDuration = currentAnim.duration / Avatar3DEngine.ANIMATION_SPEED_SCALE
                        if (playbackInfo.playbackState == AnimationPlaybackState.USER_DIRECTED &&
                            !currentAnim.isLoopable &&
                            realElapsedSeconds >= scaledDuration) {
                            playbackInfo = playbackInfo.copy(
                                playbackState = AnimationPlaybackState.AUTO_IDLE
                            )
                            println("✅ [COMPLETION] ${currentAnim.name} finished → AUTO_IDLE")
                        }

                        // Calculate animation time with looping (using scaled time for smooth playback)
                        val animTime = if (currentAnim.isLoopable && currentAnim.duration > 0f) {
                            elapsedSeconds % currentAnim.duration
                        } else {
                            elapsedSeconds.coerceAtMost(currentAnim.duration)
                        }

                        // Apply animation
                        animator.applyAnimation(playbackInfo.currentAnimIndex, animTime)

                        // CRITICAL: Update bone matrices (makes animations actually render)
                        animator.updateBoneMatrices()
                    }
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
 *
 * @param state Avatar state to render
 */
@Composable
actual fun AvatarViewContent3D(state: AvatarState) {
    Avatar3DView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        modelConfig = ModelRegistry.getDefault(),
        enableInteraction = false,
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
 *         // Full-size 3D avatar with Colobus (default)
 *         Avatar3DView(
 *             state = state,
 *             modifier = Modifier.size(280.dp),
 *             enableInteraction = true
 *         )
 *
 *         // Use different model (Sparrow)
 *         Avatar3DView(
 *             state = state,
 *             modifier = Modifier.size(280.dp),
 *             modelConfig = ModelRegistry.getById("sparrow")!!,
 *             enableInteraction = true
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
 * fun ModelSelectorExample() {
 *     var selectedModel by remember { mutableStateOf(ModelRegistry.getDefault()) }
 *     val state = AvatarState(emotion = AvatarEmotion.HAPPY)
 *
 *     Column {
 *         // Dropdown to select model
 *         ExposedDropdownMenuBox(...) {
 *             ModelRegistry.allModels.forEach { model ->
 *                 DropdownMenuItem(
 *                     text = { Text(model.name) },
 *                     onClick = { selectedModel = model }
 *                 )
 *             }
 *         }
 *
 *         // Render selected model
 *         Avatar3DView(
 *             state = state,
 *             modelConfig = selectedModel,
 *             enableInteraction = true
 *         )
 *     }
 * }
 *
 * @Composable
 * fun InteractiveAvatarExample() {
 *     val state = AvatarState(emotion = AvatarEmotion.EXCITED)
 *
 *     // Interactive 3D avatar with touch controls:
 *     // - Drag to orbit
 *     // - Pinch to zoom
 *     // - Double-tap to reset
 *     Avatar3DView(
 *         state = state,
 *         modifier = Modifier.fillMaxSize(),
 *         modelConfig = ModelRegistry.getById("gecko")!!,
 *         enableInteraction = true
 *     )
 * }
 * ```
 */
