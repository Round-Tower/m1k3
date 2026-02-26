package app.m1k3.ai.domain.activation

/**
 * ProvenanceTag - MurphySig provenance metadata.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Every M1K3 response carries a provenance tag recording the activation
 * lineage. Especially valuable for share extension flows where content
 * moves between apps.
 *
 * **Privacy guarantee**: [bytesTransmitted] is always 0 — all processing
 * is 100% on-device. Contains no PII (device class is model identifier only).
 *
 * @property sessionId Unique identifier for this activation session
 * @property activationSource How M1K3 was activated (raw string)
 * @property modelInfo Model identifier (e.g. "apple-foundation-models/v1")
 * @property deviceClass Hardware identifier (e.g. "iPhone16,2") — no PII
 * @property version MurphySig spec version
 * @property inputMimeType MIME type of the input content, if applicable
 * @property bytesTransmitted Always 0 — privacy guarantee
 */
data class ProvenanceTag(
    val sessionId: String,
    val activationSource: String,
    val modelInfo: String,
    val deviceClass: String,
    val version: String = "1.0",
    val inputMimeType: String? = null,
    val bytesTransmitted: Int = 0
)
