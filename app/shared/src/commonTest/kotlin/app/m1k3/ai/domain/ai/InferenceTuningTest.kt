package app.m1k3.ai.domain.ai

import app.m1k3.ai.domain.platform.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD coverage for [InferenceTuning.resolve] — the single source of truth for
 * per-(device × model) llama.cpp context, batch, threading, and max-token
 * settings. Phase 1 keeps the aggressive flags (FA, KV quant) dormant while
 * the wiring is proven safe; Phase 2 flips them on for HIGH_END+.
 */
class InferenceTuningTest {
    // --- Invariants across the whole matrix --------------------------------

    @Test
    fun `resolved context never exceeds model maxContextTokens`() {
        for (tier in DeviceTier.entries) {
            for (model in LlmModel.all()) {
                val tuning = InferenceTuning.resolve(tier, model)
                assertTrue(
                    tuning.nCtx <= model.maxContextTokens,
                    "${tier.name}/${model.id}: nCtx=${tuning.nCtx} > maxContextTokens=${model.maxContextTokens}",
                )
            }
        }
    }

    @Test
    fun `resolved context is always positive`() {
        for (tier in DeviceTier.entries) {
            for (model in LlmModel.all()) {
                val tuning = InferenceTuning.resolve(tier, model)
                assertTrue(tuning.nCtx > 0, "${tier.name}/${model.id} nCtx must be positive")
                assertTrue(tuning.maxTokens > 0, "${tier.name}/${model.id} maxTokens must be positive")
                assertTrue(tuning.nBatch > 0, "${tier.name}/${model.id} nBatch must be positive")
                assertTrue(tuning.nUbatch > 0, "${tier.name}/${model.id} nUbatch must be positive")
                assertTrue(tuning.threadsGen > 0, "${tier.name}/${model.id} threadsGen must be positive")
                assertTrue(tuning.threadsBatch > 0, "${tier.name}/${model.id} threadsBatch must be positive")
            }
        }
    }

