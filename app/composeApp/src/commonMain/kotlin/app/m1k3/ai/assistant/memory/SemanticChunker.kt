package app.m1k3.ai.assistant.memory

import kotlin.math.min

/**
 * 間 AI - Semantic Chunking Strategy
 *
 * Splits long text into semantic chunks for memory storage.
 * Chunks are 100-300 tokens with semantic boundaries and 20-token overlap.
 *
 * **Philosophy:**
 * - Preserve meaning by respecting sentence boundaries
 * - Maintain context with overlapping chunks
 * - Filter out trivial chunks (too short)
 *
 * **Algorithm:**
 * ```
 * 1. Split on semantic boundaries (sentence-level)
 *    - Regex pattern: (?<=[.!?])\s+
 *    - Respects punctuation (periods, exclamation marks, questions)
 *
 * 2. Group sentences into chunks
 *    - Accumulate sentences until token limit reached
 *    - If single sentence >maxTokens, split with overlap
 *
 * 3. Add overlap for context preservation
 *    - Last 20 tokens of chunk N become first 20 of chunk N+1
 *    - Ensures semantic continuity across chunks
 *
 * 4. Filter chunks below minimum threshold
 *    - Discard chunks <100 tokens (too trivial)
 *    - Return valid chunks with metadata
 * ```
 *
 * **Example:**
 * ```
 * Input: "First sentence. Second sentence. Third sentence." (450 tokens)
 *
 * Output:
 * Chunk 0: "First sentence. Second sentence." (200 tokens)
 * Chunk 1: "Second sentence. Third sentence." (250 tokens)
 *          ^^^^^^^^^^^^^^^^^ (overlap from Chunk 0)
 * ```
 *
 * **Parameters:**
 * - minChunkTokens: 100 (discard smaller chunks)
 * - maxChunkTokens: 300 (split larger chunks)
 * - overlapTokens: 20 (context preservation)
 *
 * **Usage:**
 * ```kotlin
 * val chunker = SemanticChunker()
 * val chunks = chunker.chunkMessage(
 *     messageContent = longText,
 *     messageId = "msg-123",
 *     projectId = "proj-abc",
 *     timestamp = System.currentTimeMillis(),
 *     role = "user"
 * )
 * println("Created ${chunks.size} chunks")
 * ```
 */
