package app.m1k3.ai.domain.passages

/**
 * Source — metadata for a unit of retrievable context.
 *
 * Domain entity. Pure Kotlin, no platform dependencies.
 *
 * A Source owns one or more [Passage] rows. Every piece of retrievable text
 * in M1K3 lands under a Source — imported files, pasted notes, chat-derived
 * memories (later), transcripts (later). The [kind] discriminates origin and
 * feeds retrieval ranking.
 *
 * @property id Stable identifier minted at import time (UUID-shaped).
 * @property uri Addressable origin: `file:///…`, `note:…`, `memory:…`, etc.
 * @property kind Origin format. See [SourceKind] for day-one coverage.
 * @property title Human-readable label, typically derived from the source.
 * @property byteSize Raw byte size of the ingested content.
 * @property chunkCount Number of passages produced by chunking.
 * @property importedAt Unix epoch millis when ingestion completed.
 */
data class Source(
    val id: String,
    val uri: String,
    val kind: SourceKind,
    val title: String,
    val byteSize: Int,
    val chunkCount: Int,
    val importedAt: Long,
) {
    val isEmpty: Boolean
        get() = chunkCount == 0
}

/**
 * Day-one source kinds. New kinds earn entry by shipping an ingestion path
 * alongside them — no reserved values. Planned follow-ups include
 * WEB, PDF, NOTE, MEMORY, SYSTEM, TRANSCRIPT.
 */
enum class SourceKind {
    TEXT,
    MARKDOWN,
}
