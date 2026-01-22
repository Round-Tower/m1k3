package app.m1k3.ai.assistant.memory

import app.m1k3.ai.domain.memory.ConversationContext
import app.m1k3.ai.domain.memory.MemorySearchResult
import app.m1k3.ai.domain.memory.MemoryStats
import app.m1k3.ai.domain.repositories.MemoryRepository

/**
 * MemoryRepositoryImpl - Android implementation of domain MemoryRepository
 *
 * Adapter that wraps SemanticMemoryManager to conform to the domain interface.
 *
 * **Design Note:**
 * Currently a thin adapter around SemanticMemoryManager. In the future, we should
 * refactor SemanticMemoryManager to be more modular (separate chunking, embedding,
 * storage layers) so that domain use cases can orchestrate these steps directly.
 *
 * **Known Limitations:**
 * - Importance parameter is passed through but SemanticMemoryManager recalculates it internally
 * - This creates some redundancy with CreateMemoryUseCase which also calculates importance
 * - Future refactoring should move importance calculation fully to use case layer
 */
class MemoryRepositoryImpl(
    private val semanticMemoryManager: SemanticMemoryManager
) : MemoryRepository {

    override suspend fun initialize(): Result<Unit> {
        return semanticMemoryManager.initialize()
    }

    /**
     * Create memory from message with pre-calculated importance
     *
     * Note: SemanticMemoryManager currently recalculates importance internally,
     * so this parameter is used only for the importance threshold check.
     */
    override suspend fun createMemoryFromMessage(
        messageId: String,
        content: String,
        importance: Float
    ): Result<Int> {
        // Skip low-importance content (threshold: 0.3)
        if (importance < 0.3f) {
            return Result.success(0)
        }

        // SemanticMemoryManager will recalculate importance internally
        // using ImportanceCalculator, but that's acceptable for now.
        // Future refactoring should remove duplicate importance calculation.
        return semanticMemoryManager.createMemoryFromMessage(
            messageId = messageId,
            content = content,
            context = ConversationContext(isCurrentConversation = true)
        )
    }

    override suspend fun searchMemories(
        query: String,
        topK: Int,
        minSimilarity: Float
    ): Result<List<MemorySearchResult>> {
        return semanticMemoryManager.searchMemories(
            query = query,
            topK = topK,
            minSimilarity = minSimilarity
        ).map { androidResults ->
            // Convert Android MemorySearchResult to domain MemorySearchResult
            androidResults.map { androidResult ->
                MemorySearchResult(
                    id = androidResult.id,
                    content = androidResult.content,
                    importance = androidResult.importance,
                    similarity = androidResult.similarity,
                    chunkIndex = androidResult.chunkIndex,
                    chunkTotal = androidResult.chunkTotal,
                    messageId = androidResult.messageId,
                    createdAt = androidResult.createdAt
                )
            }
        }
    }

    override suspend fun getHighImportanceMemories(limit: Int): Result<List<MemorySearchResult>> {
        return semanticMemoryManager.getHighImportanceMemories(limit).map { androidResults ->
            // Convert Android MemorySearchResult to domain MemorySearchResult
            androidResults.map { androidResult ->
                MemorySearchResult(
                    id = androidResult.id,
                    content = androidResult.content,
                    importance = androidResult.importance,
                    similarity = androidResult.similarity,
                    chunkIndex = androidResult.chunkIndex,
                    chunkTotal = androidResult.chunkTotal,
                    messageId = androidResult.messageId,
                    createdAt = androidResult.createdAt
                )
            }
        }
    }

    override suspend fun getMemoryStats(): Result<MemoryStats> {
        return semanticMemoryManager.getMemoryStats().map { androidStats ->
            // Convert Android MemoryStats to domain MemoryStats
            MemoryStats(
                totalMemories = androidStats.totalMemories,
                averageImportance = androidStats.averageImportance,
                hasVectorIndex = androidStats.hasVectorIndex
            )
        }
    }

    override suspend fun storeChunkWithEmbedding(
        messageId: String,
        content: String,
        importance: Float,
        chunkIndex: Int,
        chunkTotal: Int,
        chunkTokens: Int,
        embedding: FloatArray,
        projectId: String?
    ): Result<String> {
        // Note: projectId is ignored here because SemanticMemoryManager is already
        // scoped to a specific project. In the future, we might need to support
        // cross-project memory storage.
        return semanticMemoryManager.storeChunkWithEmbedding(
            messageId = messageId,
            content = content,
            importance = importance,
            chunkIndex = chunkIndex,
            chunkTotal = chunkTotal,
            chunkTokens = chunkTokens,
            embedding = embedding
        )
    }

    override suspend fun shutdown(): Result<Unit> {
        return semanticMemoryManager.shutdown()
    }
}
