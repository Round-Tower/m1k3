package app.m1k3.ai.assistant.avatar

/**
 * 間 AI Model Registry
 *
 * Central registry for 3D avatar models.
 * Manages multiple model configurations and provides
 * a unified API for model selection and loading.
 *
 * Features:
 * - Pre-configured models (Quirky Series animals)
 * - Runtime model registration
 * - Model discovery from assets
 * - Default fallbacks
 */

/**
 * Model configuration
 *
 * Describes a 3D model and its properties.
 *
 * @param id Unique identifier (e.g., "mask", "colobus", "sparrow")
 * @param name Display name (e.g., "Mask", "Colobus Monkey")
 * @param path Asset path (e.g., "models/Mask.glb", "models/Colobus_Animations.glb")
 * @param description Optional description
 * @param thumbnail Optional thumbnail path
 * @param category Model category (e.g., "static", "mammal", "bird", "fish")
 * @param modelType Whether model is STATIC or ANIMATED
 * @param hasAnimations Whether model has baked skeleton animations
 * @param supportsEmotions Whether model can display emotions
 * @param defaultForNewUsers Whether this is recommended starting model
 * @param attribution Optional license attribution
 */
data class ModelConfig(
    val id: String,
    val name: String,
    val path: String,
    val description: String = "",
    val thumbnail: String? = null,
    val category: String = "animal",
    val modelType: ModelType = ModelType.ANIMATED,
    val hasAnimations: Boolean = true,
    val supportsEmotions: Boolean = true,
    val defaultForNewUsers: Boolean = false,
    val attribution: String? = null
) {
    /**
     * Check if this is the default model
     */
    val isDefault: Boolean
        get() = id == ModelRegistry.DEFAULT_MODEL_ID

    /**
     * Get file name from path
     */
    val fileName: String
        get() = path.substringAfterLast('/')

    companion object {
        /**
         * Simple mask (alternative avatar)
         *
         * Static model with procedural animations (no skeleton).
         * Alternative option for low-end devices or minimalist preference.
         *
         * License: CC-BY-4.0 by IzLoM39 (Sketchfab)
         */
        val MASK = ModelConfig(
            id = "mask",
            name = "Mask",
            path = "models/Mask.glb",
            description = "Simple mask with procedural animations (rotation, scale, color)",
            category = "static",
            modelType = ModelType.STATIC,
            hasAnimations = false,
            supportsEmotions = true,
            defaultForNewUsers = false,
            attribution = "CC-BY-4.0 by IzLoM39 (Sketchfab)"
        )

        /**
         * Colobus monkey (animated) - DEFAULT
         *
         * Eco-consciousness showcase with lifelike animations.
         * Perfect starting point showcasing the full avatar system.
         */
        val COLOBUS = ModelConfig(
            id = "colobus",
            name = "Colobus Monkey",
            path = "models/Colobus_Animations.glb",
            description = "Black and white colobus monkey with 18 animations",
            category = "mammal",
            modelType = ModelType.ANIMATED,
            hasAnimations = true,
            supportsEmotions = true,
            defaultForNewUsers = true
        )

        /**
         * Sparrow (bird) - Optimal version with mesh + 18 animations
         */
        val SPARROW = ModelConfig(
            id = "sparrow",
            name = "Sparrow",
            path = "models/Sparrow_Animations.glb",
            description = "Small bird with flying and perching animations (269 KB optimal)",
            category = "bird"
        )

        /**
         * Gecko (reptile)
         */
        val GECKO = ModelConfig(
            id = "gecko",
            name = "Gecko",
            path = "models/Gecko_Animations.glb",
            description = "Colorful gecko with climbing animations",
            category = "reptile"
        )

        /**
         * Herring (fish)
         */
        val HERRING = ModelConfig(
            id = "herring",
            name = "Herring",
            path = "models/Herring_Animations.glb",
            description = "Swimming fish with aquatic animations",
            category = "fish"
        )

        /**
         * Muskrat (mammal)
         */
        val MUSKRAT = ModelConfig(
            id = "muskrat",
            name = "Muskrat",
            path = "models/Muskrat_Animations.glb",
            description = "Semi-aquatic rodent with swimming animations",
            category = "mammal"
        )

        /**
         * Pudu (mammal)
         */
        val PUDU = ModelConfig(
            id = "pudu",
            name = "Pudu",
            path = "models/Pudu_Animations.glb",
            description = "Smallest deer species with running animations",
            category = "mammal"
        )

        /**
         * Taipan (reptile)
         */
        val TAIPAN = ModelConfig(
            id = "taipan",
            name = "Taipan",
            path = "models/Taipan_Animations.glb",
            description = "Venomous snake with slithering animations",
            category = "reptile"
        )

        /**
         * Inkfish/Squid (cephalopod)
         */
        val INKFISH = ModelConfig(
            id = "inkfish",
            name = "Inkfish",
            path = "models/Inkfish_Animations.glb",
            description = "Squid with jet propulsion animations",
            category = "cephalopod"
        )
    }
}

/**
 * Model registry singleton
 *
 * Central registry for all available 3D models.
 */
object ModelRegistry {

    /**
     * Default model ID (Colobus - eco-consciousness showcase)
     */
    const val DEFAULT_MODEL_ID = "colobus"

