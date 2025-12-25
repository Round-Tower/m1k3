package app.m1k3.ai.assistant.avatar

import kotlin.math.tan

/**
 * 間 AI Camera Auto-Fit System
 *
 * Calculates optimal camera positioning for any 3D model.
 * Uses bounding box analysis to ensure the entire model is visible
 * with comfortable padding, regardless of model dimensions.
 *
 * Algorithm:
 * 1. Calculate bounding sphere radius from model bounds
 * 2. Compute camera distance using FOV and sphere radius
 * 3. Position camera at calculated distance, looking at model center
 * 4. Add padding factor for comfortable viewing (1.5x default)
 *
 * Handles edge cases:
 * - Tall models (Sparrow flying) → increase distance
 * - Wide models (Herring swimming) → adjust for aspect ratio
 * - Small models → prevent extreme close-ups
 * - Large models → prevent far distant views
 */

/**
 * Camera auto-fit calculator
 *
 * Stateless utility for computing optimal camera configurations.
 */
object CameraAutoFit {

    /**
     * Default field of view in degrees
     * Standard perspective FOV for 3D viewers
     */
    const val DEFAULT_FOV = 45f

    /**
     * Default padding multiplier
     * 1.5x = 50% extra space around model
     */
    const val DEFAULT_PADDING = 1.5f

    /**
     * Minimum camera distance (prevents extreme close-ups)
     */
    const val MIN_DISTANCE = 1.5f

    /**
     * Maximum camera distance (prevents far distant views)
     */
    const val MAX_DISTANCE = 20f

    /**
     * Calculate optimal camera configuration for model
     *
     * Main API for auto-centering. Computes camera position, lookAt,
     * and distance to ensure entire model is visible.
     *
     * @param metadata Model metadata with bounding box
     * @param fov Field of view in degrees (default: 45°)
     * @param padding Padding multiplier (default: 1.5x)
     * @param cameraAngle Vertical angle offset in degrees (default: 0° = level with center)
     * @param focusOffset Vertical focus offset as fraction of height (default: 0.0 = center, 0.3 = face area)
     * @param horizontalAngle Horizontal rotation in degrees (default: 0° = front view, -25° = 3/4 left view)
     * @return Optimal camera configuration
     */
    fun calculate(
        metadata: ModelMetadata,
        fov: Float = DEFAULT_FOV,
        padding: Float = DEFAULT_PADDING,
        cameraAngle: Float = 0f,
        focusOffset: Float = 0.0f,
        horizontalAngle: Float = 0f
    ): CameraConfig {
        // Calculate focus point (with optional vertical offset for face/forehead focus)
        val baseCenter = metadata.center
        val verticalOffset = metadata.boundingBox.height * focusOffset
        val focusPoint = Position3D(
            x = baseCenter.x,
            y = baseCenter.y + verticalOffset,
            z = baseCenter.z
        )

        // Calculate required distance to fit model in view
        val distance = calculateDistance(
            boundingBox = metadata.boundingBox,
            fov = fov,
            padding = padding
        )

        // Calculate camera position with optional vertical and horizontal angles
        val position = calculateCameraPosition(
            center = focusPoint,
            distance = distance,
            verticalAngle = cameraAngle,
            horizontalAngle = horizontalAngle
        )

        return CameraConfig(
            position = position,
            lookAt = focusPoint,  // Camera looks at focus point (face area if offset > 0)
            distance = distance,
            fov = fov
        )
    }

    /**
     * Calculate camera distance to fit bounding box in view
     *
     * Uses trigonometry: distance = radius / tan(FOV/2)
     * Adds padding for comfortable viewing.
     *
     * @param boundingBox Model bounding box
     * @param fov Field of view in degrees
     * @param padding Padding multiplier (1.0 = tight fit, 1.5 = comfortable)
     * @return Camera distance from model center
     */
    fun calculateDistance(
        boundingBox: BoundingBox,
        fov: Float = DEFAULT_FOV,
        padding: Float = DEFAULT_PADDING
    ): Float {
        // Use bounding sphere radius (ensures entire model fits)
        val radius = boundingBox.boundingSphereRadius

        // Convert FOV to radians
        val fovRadians = Math.toRadians(fov.toDouble()) / 2.0

        // Calculate base distance: radius / tan(fov/2)
        val baseDistance = (radius / tan(fovRadians)).toFloat()

        // Apply padding and clamp to reasonable range
        val paddedDistance = baseDistance * padding
        return paddedDistance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
    }

    /**
     * Calculate camera position given center, distance, and angle
     *
     * Positions camera on a sphere around the model center.
     *
     * @param center Model center (lookAt point)
     * @param distance Distance from center
     * @param verticalAngle Vertical angle in degrees (0° = level, +ve = above, -ve = below)
     * @param horizontalAngle Horizontal angle in degrees (0° = front, 90° = right side)
     * @return Camera position in world space
     */
    fun calculateCameraPosition(
        center: Position3D,
        distance: Float,
        verticalAngle: Float = 0f,
        horizontalAngle: Float = 0f
    ): Position3D {
        // Convert angles to radians
        val vRadians = Math.toRadians(verticalAngle.toDouble())
        val hRadians = Math.toRadians(horizontalAngle.toDouble())

        // Calculate position on sphere
        val x = (distance * kotlin.math.sin(hRadians) * kotlin.math.cos(vRadians)).toFloat()
        val y = (distance * kotlin.math.sin(vRadians)).toFloat()
        val z = (distance * kotlin.math.cos(hRadians) * kotlin.math.cos(vRadians)).toFloat()

        // Offset by center
        return Position3D(
            x = center.x + x,
            y = center.y + y,
            z = center.z + z
        )
    }

