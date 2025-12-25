package app.m1k3.ai.assistant.avatar

/**
 * 間 AI 3D Model Introspection System
 *
 * Platform-agnostic metadata classes for generic GLB/GLTF model loading.
 * Supports automatic bounding box calculation, animation discovery,
 * material introspection, and camera auto-fitting.
 *
 * Design Philosophy:
 * - Universal GLB support (any model, not just Colobus)
 * - Auto-discovery (inspect model structure at runtime)
 * - Platform-agnostic (commonMain data classes)
 * - Caching-friendly (immutable data structures)
 */

/**
 * 3D position in world space
 *
 * @param x X coordinate (left/right)
 * @param y Y coordinate (up/down)
 * @param z Z coordinate (forward/back)
 */
data class Position3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Position3D) = Position3D(
        x = x + other.x,
        y = y + other.y,
        z = z + other.z
    )

    operator fun minus(other: Position3D) = Position3D(
        x = x - other.x,
        y = y - other.y,
        z = z - other.z
    )

    operator fun times(scalar: Float) = Position3D(
        x = x * scalar,
        y = y * scalar,
        z = z * scalar
    )

    operator fun div(scalar: Float) = Position3D(
        x = x / scalar,
        y = y / scalar,
        z = z / scalar
    )

    companion object {
        val ZERO = Position3D(0f, 0f, 0f)
    }
}

/**
 * Axis-aligned bounding box (AABB)
 *
 * Encapsulates the spatial extent of a 3D model.
 * Used for camera auto-fitting and collision detection.
 *
 * @param min Minimum corner (most negative coordinates)
 * @param max Maximum corner (most positive coordinates)
 */
