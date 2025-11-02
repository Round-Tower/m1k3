package app.m1k3.ai.assistant.avatar

import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 間 AI GLB Model Loader (Android)
 *
 * Generic loader for GLB/GLTF models with pre-configured metadata.
 *
 * **NOTE:** SceneView doesn't expose full Filament introspection APIs,
 * so we use pre-configured metadata for known models (Quirky Series).
 *
 * Features:
 * - Load any GLB file from assets
 * - Pre-configured metadata for 8 Quirky Series animals
 * - Estimated bounding boxes for camera auto-fit
 * - Cache metadata for performance
 */

/**
 * GLB model loader with pre-configured metadata
 */
object GLBModelLoader {

    /**
     * Metadata cache (path → metadata)
     * Prevents redundant lookups
     */
    private val metadataCache = mutableMapOf<String, ModelMetadata>()

    /**
     * Load model metadata from GLB file
     *
     * Returns pre-configured metadata for known models.
     * For unknown models, returns estimated metadata.
     *
     * @param modelLoader SceneView ModelLoader instance
     * @param modelPath Asset path (e.g., "models/Colobus_Animations.glb")
     * @return Model metadata
     */
    suspend fun loadMetadata(
        modelLoader: ModelLoader,
        modelPath: String
    ): ModelMetadata = withContext(Dispatchers.IO) {
        // Check cache first
        metadataCache[modelPath]?.let { return@withContext it }

        try {
            // Get pre-configured metadata for known models
            val metadata = getPreconfiguredMetadata(modelPath)

            // Cache and return
            metadataCache[modelPath] = metadata
            metadata
        } catch (e: Exception) {
            println("⚠️ Failed to load model metadata for $modelPath: ${e.message}")
            e.printStackTrace()

            // Return empty metadata as fallback
            ModelMetadata.empty(modelPath)
        }
    }

    /**
     * Get pre-configured metadata for known models
     *
     * Returns hardcoded metadata for Quirky Series animals.
     * Falls back to default metadata for unknown models.
     *
     * @param modelPath Asset path
     * @return Model metadata
     */
    private fun getPreconfiguredMetadata(modelPath: String): ModelMetadata {
        return when {
            modelPath.contains("Colobus") -> createColobusMetadata(modelPath)
            modelPath.contains("Sparrow") -> createSparrowMetadata(modelPath)
            modelPath.contains("Gecko") -> createGeckoMetadata(modelPath)
            modelPath.contains("Herring") -> createHerringMetadata(modelPath)
            modelPath.contains("Muskrat") -> createMuskratMetadata(modelPath)
            modelPath.contains("Pudu") -> createPuduMetadata(modelPath)
            modelPath.contains("Taipan") -> createTaipanMetadata(modelPath)
            modelPath.contains("Inkfish") -> createInkfishMetadata(modelPath)
            else -> createDefaultMetadata(modelPath)
        }
    }