    @Test
    fun `nUbatch never exceeds nBatch`() {
        for (tier in DeviceTier.entries) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertTrue(
                    t.nUbatch <= t.nBatch,
                    "${tier.name}/${model.id}: nUbatch=${t.nUbatch} must be <= nBatch=${t.nBatch}",
                )
            }
        }
    }

    @Test
    fun `max tokens is smaller than context window`() {
        for (tier in DeviceTier.entries) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertTrue(
                    t.maxTokens < t.nCtx,
                    "${tier.name}/${model.id}: maxTokens=${t.maxTokens} must be < nCtx=${t.nCtx}",
                )
            }
        }
    }

    // --- Phase 2 activation: FA + Q8_0 on HIGH_END+, F16 + FA-off below ---

    @Test
    fun `HIGH_END and FLAGSHIP enable flash attention`() {
        val hotTiers = listOf(DeviceTier.HIGH_END, DeviceTier.FLAGSHIP)
        for (tier in hotTiers) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertTrue(
                    t.useFlashAttn,
                    "${tier.name}/${model.id}: FA must be ON in Phase 2",
                )
            }
        }
    }

    @Test
    fun `HIGH_END and FLAGSHIP use Q8_0 KV cache`() {
        val hotTiers = listOf(DeviceTier.HIGH_END, DeviceTier.FLAGSHIP)
        for (tier in hotTiers) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertEquals(
                    KvCacheType.Q8_0,
                    t.kvQuant,
                    "${tier.name}/${model.id}: KV cache must be Q8_0 in Phase 2",
                )
            }
        }
    }

    @Test
    fun `MID_RANGE and below stay on F16 KV with FA disabled`() {
        val safeTiers = listOf(DeviceTier.MID_RANGE, DeviceTier.BUDGET, DeviceTier.LOW_END)
        for (tier in safeTiers) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertFalse(
                    t.useFlashAttn,
                    "${tier.name}/${model.id}: FA must be OFF below HIGH_END",
                )
                assertEquals(
                    KvCacheType.F16,
                    t.kvQuant,
                    "${tier.name}/${model.id}: KV cache must stay F16 below HIGH_END",
                )
            }
        }
    }

    @Test
    fun `Q8_0 KV is only ever paired with flash attention`() {
        // Upstream llama.cpp rejects quantized V-cache without FA (llama-context.cpp).
        // Belt-and-braces invariant — if we ever drift, this fails loudly.
        for (tier in DeviceTier.entries) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                if (t.kvQuant != KvCacheType.F16) {
                    assertTrue(
                        t.useFlashAttn,
                        "${tier.name}/${model.id}: quantized KV requires FA on",
                    )
                }
            }
        }
    }

    @Test
    fun `FLAGSHIP pins model weights with mlock`() {
        for (model in LlmModel.all()) {
            val t = InferenceTuning.resolve(DeviceTier.FLAGSHIP, model)
            assertTrue(
                t.useMlock,
                "FLAGSHIP/${model.id}: mlock should be on — 12GB+ has headroom",
            )
        }
    }

    @Test
    fun `non-flagship tiers do not use mlock`() {
        val nonFlagship = DeviceTier.entries - DeviceTier.FLAGSHIP
        for (tier in nonFlagship) {
            for (model in LlmModel.all()) {
                val t = InferenceTuning.resolve(tier, model)
                assertFalse(
                    t.useMlock,
                    "${tier.name}/${model.id}: mlock only belongs on FLAGSHIP",
                )
            }
        }
    }

    // --- FLAGSHIP (≥12GB) specifics ---------------------------------------

    @Test
    fun `FLAGSHIP gives Mini 8192 context (capped by model ceiling)`() {
        val t = InferenceTuning.resolve(DeviceTier.FLAGSHIP, LlmModel.Qwen35_0B8)
        assertEquals(8192, t.nCtx)
    }

    @Test
    fun `FLAGSHIP gives Lil 4096 context`() {
        val t = InferenceTuning.resolve(DeviceTier.FLAGSHIP, LlmModel.Qwen35_2B)
        assertEquals(4096, t.nCtx)
    }

    @Test
    fun `FLAGSHIP gives Big 4096 context`() {
        val t = InferenceTuning.resolve(DeviceTier.FLAGSHIP, LlmModel.Gemma4_E2B)
        assertEquals(4096, t.nCtx)
    }

    // --- HIGH_END (≥8GB) specifics — the Pixel 9a hot path -----------------

    @Test
    fun `HIGH_END gives Lil 4096 context (Pixel 9a default path)`() {
        val t = InferenceTuning.resolve(DeviceTier.HIGH_END, LlmModel.Qwen35_2B)
        assertEquals(4096, t.nCtx)
    }

    @Test
    fun `HIGH_END gives Mini 6144 context`() {
        val t = InferenceTuning.resolve(DeviceTier.HIGH_END, LlmModel.Qwen35_0B8)
        assertEquals(6144, t.nCtx)
    }

    @Test
    fun `HIGH_END gives Big 3072 context`() {
        val t = InferenceTuning.resolve(DeviceTier.HIGH_END, LlmModel.Gemma4_E2B)
        assertEquals(3072, t.nCtx)
    }

    // --- MID_RANGE (≥6GB) ---------------------------------------------------

    @Test
    fun `MID_RANGE gives Mini 4096 context`() {
        val t = InferenceTuning.resolve(DeviceTier.MID_RANGE, LlmModel.Qwen35_0B8)
        assertEquals(4096, t.nCtx)
    }

    @Test
    fun `MID_RANGE gives Lil 3072 context`() {
        val t = InferenceTuning.resolve(DeviceTier.MID_RANGE, LlmModel.Qwen35_2B)
        assertEquals(3072, t.nCtx)
    }

    // --- BUDGET / LOW_END — must stay conservative -------------------------

    @Test
    fun `BUDGET Mini uses 2048 context`() {
        val t = InferenceTuning.resolve(DeviceTier.BUDGET, LlmModel.Qwen35_0B8)
        assertEquals(2048, t.nCtx)
    }

    @Test
    fun `BUDGET Lil uses 2048 context`() {
        val t = InferenceTuning.resolve(DeviceTier.BUDGET, LlmModel.Qwen35_2B)
        assertEquals(2048, t.nCtx)
    }

    @Test
    fun `LOW_END Mini uses 1536 context`() {
        val t = InferenceTuning.resolve(DeviceTier.LOW_END, LlmModel.Qwen35_0B8)
        assertEquals(1536, t.nCtx)
    }

    // --- maxTokens tiering -------------------------------------------------

    @Test
    fun `maxTokens shrinks on smaller RAM tiers`() {
        val flagship = InferenceTuning.resolve(DeviceTier.FLAGSHIP, LlmModel.Qwen35_2B).maxTokens
        val highEnd = InferenceTuning.resolve(DeviceTier.HIGH_END, LlmModel.Qwen35_2B).maxTokens
        val midRange = InferenceTuning.resolve(DeviceTier.MID_RANGE, LlmModel.Qwen35_2B).maxTokens
        val budget = InferenceTuning.resolve(DeviceTier.BUDGET, LlmModel.Qwen35_2B).maxTokens
        assertTrue(flagship >= highEnd, "FLAGSHIP maxTokens should be >= HIGH_END")
        assertTrue(highEnd >= midRange, "HIGH_END maxTokens should be >= MID_RANGE")
        assertTrue(midRange >= budget, "MID_RANGE maxTokens should be >= BUDGET")
    }
}