data class BoundingBox(
    val min: Position3D,
    val max: Position3D
) {
    /**
     * Width (X-axis extent)
     */
    val width: Float
        get() = max.x - min.x

    /**
     * Height (Y-axis extent)
     */
    val height: Float
        get() = max.y - min.y

    /**
     * Depth (Z-axis extent)
     */
    val depth: Float
        get() = max.z - min.z

    /**
     * Geometric center of bounding box
     */
    val center: Position3D
        get() = Position3D(
            x = (min.x + max.x) / 2f,
            y = (min.y + max.y) / 2f,
            z = (min.z + max.z) / 2f
        )

    /**
     * Largest dimension (used for camera distance calculation)
     */
    val maxDimension: Float
        get() = maxOf(width, height, depth)

    /**
     * Radius of bounding sphere (sphere that fully contains this box)
     */
    val boundingSphereRadius: Float
        get() {
            val halfWidth = width / 2f
            val halfHeight = height / 2f
            val halfDepth = depth / 2f
            return kotlin.math.sqrt(
                halfWidth * halfWidth +
                        halfHeight * halfHeight +
                        halfDepth * halfDepth
            )
        }

    /**
     * Check if bounding box is valid (min < max on all axes)
     */
    val isValid: Boolean
        get() = min.x <= max.x && min.y <= max.y && min.z <= max.z

    companion object {
        /**
         * Empty bounding box (invalid, needs initialization)
         */
        val EMPTY = BoundingBox(
            min = Position3D(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            max = Position3D(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        )

        /**
         * Unit cube (1x1x1 centered at origin)
         */
        val UNIT = BoundingBox(
            min = Position3D(-0.5f, -0.5f, -0.5f),
            max = Position3D(0.5f, 0.5f, 0.5f)
        )
    }
}

/**
 * Animation metadata extracted from GLB file
 *
 * @param name Animation name (e.g., "Idle_A", "Walk", "Run")
 * @param index Index in model's animation array
 * @param duration Duration in seconds
 * @param frameCount Total number of frames
 * @param isLoopable Whether animation can loop smoothly
 */
data class AnimationMetadata(
    val name: String,
    val index: Int,
    val duration: Float,
    val frameCount: Int = 0,
    val isLoopable: Boolean = true
) {
    /**
     * Frames per second (calculated)
     */
    val fps: Float
        get() = if (duration > 0f) frameCount / duration else 0f

    /**
     * Check if animation name matches pattern (case-insensitive)
     */
    fun matches(pattern: String): Boolean {
        return name.contains(pattern, ignoreCase = true)
    }

    /**
     * Check if animation is an idle variant
     */
    val isIdle: Boolean
        get() = matches("idle")

    /**
     * Check if animation is a movement type
     */
    val isMovement: Boolean
        get() = matches("walk") || matches("run") || matches("fly") || matches("swim")
}

/**
 * Material information extracted from model
 *
 * @param name Material name
 * @param index Index in material array
 * @param hasTexture Whether material uses texture maps
 * @param baseColor Base color (if available)
 */
data class MaterialInfo(
    val name: String,
    val index: Int,
    val hasTexture: Boolean = false,
    val baseColor: String? = null  // Hex color string, e.g., "#FF5733"
)

/**
 * Complete model metadata
 *
 * Comprehensive introspection data for a 3D model.
 * Calculated once and cached for performance.
 *
 * @param boundingBox Spatial extent of model
 * @param animations List of available animations
 * @param materials List of materials used
 * @param hasSkeleton Whether model has bone structure (rigged)
 * @param triangleCount Approximate polygon count
 * @param modelPath Original file path
 */
data class ModelMetadata(
    val boundingBox: BoundingBox,
    val animations: List<AnimationMetadata>,
    val materials: List<MaterialInfo>,
    val hasSkeleton: Boolean = false,
    val triangleCount: Int = 0,
    val modelPath: String
) {
    /**
     * Geometric center of model (from bounding box)
     */
    val center: Position3D
        get() = boundingBox.center

    /**
     * Total animation count
     */
    val animationCount: Int
        get() = animations.size

    /**
     * Get animation by name (case-insensitive)
     */
    fun getAnimationByName(name: String): AnimationMetadata? {
        return animations.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * Get animations matching pattern (e.g., "Idle_*")
     */
    fun getAnimationsMatching(pattern: String): List<AnimationMetadata> {
        return animations.filter { it.matches(pattern) }
    }

    /**
     * Get first idle animation (fallback for unknown states)
     */
    fun getDefaultAnimation(): AnimationMetadata? {
        return animations.firstOrNull { it.isIdle } ?: animations.firstOrNull()
    }

    /**
     * Check if model is suitable for avatar display
     * (has skeleton and at least one animation)
     */
    val isAvatarReady: Boolean
        get() = hasSkeleton && animations.isNotEmpty()

    /**
     * Human-readable summary
     */
    override fun toString(): String {
        return """
            ModelMetadata(
              path: $modelPath,
              bounds: ${boundingBox.width.format(2)}×${boundingBox.height.format(2)}×${boundingBox.depth.format(2)},
              animations: $animationCount,
              materials: ${materials.size},
              triangles: ~$triangleCount,
              skeleton: $hasSkeleton
            )
        """.trimIndent()
    }

    companion object {
        /**
         * Create empty metadata (for loading state)
         */
        fun empty(modelPath: String) = ModelMetadata(
            boundingBox = BoundingBox.EMPTY,
            animations = emptyList(),
            materials = emptyList(),
            hasSkeleton = false,
            triangleCount = 0,
            modelPath = modelPath
        )
    }
}

/**
 * Camera configuration calculated from model metadata
 *
 * @param position Camera position in world space
 * @param lookAt Point camera should look at (usually model center)
 * @param distance Distance from camera to lookAt point
 * @param fov Field of view in degrees
 */
data class CameraConfig(
    val position: Position3D,
    val lookAt: Position3D,
    val distance: Float,
    val fov: Float = 45f  // Standard FOV
) {
    /**
     * Near clipping plane (10% of distance)
     */
    val nearPlane: Float
        get() = distance * 0.1f

    /**
     * Far clipping plane (300% of distance)
     */
    val farPlane: Float
        get() = distance * 3.0f

    companion object {
        /**
         * Default camera (for empty scenes)
         */
        val DEFAULT = CameraConfig(
            position = Position3D(0f, 0f, 5f),
            lookAt = Position3D.ZERO,
            distance = 5f
        )
    }
}

/**
 * Float formatting extension (for debug strings)
 */
private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

/**
 * Usage Examples:
 * ```kotlin
 * // Load model and extract metadata
 * val metadata = GLBModelLoader.loadMetadata("models/Colobus_Animations.glb")
 *
 * println("Model center: ${metadata.center}")
 * println("Bounding sphere radius: ${metadata.boundingBox.boundingSphereRadius}")
 * println("Available animations: ${metadata.animations.map { it.name }}")
 *
 * // Find specific animation
 * val walkAnim = metadata.getAnimationByName("Walk")
 * val idleAnims = metadata.getAnimationsMatching("Idle")
 *
 * // Calculate optimal camera
 * val camera = CameraAutoFit.calculate(metadata)
 * println("Camera position: ${camera.position}")
 * println("Camera distance: ${camera.distance}")
 * ```
 */