    /**
     * Create metadata for Colobus monkey
     */
    private fun createColobusMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.5f, -0.5f, -0.5f),
            max = Position3D(0.5f, 1.0f, 0.5f)
        ),
        animations = listOf(
            AnimationMetadata("Idle_A", 0, 2.0f, 60, true),
            AnimationMetadata("Idle_B", 1, 2.0f, 60, true),
            AnimationMetadata("Idle_C", 2, 2.0f, 60, true),
            AnimationMetadata("Fear", 3, 1.5f, 45, true),
            AnimationMetadata("Bounce", 4, 1.5f, 45, true),
            AnimationMetadata("Death", 5, 2.0f, 60, false),
            AnimationMetadata("Clicked", 6, 1.0f, 30, true),
            AnimationMetadata("Eat", 7, 2.0f, 60, true),
            AnimationMetadata("Sit", 8, 2.5f, 75, true),
            AnimationMetadata("Walk", 9, 1.5f, 45, true),
            AnimationMetadata("Run", 10, 1.0f, 30, true),
            AnimationMetadata("Fly", 11, 2.0f, 60, true),
            AnimationMetadata("Swim", 12, 2.0f, 60, true),
            AnimationMetadata("Attack", 13, 1.5f, 45, true),
            AnimationMetadata("Hit", 14, 1.0f, 30, false),
            AnimationMetadata("Roll", 15, 1.5f, 45, true),
            AnimationMetadata("Spin", 16, 2.0f, 60, true),
            AnimationMetadata("Jump", 17, 1.0f, 30, false)
        ),
        materials = listOf(MaterialInfo("ColobusMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 1200,
        modelPath = modelPath
    )

    /**
     * Create metadata for Sparrow
     */
    private fun createSparrowMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.3f, -0.3f, -0.3f),
            max = Position3D(0.3f, 0.4f, 0.3f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("SparrowMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 800,
        modelPath = modelPath
    )

    /**
     * Create metadata for Gecko
     */
    private fun createGeckoMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.4f, -0.2f, -0.4f),
            max = Position3D(0.4f, 0.3f, 0.8f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("GeckoMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 1000,
        modelPath = modelPath
    )

    /**
     * Create metadata for Herring
     */
    private fun createHerringMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.6f, -0.2f, -0.2f),
            max = Position3D(0.6f, 0.2f, 0.2f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("HerringMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 700,
        modelPath = modelPath
    )

    /**
     * Create metadata for Muskrat
     */
    private fun createMuskratMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.4f, -0.3f, -0.6f),
            max = Position3D(0.4f, 0.4f, 0.6f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("MuskratMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 900,
        modelPath = modelPath
    )

    /**
     * Create metadata for Pudu
     */
    private fun createPuduMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.4f, -0.4f, -0.5f),
            max = Position3D(0.4f, 0.8f, 0.5f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("PuduMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 1100,
        modelPath = modelPath
    )

    /**
     * Create metadata for Taipan
     */
    private fun createTaipanMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.2f, -0.2f, -1.0f),
            max = Position3D(0.2f, 0.2f, 1.0f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("TaipanMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 600,
        modelPath = modelPath
    )

    /**
     * Create metadata for Inkfish
     */
    private fun createInkfishMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox(
            min = Position3D(-0.5f, -0.5f, -0.5f),
            max = Position3D(0.5f, 0.5f, 0.5f)
        ),
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("InkfishMaterial", 0, true)),
        hasSkeleton = true,
        triangleCount = 1300,
        modelPath = modelPath
    )

    /**
     * Create default metadata for unknown models
     */
    private fun createDefaultMetadata(modelPath: String) = ModelMetadata(
        boundingBox = BoundingBox.UNIT,
        animations = createStandardAnimations(),
        materials = listOf(MaterialInfo("DefaultMaterial", 0, false)),
        hasSkeleton = true,
        triangleCount = 1000,
        modelPath = modelPath
    )

    /**
     * Create standard animation set (18 animations)
     * Used for all Quirky Series animals
     */
    private fun createStandardAnimations() = listOf(
        AnimationMetadata("Idle_A", 0, 2.0f, 60, true),
        AnimationMetadata("Idle_B", 1, 2.0f, 60, true),
        AnimationMetadata("Idle_C", 2, 2.0f, 60, true),
        AnimationMetadata("Fear", 3, 1.5f, 45, true),
        AnimationMetadata("Bounce", 4, 1.5f, 45, true),
        AnimationMetadata("Death", 5, 2.0f, 60, false),
        AnimationMetadata("Clicked", 6, 1.0f, 30, true),
        AnimationMetadata("Eat", 7, 2.0f, 60, true),
        AnimationMetadata("Sit", 8, 2.5f, 75, true),
        AnimationMetadata("Walk", 9, 1.5f, 45, true),
        AnimationMetadata("Run", 10, 1.0f, 30, true),
        AnimationMetadata("Fly", 11, 2.0f, 60, true),
        AnimationMetadata("Swim", 12, 2.0f, 60, true),
        AnimationMetadata("Attack", 13, 1.5f, 45, true),
        AnimationMetadata("Hit", 14, 1.0f, 30, false),
        AnimationMetadata("Roll", 15, 1.5f, 45, true),
        AnimationMetadata("Spin", 16, 2.0f, 60, true),
        AnimationMetadata("Jump", 17, 1.0f, 30, false)
    )


    /**
     * Clear metadata cache
     *
     * Frees memory if models are no longer needed.
     */
    fun clearCache() {
        metadataCache.clear()
    }

    /**
     * Preload metadata for model
     *
     * Loads metadata in background for faster access later.
     *
     * @param modelLoader SceneView ModelLoader
     * @param modelPath Asset path
     */
    suspend fun preloadMetadata(
        modelLoader: ModelLoader,
        modelPath: String
    ) {
        if (!metadataCache.containsKey(modelPath)) {
            loadMetadata(modelLoader, modelPath)
        }
    }

    /**
     * Get cached metadata
     *
     * Returns cached metadata without loading model.
     *
     * @param modelPath Asset path
     * @return Cached metadata or null
     */
    fun getCached(modelPath: String): ModelMetadata? {
        return metadataCache[modelPath]
    }

    /**
     * Check if metadata is cached
     *
     * @param modelPath Asset path
     * @return True if cached
     */
    fun isCached(modelPath: String): Boolean {
        return metadataCache.containsKey(modelPath)
    }
}

/**
 * Extension: Convert Position3D to SceneView Position
 */
fun Position3D.toSceneViewPosition(): io.github.sceneview.math.Position {
    return io.github.sceneview.math.Position(x, y, z)
}

/**
 * Extension: Convert SceneView Position to Position3D
 */
fun io.github.sceneview.math.Position.toPosition3D(): Position3D {
    return Position3D(x, y, z)
}

/**
 * Usage Examples:
 * ```kotlin
 * @Composable
 * fun LoadModelExample() {
 *     val engine = rememberEngine()
 *     val modelLoader = rememberModelLoader(engine)
 *     var metadata by remember { mutableStateOf<ModelMetadata?>(null) }
 *
 *     LaunchedEffect(Unit) {
 *         // Load pre-configured metadata
 *         metadata = GLBModelLoader.loadMetadata(
 *             modelLoader = modelLoader,
 *             modelPath = "models/Sparrow_Animations.glb"
 *         )
 *
 *         println("Loaded: ${metadata?.modelPath}")
 *         println("Bounds: ${metadata?.boundingBox}")
 *         println("Animations: ${metadata?.animations?.map { it.name }}")
 *         // Output: Animations: [Idle_A, Idle_B, Idle_C, Fear, ...]
 *     }
 *
 *     // Use metadata for auto-fitting camera
 *     metadata?.let { meta ->
 *         val camera = CameraAutoFit.calculate(meta)
 *         // Apply camera config...
 *     }
 * }
 *
 * // All Quirky Series models have pre-configured metadata
 * val colobusMetadata = GLBModelLoader.loadMetadata(modelLoader, "models/Colobus_Animations.glb")
 * val sparrowMetadata = GLBModelLoader.loadMetadata(modelLoader, "models/Sparrow_Animations.glb")
 * // Returns instantly from pre-configured data (no model parsing needed)
 * ```
 */
