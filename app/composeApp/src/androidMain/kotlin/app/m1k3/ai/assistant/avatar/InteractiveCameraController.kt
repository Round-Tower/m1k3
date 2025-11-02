package app.m1k3.ai.assistant.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import io.github.sceneview.node.CameraNode
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * 間 AI Interactive Camera Controller (Android)
 *
 * Touch-based camera manipulation for 3D model viewing.
 * Provides intuitive gestures for zooming, orbiting, and panning.
 *
 * Gestures:
 * - Single finger drag → Orbit around model
 * - Pinch → Zoom in/out
 * - Two-finger drag → Pan camera
 * - Double-tap → Reset to optimal position
 *
 * Features:
 * - Smooth spring animations
 * - Min/max zoom constraints
 * - Orbit limits (prevent flipping)
 * - Inertia/momentum for natural feel
 */

/**
 * Camera controller state
 *
 * Mutable state for camera transformations.
 *
 * @param initialConfig Initial camera configuration
 * @param minDistance Minimum zoom distance
 * @param maxDistance Maximum zoom distance
 */
@Stable
class CameraControllerState(
    initialConfig: CameraConfig,
    val minDistance: Float = initialConfig.distance * 0.5f,
    val maxDistance: Float = initialConfig.distance * 3.0f
) {
    /**
     * Current camera distance from lookAt point
     */
    var distance by mutableFloatStateOf(initialConfig.distance)
        private set

    /**
     * Horizontal orbit angle (degrees)
     * 0° = front, 90° = right, 180° = back, 270° = left
     */
    var horizontalAngle by mutableFloatStateOf(0f)
        private set

    /**
     * Vertical orbit angle (degrees)
     * 0° = level, +90° = top, -90° = bottom
     */
    var verticalAngle by mutableFloatStateOf(0f)
        private set

    /**
     * Pan offset (XY translation)
     */
    var panOffset by mutableStateOf(Offset.Zero)
        private set

    /**
     * LookAt point (model center)
     */
    var lookAt by mutableStateOf(initialConfig.lookAt)
        private set

    /**
     * Initial configuration (for reset)
     */
    private var initialConfig by mutableStateOf(initialConfig)

    /**
     * Update distance (zoom)
     *
     * @param newDistance New distance (clamped to min/max)
     */
    fun updateDistance(newDistance: Float) {
        distance = newDistance.coerceIn(minDistance, maxDistance)
    }

    /**
     * Update orbit angles
     *
     * @param deltaH Horizontal angle delta (degrees)
     * @param deltaV Vertical angle delta (degrees)
     */
    fun updateOrbit(deltaH: Float, deltaV: Float) {
        horizontalAngle = (horizontalAngle + deltaH) % 360f
        verticalAngle = (verticalAngle + deltaV).coerceIn(-85f, 85f)  // Prevent flipping
    }

    /**
     * Update pan offset
     *
     * @param delta Pan delta
     */
    fun updatePan(delta: Offset) {
        panOffset += delta
    }

    /**
     * Reset to initial configuration
     */
    fun reset() {
        distance = initialConfig.distance
        horizontalAngle = 0f
        verticalAngle = 0f
        panOffset = Offset.Zero
        lookAt = initialConfig.lookAt
    }

    /**
     * Calculate current camera position from state
     *
     * @return Camera position in world space
     */
    fun calculatePosition(): Position3D {
        // Convert angles to radians
        val hRad = Math.toRadians(horizontalAngle.toDouble())
        val vRad = Math.toRadians(verticalAngle.toDouble())

        // Calculate position on sphere
        val x = (distance * sin(hRad) * cos(vRad)).toFloat()
        val y = (distance * sin(vRad)).toFloat()
        val z = (distance * cos(hRad) * cos(vRad)).toFloat()

        // Apply pan offset (in camera space)
        val panScale = distance * 0.001f  // Scale pan by distance
        val panX = panOffset.x * panScale
        val panY = -panOffset.y * panScale  // Invert Y for intuitive controls

        return Position3D(
            x = lookAt.x + x + panX,
            y = lookAt.y + y + panY,
            z = lookAt.z + z
        )
    }

    /**
     * Apply state to CameraNode
     *
     * @param cameraNode SceneView camera node
     */
    fun applyCameraNode(cameraNode: CameraNode) {
        val position = calculatePosition()
        cameraNode.position = position.toSceneViewPosition()
        cameraNode.lookAt(lookAt.toSceneViewPosition())
    }
}

/**
 * Remember camera controller state
 *
 * @param initialConfig Initial camera configuration
 * @param minDistance Minimum zoom distance (default: 50% of initial)
 * @param maxDistance Maximum zoom distance (default: 300% of initial)
 * @return Camera controller state
 */
@Composable
fun rememberCameraControllerState(
    initialConfig: CameraConfig,
    minDistance: Float = initialConfig.distance * 0.5f,
    maxDistance: Float = initialConfig.distance * 3.0f
): CameraControllerState {
    return remember(initialConfig) {
        CameraControllerState(
            initialConfig = initialConfig,
            minDistance = minDistance,
            maxDistance = maxDistance
        )
    }
}

