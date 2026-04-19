package app.m1k3.ai.assistant.avatar

import android.content.Context
import app.m1k3.ai.assistant.utils.Logger
import com.google.android.filament.Engine
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.Model
import io.github.sceneview.model.ModelInstance

private val logger = Logger.withTag("SharedModelCache")

/**
 * One ModelLoader + one Model per GLB path, shared across every Avatar3D
 * surface in the app. Each surface gets its own cheap ModelInstance
 * (independent animator, shared GPU resources).
 *
 * Replaces per-composable `rememberModelLoader(engine)` + per-call
 * `createModelInstance(path)`. That pattern spawned independent
 * AssetLoader/ResourceLoader/UbershaderProvider trios per surface —
 * when two were alive concurrently (hero + toolbar) their tear-downs
 * raced and libgltfio-jni.so null-deref'd at 0x2a8.
 *
 * One loader, one asset per path, N instances. Filament's gltfio
 * supports secondary FilamentInstances created via
 * [com.google.android.filament.gltfio.AssetLoader.createInstance]
 * with their own animators — confirmed by the FilamentInstance API.
 * The stale comment in SceneView's ModelLoader ("Animation is not
 * supported in new instances") predates modern gltfio.
 *
 * Lifecycle: loader + models live until [forceDestroy], called from
 * MainActivity.onDestroy just before [FilamentEngineManager.forceDestroy].
 * Ordering matters — the loader holds engine-owned handles.
 *
 * MurphySig: kev+claude / confidence 0.82 / 2026-04-19
 * Rationale: single shared-loader pattern is the minimum change that
 * eliminates the race. Doesn't require reworking ModelNode lifecycle
 * or pre-allocating instanced assets (which would cap surface count).
 */
object SharedModelCache {
    @Volatile private var loader: ModelLoader? = null
    private val models = mutableMapOf<String, Model>()
    private val lock = Any()

    fun getLoader(
        context: Context,
        engine: Engine,
    ): ModelLoader =
        synchronized(lock) {
            loader ?: ModelLoader(engine, context.applicationContext).also {
                loader = it
                logger.d { "[SharedModelCache] Created shared ModelLoader" }
            }
        }

    /**
     * Returns a ModelInstance for [assetPath] using the shared loader.
     * First consumer for a given path gets the default instance (free
     * with the asset). Subsequent consumers get secondary instances
     * created via [ModelLoader.createInstance], which share the Model's
     * GPU resources and own animator state.
     */
    fun createInstance(
        context: Context,
        engine: Engine,
        assetPath: String,
    ): ModelInstance? =
        synchronized(lock) {
            val sharedLoader = getLoader(context, engine)
            val existing = models[assetPath]
            if (existing != null) {
                sharedLoader.createInstance(existing).also {
                    logger.d { "[SharedModelCache] Secondary instance for $assetPath" }
                }
            } else {
                val model = sharedLoader.createModel(assetPath)
                models[assetPath] = model
                logger.d { "[SharedModelCache] Loaded asset $assetPath (${models.size} cached)" }
                model.instance
            }
        }

    fun forceDestroy() =
        synchronized(lock) {
            logger.i { "[SharedModelCache] Force destroy (${models.size} cached models)" }
            models.clear()
            loader?.destroy()
            loader = null
        }

    fun cachedModelCount(): Int = synchronized(lock) { models.size }
}