    /**
     * Pre-configured models
     *
     * Includes:
     * - Colobus monkey (DEFAULT - animated, eco-consciousness showcase)
     * - Quirky Series FREE Animals v1.4 (7 additional animated models)
     * - Mask (static, procedural animations alternative)
     */
    private val predefinedModels = listOf(
        ModelConfig.MASK,        // Static model (alternative)
        ModelConfig.COLOBUS,     // Animated models (DEFAULT, eco-consciousness showcase)
        ModelConfig.SPARROW,
        ModelConfig.GECKO,
        ModelConfig.HERRING,
        ModelConfig.MUSKRAT,
        ModelConfig.PUDU,
        ModelConfig.TAIPAN,
        ModelConfig.INKFISH
    )

    /**
     * Runtime-registered models
     */
    private val customModels = mutableListOf<ModelConfig>()

    /**
     * All available models (predefined + custom)
     */
    val allModels: List<ModelConfig>
        get() = predefinedModels + customModels

    /**
     * Get model by ID
     *
     * @param id Model identifier
     * @return Model configuration or null if not found
     */
    fun getById(id: String): ModelConfig? {
        return allModels.firstOrNull { it.id == id }
    }

    /**
     * Get default model (Colobus Monkey)
     *
     * Returns the Colobus monkey as the default avatar, showcasing
     * full animated capabilities and eco-consciousness credentials.
     *
     * @return Default model configuration
     */
    fun getDefault(): ModelConfig {
        return ModelConfig.COLOBUS
    }

    /**
     * Get models by category
     *
     * @param category Category name (e.g., "mammal", "bird")
     * @return List of models in category
     */
    fun getByCategory(category: String): List<ModelConfig> {
        return allModels.filter { it.category.equals(category, ignoreCase = true) }
    }

    /**
     * Register custom model at runtime
     *
     * Allows apps to add their own models dynamically.
     *
     * @param config Model configuration
     * @return True if registered successfully, false if ID already exists
     */
    fun register(config: ModelConfig): Boolean {
        if (allModels.any { it.id == config.id }) {
            return false  // ID collision
        }
        customModels.add(config)
        return true
    }

    /**
     * Unregister custom model
     *
     * @param id Model ID to remove
     * @return True if removed, false if not found or is predefined
     */
    fun unregister(id: String): Boolean {
        return customModels.removeIf { it.id == id }
    }

    /**
     * Check if model ID exists
     *
     * @param id Model identifier
     * @return True if model exists in registry
     */
    fun exists(id: String): Boolean {
        return allModels.any { it.id == id }
    }

    /**
     * Get all categories
     *
     * @return Distinct list of categories
     */
    fun getCategories(): List<String> {
        return allModels.map { it.category }.distinct().sorted()
    }

    /**
     * Search models by name or description
     *
     * @param query Search query (case-insensitive)
     * @return List of matching models
     */
    fun search(query: String): List<ModelConfig> {
        val lowerQuery = query.lowercase()
        return allModels.filter {
            it.name.lowercase().contains(lowerQuery) ||
                    it.description.lowercase().contains(lowerQuery) ||
                    it.id.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Get model count
     *
     * @return Total number of registered models
     */
    val count: Int
        get() = allModels.size

    /**
     * Get predefined model count
     *
     * @return Number of pre-configured models
     */
    val predefinedCount: Int
        get() = predefinedModels.size

    /**
     * Get custom model count
     *
     * @return Number of runtime-registered models
     */
    val customCount: Int
        get() = customModels.size

    /**
     * Generate registry report
     *
     * @return Human-readable summary of registered models
     */
    fun generateReport(): String {
        val report = StringBuilder()
        report.appendLine("Model Registry Report")
        report.appendLine("=" .repeat(50))
        report.appendLine()
        report.appendLine("Total Models: $count")
        report.appendLine("  • Predefined: $predefinedCount")
        report.appendLine("  • Custom: $customCount")
        report.appendLine()
        report.appendLine("Categories:")
        getCategories().forEach { category ->
            val models = getByCategory(category)
            report.appendLine("  • $category (${models.size})")
        }
        report.appendLine()
        report.appendLine("Available Models:")
        allModels.forEach { model ->
            val defaultTag = if (model.isDefault) " [DEFAULT]" else ""
            report.appendLine("  • ${model.name} (${model.id})$defaultTag")
            report.appendLine("    Path: ${model.path}")
            if (model.description.isNotEmpty()) {
                report.appendLine("    Desc: ${model.description}")
            }
        }

        return report.toString()
    }
}

/**
 * Usage Examples:
 * ```kotlin
 * // Get default model
 * val defaultModel = ModelRegistry.getDefault()
 * println("Default: ${defaultModel.name}")  // Output: "Default: Colobus Monkey"
 *
 * // Get specific model
 * val sparrow = ModelRegistry.getById("sparrow")
 * if (sparrow != null) {
 *     val metadata = GLBModelLoader.loadMetadata(sparrow.path)
 * }
 *
 * // Get all models
 * val allModels = ModelRegistry.allModels
 * println("Total models: ${allModels.size}")  // Output: "Total models: 8"
 *
 * // Get models by category
 * val mammals = ModelRegistry.getByCategory("mammal")
 * println("Mammals: ${mammals.map { it.name }}")  // Output: [Colobus Monkey, Muskrat, Pudu]
 *
 * // Search models
 * val birdModels = ModelRegistry.search("bird")
 * val flyingModels = ModelRegistry.search("flying")
 *
 * // Register custom model
 * val customModel = ModelConfig(
 *     id = "custom_dragon",
 *     name = "Dragon",
 *     path = "models/Dragon.glb",
 *     description = "Fantasy dragon with fire breathing",
 *     category = "fantasy"
 * )
 * ModelRegistry.register(customModel)
 *
 * // Generate report
 * val report = ModelRegistry.generateReport()
 * println(report)
 * ```
 */