/**
 * Interactive camera controller modifier
 *
 * Adds touch gesture handling to enable camera control.
 *
 * @param state Camera controller state
 * @param enabled Whether gestures are enabled
 * @param onDoubleTap Optional double-tap handler (default: reset camera)
 * @return Modifier with gesture handling
 */
fun Modifier.interactiveCamera(
    state: CameraControllerState,
    enabled: Boolean = true,
    onDoubleTap: (() -> Unit)? = null
): Modifier {
    if (!enabled) return this

    return this.pointerInput(state) {
        // Handle touch gestures
        detectTransformGestures(
            onGesture = { centroid, pan, zoom, rotation ->
                // Zoom (pinch)
                if (zoom != 1f) {
                    val newDistance = state.distance / zoom
                    state.updateDistance(newDistance)
                }

                // Orbit (single finger drag) or Pan (two-finger drag)
                if (pan != Offset.Zero) {
                    // Heuristic: if zoom is changing, it's a pinch → pan
                    // Otherwise, it's a single finger drag → orbit
                    if (zoom != 1f) {
                        // Two-finger pan
                        state.updatePan(pan * 0.5f)
                    } else {
                        // Single-finger orbit
                        val sensitivity = 0.3f
                        state.updateOrbit(
                            deltaH = -pan.x * sensitivity,
                            deltaV = pan.y * sensitivity
                        )
                    }
                }
            }
        )
    }.pointerInput(state) {
        // Handle double-tap
        detectTapGestures(
            onDoubleTap = {
                onDoubleTap?.invoke() ?: state.reset()
            }
        )
    }
}

/**
 * Animated camera reset
 *
 * Smoothly animates camera back to initial position.
 *
 * @param state Camera controller state
 * @param duration Animation duration in milliseconds
 */
@Composable
fun rememberAnimatedCameraReset(
    state: CameraControllerState,
    duration: Int = 500
): () -> Unit {
    val coroutineScope = rememberCoroutineScope()

    return remember(state) {
        {
            coroutineScope.launch {
                // Animate distance
                val startDistance = state.distance
                val targetDistance = state.minDistance * 2f  // Reset distance

                // Animate angles
                val startH = state.horizontalAngle
                val startV = state.verticalAngle

                // Animate pan
                val startPan = state.panOffset

                // Use AnimationSpec for smooth easing
                val animationSpec: AnimationSpec<Float> = tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing
                )

                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = animationSpec
                ) { progress, _ ->
                    // Lerp distance
                    state.updateDistance(
                        lerp(startDistance, targetDistance, progress)
                    )

                    // Lerp angles
                    val currentH = lerp(startH, 0f, progress)
                    val currentV = lerp(startV, 0f, progress)
                    state.updateOrbit(
                        deltaH = currentH - state.horizontalAngle,
                        deltaV = currentV - state.verticalAngle
                    )

                    // Lerp pan
                    state.updatePan(
                        Offset(
                            x = lerp(startPan.x, 0f, progress) - state.panOffset.x,
                            y = lerp(startPan.y, 0f, progress) - state.panOffset.y
                        )
                    )
                }
            }
        }
    }
}

/**
 * Linear interpolation helper
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun InteractiveAvatarView(metadata: ModelMetadata) {
 *     val engine = rememberEngine()
 *     val modelLoader = rememberModelLoader(engine)
 *
 *     // Calculate optimal camera
 *     val optimalCamera = remember(metadata) {
 *         CameraAutoFit.calculate(metadata)
 *     }
 *
 *     // Create camera controller state
 *     val cameraState = rememberCameraControllerState(optimalCamera)
 *
 *     // Create camera node
 *     val cameraNode = rememberCameraNode(engine).apply {
 *         // Apply initial position
 *         cameraState.applyCameraNode(this)
 *     }
 *
 *     // Animated reset function
 *     val resetCamera = rememberAnimatedCameraReset(cameraState)
 *
 *     Box(
 *         modifier = Modifier
 *             .fillMaxSize()
 *             .interactiveCamera(
 *                 state = cameraState,
 *                 enabled = true,
 *                 onDoubleTap = resetCamera
 *             )
 *     ) {
 *         Scene(
 *             engine = engine,
 *             modelLoader = modelLoader,
 *             cameraNode = cameraNode,
 *             onFrame = {
 *                 // Update camera every frame
 *                 cameraState.applyCameraNode(cameraNode)
 *             }
 *         ) {
 *             // Model node...
 *         }
 *
 *         // Reset button
 *         FloatingActionButton(
 *             onClick = resetCamera,
 *             modifier = Modifier.align(Alignment.BottomEnd)
 *         ) {
 *             Icon(Icons.Default.Refresh, "Reset Camera")
 *         }
 *     }
 * }
 * ```
 */