    /**
     * Calculate camera position for top-down view
     *
     * Useful for viewing models from above (bird's eye view).
     *
     * @param metadata Model metadata
     * @param heightMultiplier How high above model (2.0 = twice bounding box height)
     * @return Camera configuration for top-down view
     */
    fun calculateTopDownView(
        metadata: ModelMetadata,
        heightMultiplier: Float = 2.0f
    ): CameraConfig {
        val center = metadata.center
        val height = metadata.boundingBox.height * heightMultiplier

        return CameraConfig(
            position = Position3D(
                x = center.x,
                y = center.y + height,
                z = center.z
            ),
            lookAt = center,
            distance = height,
            fov = DEFAULT_FOV
        )
    }

    /**
     * Calculate camera position for side view
     *
     * Useful for viewing model profile (e.g., character design).
     *
     * @param metadata Model metadata
     * @param side Which side (90° = right, -90° = left, 180° = back)
     * @return Camera configuration for side view
     */
    fun calculateSideView(
        metadata: ModelMetadata,
        side: Float = 90f
    ): CameraConfig {
        val center = metadata.center
        val distance = calculateDistance(metadata.boundingBox)

        return CameraConfig(
            position = calculateCameraPosition(
                center = center,
                distance = distance,
                horizontalAngle = side
            ),
            lookAt = center,
            distance = distance,
            fov = DEFAULT_FOV
        )
    }

    /**
     * Calculate camera bounds for interactive controls
     *
     * Returns min/max distances for zoom constraints.
     *
     * @param metadata Model metadata
     * @return Pair of (minDistance, maxDistance)
     */
    fun calculateZoomBounds(metadata: ModelMetadata): Pair<Float, Float> {
        val optimalDistance = calculateDistance(metadata.boundingBox)

        return Pair(
            first = (optimalDistance * 0.5f).coerceAtLeast(MIN_DISTANCE),  // Can zoom in to 50%
            second = (optimalDistance * 3.0f).coerceAtMost(MAX_DISTANCE)   // Can zoom out to 300%
        )
    }

    /**
     * Interpolate between two camera configurations
     *
     * Useful for smooth camera transitions.
     *
     * @param from Start camera config
     * @param to End camera config
     * @param progress Interpolation factor (0.0 = from, 1.0 = to)
     * @return Interpolated camera configuration
     */
    fun interpolate(
        from: CameraConfig,
        to: CameraConfig,
        progress: Float
    ): CameraConfig {
        val t = progress.coerceIn(0f, 1f)

        return CameraConfig(
            position = Position3D(
                x = lerp(from.position.x, to.position.x, t),
                y = lerp(from.position.y, to.position.y, t),
                z = lerp(from.position.z, to.position.z, t)
            ),
            lookAt = Position3D(
                x = lerp(from.lookAt.x, to.lookAt.x, t),
                y = lerp(from.lookAt.y, to.lookAt.y, t),
                z = lerp(from.lookAt.z, to.lookAt.z, t)
            ),
            distance = lerp(from.distance, to.distance, t),
            fov = lerp(from.fov, to.fov, t)
        )
    }

    /**
     * Linear interpolation helper
     */
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Basic auto-fit
 * val metadata = GLBModelLoader.loadMetadata("models/Colobus_Animations.glb")
 * val camera = CameraAutoFit.calculate(metadata)
 * cameraNode.position = camera.position.toSceneViewPosition()
 * cameraNode.lookAt(camera.lookAt.toSceneViewPosition())
 *
 * // Custom padding (tighter fit)
 * val tightCamera = CameraAutoFit.calculate(
 *     metadata = metadata,
 *     padding = 1.2f  // Only 20% extra space
 * )
 *
 * // Elevated view (15° above model)
 * val elevatedCamera = CameraAutoFit.calculate(
 *     metadata = metadata,
 *     cameraAngle = 15f
 * )
 *
 * // Top-down view
 * val topCamera = CameraAutoFit.calculateTopDownView(metadata)
 *
 * // Side view
 * val sideCamera = CameraAutoFit.calculateSideView(
 *     metadata = metadata,
 *     side = 90f  // Right side
 * )
 *
 * // Zoom bounds for interactive camera
 * val (minZoom, maxZoom) = CameraAutoFit.calculateZoomBounds(metadata)
 *
 * // Smooth camera transition
 * val currentCamera = CameraAutoFit.calculate(metadata)
 * val targetCamera = CameraAutoFit.calculateTopDownView(metadata)
 * val interpolatedCamera = CameraAutoFit.interpolate(
 *     from = currentCamera,
 *     to = targetCamera,
 *     progress = animationProgress  // 0.0 to 1.0
 * )
 * ```
 */
