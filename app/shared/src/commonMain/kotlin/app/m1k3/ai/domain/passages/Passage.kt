package app.m1k3.ai.domain.passages

/**
 * Passage — one retrievable segment of a [Source].
 *
 * Domain entity. Pure Kotlin, no platform dependencies.
 *
 * Mirrors the boundary helpers on [app.m1k3.ai.domain.memory.MemoryChunk] so
 * callers that consume both conversation memory and document-backed context
 * can reason about ordering the same way.
 *
 * @property id Stable identifier, namespaced by [sourceId] for traceability.
 * @property sourceId Parent [Source.id].
 * @property sequence Zero-based ordinal within the parent source.
 * @property content The passage text.
 * @property tokenCount Estimated token count (from the active tokenizer).
 * @property totalPassagesInSource Total passages produced for this source.
 */
data class Passage(
    val id: String,
    val sourceId: String,
    val sequence: Int,
    val content: String,
    val tokenCount: Int,
    val totalPassagesInSource: Int,
) {
    val isFirstPassage: Boolean
        get() = sequence == 0

    val isLastPassage: Boolean
        get() = sequence == totalPassagesInSource - 1
}
