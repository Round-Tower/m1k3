package app.m1k3.ai.domain.ai

/**
 * GenerationConfig - AI generation configuration
 *
 * Configuration for LLM text generation. All parameters are nullable:
 * - null values indicate "use engine default"
 *
 * Design philosophy: **Graceful degradation**
 * If an engine doesn't support a parameter (e.g., Llamatik lacks sampling APIs),
 * it should ignore it gracefully without errors.
 *
 * @param maxTokens Maximum tokens to generate (null = use engine default, typically 128-512)
 * @param temperature Sampling temperature 0.0-1.0 (null = engine doesn't support or use default 0.7)
 *                    - 0.0 = deterministic (greedy decoding)
 *                    - 0.7 = balanced creativity
 *                    - 1.0 = maximum creativity
 *                    Note: Llamatik ignores this - use prompt engineering instead
 * @param systemPrompt Custom system prompt (null = use engine's default M1K3 prompt)
 * @param userContext User-specific context for personalization (e.g., {"name": "Alice"})
 * @param knowledgeContext RAG-retrieved knowledge to inject into prompt
 * @param topP Nucleus sampling top-P (null = engine doesn't support or use default 0.95)
 * @param topK Top-K sampling (null = engine doesn't support or use default 40)
 * @param minP Minimum probability threshold (null = engine doesn't support or use default 0.05)
 * @param repetitionPenalty Penalty for repeated tokens (null = engine doesn't support or use default 1.1)
 */
data class GenerationConfig(
    val maxTokens: Int? = null,
    val temperature: Float? = 1.0f,
    val systemPrompt: String? = null,
    val userContext: Map<String, String>? = null,
    val knowledgeContext: String? = null,
    val topP: Float? = 0.95f,
    val topK: Int? = 64,
    val minP: Float? = 0.0f,
    val repetitionPenalty: Float? = 1.0f
)