class SemanticChunker(
    private val tokenCounter: TokenCounter = SimpleTokenCounter(),
    private val minChunkTokens: Int = 100,
    private val maxChunkTokens: Int = 300,
    private val overlapTokens: Int = 20
) {

    /**
     * Chunk message content into semantic segments
     *
     * @param messageContent Text content from message
     * @param messageId Associated message ID
     * @param projectId Associated project ID
     * @param timestamp Message timestamp
     * @param role Message role (user/assistant)
     * @return List of chunks meeting size requirements
     */
    fun chunkMessage(
        messageContent: String,
        messageId: String,
        projectId: String?,
        timestamp: Long,
        role: String
    ): List<Chunk> {
        if (messageContent.isBlank()) return emptyList()

        // Try semantic boundaries first (sentences)
        val semanticSegments = splitOnSemanticBoundaries(messageContent)

        // Process each segment into valid chunks
        val chunks = semanticSegments.flatMap { segment ->
            val tokenCount = countTokens(segment)

            when {
                tokenCount < minChunkTokens -> {
                    // Too small, skip
                    emptyList()
                }
                tokenCount <= maxChunkTokens -> {
                    // Perfect size, keep as-is
                    listOf(createChunk(segment, messageId, projectId, timestamp, role))
                }
                else -> {
                    // Too large, split with overlap
                    splitWithOverlap(segment, messageId, projectId, timestamp, role)
                }
            }
        }

        return chunks
    }

    /**
     * Split text on semantic boundaries (sentences)
     */
    private fun splitOnSemanticBoundaries(text: String): List<String> {
        val segments = mutableListOf<String>()
        val currentSegment = StringBuilder()

        // Split on sentences (. ! ?)
        val sentences = text.split(Regex("[.!?]+\\s+")).filter { it.isNotBlank() }

        for (sentence in sentences) {
            val currentTokens = countTokens(currentSegment.toString())
            val sentenceTokens = countTokens(sentence)

            if (currentTokens + sentenceTokens > maxChunkTokens && currentSegment.isNotEmpty()) {
                // Current segment is full, save and start new one
                segments.add(currentSegment.toString().trim())
                currentSegment.clear()
            }

            currentSegment.append(sentence).append(". ")

            // Check for semantic completeness (transition words)
            if (isSemanticBoundary(sentence) && currentTokens >= minChunkTokens) {
                segments.add(currentSegment.toString().trim())
                currentSegment.clear()
            }
        }

        // Add remaining content
        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString().trim())
        }

        return segments.filter { it.isNotBlank() }
    }

    /**
     * Detect semantic boundary indicators (topic transitions)
     */
    private fun isSemanticBoundary(sentence: String): Boolean {
        val boundaryIndicators = listOf(
            // Topic transitions
            "anyway", "meanwhile", "however", "but", "so",
            // Conclusions
            "therefore", "thus", "in conclusion",
            // New topics
            "speaking of", "by the way", "oh",
            // Time transitions
            "later", "then", "after", "before", "next", "finally"
        )

        val lowerSentence = sentence.lowercase()
        return boundaryIndicators.any { lowerSentence.contains(it) }
    }

    /**
     * Split large segment with overlap for context preservation
     */
    private fun splitWithOverlap(
        text: String,
        messageId: String,
        projectId: String?,
        timestamp: Long,
        role: String
    ): List<Chunk> {
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<Chunk>()

        var startIndex = 0
        while (startIndex < words.size) {
            // Determine end index (approximate 2 tokens per word)
            val approxWords = maxChunkTokens / 2
            var endIndex = min(startIndex + approxWords, words.size)

            // Adjust to sentence boundary if possible
            val chunkText = words.subList(startIndex, endIndex).joinToString(" ")
            val lastPeriod = chunkText.lastIndexOf('.')

            if (lastPeriod > chunkText.length / 2) {
                // Good sentence boundary found
                val wordsBeforePeriod = chunkText.substring(0, lastPeriod).split(Regex("\\s+")).size
                endIndex = startIndex + wordsBeforePeriod
            }

            // Create chunk
            val chunk = words.subList(startIndex, endIndex).joinToString(" ")

            if (countTokens(chunk) >= minChunkTokens) {
                chunks.add(createChunk(chunk, messageId, projectId, timestamp, role))
            }

            // Move forward with overlap
            val overlapWords = overlapTokens / 2 // Approximate words for overlap
            startIndex = if (endIndex == words.size) {
                endIndex // End of text
            } else {
                kotlin.math.max(startIndex + 1, endIndex - overlapWords)
            }
        }

        return chunks
    }

    /**
     * Create chunk data class
     */
    private fun createChunk(
        content: String,
        messageId: String,
        projectId: String?,
        timestamp: Long,
        role: String
    ): Chunk {
        return Chunk(
            id = generateChunkId(messageId, content),
            content = content,
            messageId = messageId,
            projectId = projectId,
            timestamp = timestamp,
            role = role,
            tokenCount = countTokens(content)
        )
    }

    /**
     * Generate unique chunk ID from message ID + content hash
     */
    private fun generateChunkId(messageId: String, content: String): String {
        val hash = content.hashCode().toString(16)
        return "${messageId}_chunk_$hash"
    }

    /**
     * Count tokens in text using token counter
     */
    private fun countTokens(text: String): Int {
        return tokenCounter.countTokens(text)
    }
}

/**
 * Chunk data class
 *
 * Represents a semantic segment of a message for memory storage.
 */
data class Chunk(
    val id: String,
    val content: String,
    val messageId: String,
    val projectId: String?,
    val timestamp: Long,
    val role: String,
    val tokenCount: Int
)
