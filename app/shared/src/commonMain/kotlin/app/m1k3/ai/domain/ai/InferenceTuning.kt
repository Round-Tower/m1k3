package app.m1k3.ai.domain.ai

import app.m1k3.ai.domain.platform.DeviceTier

/**
 * KV cache precision.
 *
 * Upstream llama.cpp couples V-cache quantization with flash attention — V must
 * stay F16 when flash_attn is disabled. [InferenceTuning.resolve] enforces the
 * pairing: Q8_0 is only returned alongside `useFlashAttn = true` (HIGH_END+).
 */
enum class KvCacheType {
    F16,
    Q8_0,
}

/**
 * Device-and-model-aware llama.cpp runtime configuration.
 *
 * Produced by [InferenceTuning.resolve]. Passed through the JNI bridge into
 * `llama_context_params` (see ma_bridge.cpp init).
 *
 * @param nCtx Context window in tokens (sets `llama_context_params.n_ctx`).
 * @param nBatch Logical batch size for prompt prefill (sets `n_batch`).
 * @param nUbatch Physical micro-batch size; must be <= nBatch (sets `n_ubatch`).
 * @param useFlashAttn When true the bridge asks for LLAMA_FLASH_ATTN_TYPE_AUTO.
 *   HIGH_END + FLAGSHIP enable it; MID_RANGE and below keep it disabled.
 * @param kvQuant KV cache precision. F16 is safe everywhere; Q8_0 halves KV
 *   memory but upstream requires flash attention — must be paired with useFlashAttn.
 * @param useMlock Pin model weights into RAM on flagship devices.
 * @param threadsGen Thread count for decode (generation).
 * @param threadsBatch Thread count for prefill (prompt encode).
 * @param maxTokens Default generation cap when the caller doesn't override.
 */
data class InferenceTuning(
    val nCtx: Int,
    val nBatch: Int,
    val nUbatch: Int,
    val useFlashAttn: Boolean,
    val kvQuant: KvCacheType,
    val useMlock: Boolean,
    val threadsGen: Int,
    val threadsBatch: Int,
    val maxTokens: Int,
) {
    companion object {
        /**
         * Resolve runtime tuning for a `(tier, model)` pair.
         *
         * The matrix trades context window + batch size for memory headroom and
         * never offers a context larger than [LlmModel.maxContextTokens]. See
         * `swift-noodling-dijkstra.md` for the full matrix and rationale.
         *
         * **Phase 2:** HIGH_END + FLAGSHIP light up flash attention and Q8_0 KV
         * cache (upstream couples them: V-quant requires FA). FLAGSHIP also
         * mlocks model weights since 12GB+ has the headroom. MID_RANGE and
         * below stay on F16 KV + FA disabled until more device telemetry.
         */
        fun resolve(
            tier: DeviceTier,
            model: LlmModel,
        ): InferenceTuning {
            // Big-tier models like Gemma 4 E2B are 2.3B params but 5.1B with PLE
            // and need 6GB RAM — use minRamGB as the size-tier signal, not params.
            val isMini = model.parameterCount <= 1_000_000_000L
            val isBig = model.minRamGB >= 6
            // Hot-path tiers get FA + Q8_0 KV; safe tiers stay on defaults.
            val useFlashAttn = tier == DeviceTier.HIGH_END || tier == DeviceTier.FLAGSHIP
            val kvQuant = if (useFlashAttn) KvCacheType.Q8_0 else KvCacheType.F16
            val useMlock = tier == DeviceTier.FLAGSHIP

            return when (tier) {
                DeviceTier.FLAGSHIP -> {
                    when {
                        isMini -> {
                            template(
                                requestedCtx = 8192,
                                nBatch = 512,
                                nUbatch = 128,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 3072,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }

                        isBig -> {
                            template(
                                requestedCtx = 4096,
                                nBatch = 384,
                                nUbatch = 96,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 2048,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }

                        else -> {
                            template(
                                requestedCtx = 6144,
                                nBatch = 512,
                                nUbatch = 128,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 2560,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }
                    }
                }

                DeviceTier.HIGH_END -> {
                    when {
                        isMini -> {
                            template(
                                requestedCtx = 6144,
                                nBatch = 512,
                                nUbatch = 128,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 2560,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }

                        isBig -> {
                            template(
                                requestedCtx = 3072,
                                nBatch = 256,
                                nUbatch = 64,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 1536,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }

                        else -> {
                            template(
                                requestedCtx = 4096,
                                nBatch = 384,
                                nUbatch = 96,
                                threadsGen = 4,
                                threadsBatch = 6,
                                maxTokens = 2048,
                                model = model,
                                useFlashAttn = useFlashAttn,
                                kvQuant = kvQuant,
                                useMlock = useMlock,
                            )
                        }
                    }
                }

                DeviceTier.MID_RANGE -> {
                    if (isMini) {
                        template(
                            requestedCtx = 4096,
                            nBatch = 384,
                            nUbatch = 96,
                            threadsGen = 4,
                            threadsBatch = 6,
                            maxTokens = 2048,
                            model = model,
                            useFlashAttn = useFlashAttn,
                            kvQuant = kvQuant,
                            useMlock = useMlock,
                        )
                    } else {
                        template(
                            requestedCtx = 3072,
                            nBatch = 256,
                            nUbatch = 64,
                            threadsGen = 3,
                            threadsBatch = 5,
                            maxTokens = 1536,
                            model = model,
                            useFlashAttn = useFlashAttn,
                            kvQuant = kvQuant,
                            useMlock = useMlock,
                        )
                    }
                }

                DeviceTier.BUDGET -> {
                    if (isMini) {
                        template(
                            requestedCtx = 2048,
                            nBatch = 256,
                            nUbatch = 64,
                            threadsGen = 3,
                            threadsBatch = 4,
                            maxTokens = 1024,
                            model = model,
                            useFlashAttn = useFlashAttn,
                            kvQuant = kvQuant,
                            useMlock = useMlock,
                        )
                    } else {
                        template(
                            requestedCtx = 2048,
                            nBatch = 256,
                            nUbatch = 64,
                            threadsGen = 3,
                            threadsBatch = 4,
                            maxTokens = 768,
                            model = model,
                            useFlashAttn = useFlashAttn,
                            kvQuant = kvQuant,
                            useMlock = useMlock,
                        )
                    }
                }

                DeviceTier.LOW_END -> {
                    template(
                        requestedCtx = 1536,
                        nBatch = 128,
                        nUbatch = 32,
                        threadsGen = 2,
                        threadsBatch = 3,
                        maxTokens = 768,
                        model = model,
                        useFlashAttn = useFlashAttn,
                        kvQuant = kvQuant,
                        useMlock = useMlock,
                    )
                }
            }
        }

        private fun template(
            requestedCtx: Int,
            nBatch: Int,
            nUbatch: Int,
            threadsGen: Int,
            threadsBatch: Int,
            maxTokens: Int,
            model: LlmModel,
            useFlashAttn: Boolean,
            kvQuant: KvCacheType,
            useMlock: Boolean,
        ): InferenceTuning {
            val ctx = minOf(requestedCtx, model.maxContextTokens)
            val cappedMax = minOf(maxTokens, ctx - 64)
            return InferenceTuning(
                nCtx = ctx,
                nBatch = nBatch,
                nUbatch = nUbatch,
                useFlashAttn = useFlashAttn,
                kvQuant = kvQuant,
                useMlock = useMlock,
                threadsGen = threadsGen,
                threadsBatch = threadsBatch,
                maxTokens = cappedMax.coerceAtLeast(256),
            )
        }
    }
}
