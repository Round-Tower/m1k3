package app.m1k3.ai.domain.passages.services

import app.m1k3.ai.domain.memory.SimpleTokenCounter
import app.m1k3.ai.domain.memory.TokenCounter
import app.m1k3.ai.domain.passages.Passage

/**
 * Passage Chunker — paragraph-aware greedy packer that produces [Passage]
 * values from raw source text.
 *
 * Domain service. Pure Kotlin, no platform dependencies. Content-agnostic —
 * works for plain text, markdown, pasted notes, transcripts, and anything
 * else that lives in a [app.m1k3.ai.domain.passages.Source].
 *
 * **Algorithm (MVP):**
 * ```
 * 1. Split content on blank-line paragraph boundaries.
 * 2. Any paragraph exceeding maxChunkTokens is pre-split on word boundaries
 *    into pieces of at most maxChunkTokens each.
 * 3. Greedy-pack the resulting pieces into passages up to maxChunkTokens.
 *    Pieces within the same passage are rejoined with a blank line.
 * 4. Drop passages below minChunkTokens. If that filter nukes everything,
 *    the original pack is returned unfiltered (better a small passage than none).
 * 5. Emit Passage values with monotonic sequence numbers.
 * ```
 *
 * Out of scope for this slice: passage overlap, sentence-level fallback for
 * oversize paragraphs (beyond word splitting), structural awareness of
 * Markdown headings. Those land when ingestion earns them.
 *
 * @param tokenCounter Strategy for estimating token count per passage.
 * @param maxChunkTokens Hard upper bound per passage.
 * @param minChunkTokens Soft lower bound; enforced only when at least one
 *                      passage meets the threshold.
 */
open class PassageChunker(
    private val tokenCounter: TokenCounter = SimpleTokenCounter(),
    private val maxChunkTokens: Int = 300,
    private val minChunkTokens: Int = 20,
) {
    open fun chunk(
        sourceId: String,
        content: String,
    ): List<Passage> {
        if (content.isBlank()) return emptyList()

        val paragraphs =
            content
                .split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return emptyList()

        val packables =
            paragraphs.flatMap { paragraph ->
                if (tokenCounter.countTokens(paragraph) > maxChunkTokens) {
                    splitOnWords(paragraph, maxChunkTokens)
                } else {
                    listOf(paragraph)
                }
            }

        val packed = greedyPack(packables)
        val filtered = packed.filter { tokenCounter.countTokens(it) >= minChunkTokens }
        val final = if (filtered.isEmpty()) packed else filtered

        return final.mapIndexed { index, text ->
            Passage(
                id = "${sourceId}_passage_$index",
                sourceId = sourceId,
                sequence = index,
                content = text,
                tokenCount = tokenCounter.countTokens(text),
                totalPassagesInSource = final.size,
            )
        }
    }

    private fun greedyPack(pieces: List<String>): List<String> {
        val packed = mutableListOf<String>()
        val current = StringBuilder()
        var currentTokens = 0

        for (piece in pieces) {
            val pieceTokens = tokenCounter.countTokens(piece)

            if (current.isNotEmpty() && currentTokens + pieceTokens > maxChunkTokens) {
                packed.add(current.toString())
                current.clear()
                currentTokens = 0
            }

            if (current.isNotEmpty()) current.append("\n\n")
            current.append(piece)
            currentTokens += pieceTokens
        }

        if (current.isNotEmpty()) packed.add(current.toString())
        return packed
    }

    private fun splitOnWords(
        text: String,
        budget: Int,
    ): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        var count = 0

        for (word in words) {
            if (count >= budget && current.isNotEmpty()) {
                pieces.add(current.toString())
                current.clear()
                count = 0
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
            count++
        }

        if (current.isNotEmpty()) pieces.add(current.toString())
        return pieces
    }
}
