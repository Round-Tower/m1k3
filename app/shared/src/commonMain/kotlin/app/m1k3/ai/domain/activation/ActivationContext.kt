package app.m1k3.ai.domain.activation

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock

/**
 * ActivationContext - Unified activation payload.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * This is the single data structure that flows from any activation path
 * (widget, hotword, share, intent, deep link) into the core M1K3 engine.
 * All activation paths funnel into this context.
 *
 * **Usage:**
 * ```kotlin
 * val context = ActivationContext(
 *     source = ActivationSource.Widget,
 *     mode = M1K3Mode.Reading,
 *     input = "Simplify this text"
 * )
 * engine.activate(context)
 * ```
 *
 * @property source How M1K3 was activated
 * @property mode Operational mode requested
 * @property input Text input or query (optional)
 * @property payload Binary payload for share extension flows (optional)
 * @property mimeType MIME type of the payload (optional)
 * @property sessionId Unique session identifier (auto-generated)
 * @property timestamp Activation timestamp in epoch milliseconds
 * @property provenanceTag MurphySig provenance metadata (optional)
 */
data class ActivationContext @OptIn(ExperimentalUuidApi::class) constructor(
    val source: ActivationSource,
    val mode: M1K3Mode = M1K3Mode.Default,
    val input: String? = null,
    val payload: ByteArray? = null,
    val mimeType: String? = null,
    val sessionId: String = Uuid.random().toString(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val provenanceTag: ProvenanceTag? = null
) {
    /**
     * ByteArray-aware equality.
     *
     * Data class generated equals() doesn't handle ByteArray correctly,
     * so we override to use contentEquals for the payload field.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActivationContext) return false
        return source == other.source &&
                mode == other.mode &&
                input == other.input &&
                payload.contentEquals(other.payload) &&
                mimeType == other.mimeType &&
                sessionId == other.sessionId &&
                timestamp == other.timestamp &&
                provenanceTag == other.provenanceTag
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + (input?.hashCode() ?: 0)
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (provenanceTag?.hashCode() ?: 0)
        return result
    }
}

/**
 * Null-safe ByteArray content equality.
 */
private fun ByteArray?.contentEquals(other: ByteArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return this == other
    return this.contentEquals(other)
}
