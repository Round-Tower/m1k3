package app.m1k3.ai.domain.embedding

/**
 * Embedding Task Type - Task-specific prompts for embedding models.
 *
 * Embedding models like Gemma support different prompts for different use cases,
 * which can improve retrieval quality for specific tasks.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * @see app.m1k3.ai.domain.repositories.EmbeddingRepository
 */
enum class EmbeddingTaskType {
    /** General text retrieval (documents, passages) */
    RETRIEVAL,

    /** Search queries */
    QUERY,

    /** Classification tasks */
    CLASSIFICATION,

    /** Clustering/grouping */
    CLUSTERING,

    /** Document with title */
    DOCUMENT,

    /** Code retrieval */
    CODE
}
