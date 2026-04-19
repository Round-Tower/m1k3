package app.m1k3.ai.assistant.avatar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for [SharedModelCache] that don't require a live
 * Filament Engine. Instance creation goes through native gltfio and
 * needs an Android + Filament setup — that path is exercised by the
 * on-device smoke test.
 *
 * These assertions cover the parts that must hold regardless of
 * platform: empty start state, forceDestroy idempotency, and that
 * forceDestroy resets the count when no loader was ever acquired.
 */
class SharedModelCacheTest {
    @Test
    fun `starts with zero cached models`() {
        // May carry state from a prior test in the same JVM — reset first.
        SharedModelCache.forceDestroy()
        assertEquals(0, SharedModelCache.cachedModelCount())
    }

    @Test
    fun `forceDestroy is safe when loader was never acquired`() {
        SharedModelCache.forceDestroy()
        SharedModelCache.forceDestroy()
        assertEquals(0, SharedModelCache.cachedModelCount())
    }
}
