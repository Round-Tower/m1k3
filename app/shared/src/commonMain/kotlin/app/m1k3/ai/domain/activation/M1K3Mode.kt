package app.m1k3.ai.domain.activation

/**
 * M1K3Mode - Operational mode for activation.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Each mode maps to a distinct user intent:
 * - **Default**: General chat / assistant
 * - **Reading**: Lexy-style reading assistance (simplification, dyslexia support)
 * - **Dictation**: Voice-to-text transcription mode
 * - **Summarise**: Content summarisation
 * - **Explain**: Detailed explanation of content
 *
 * @property id Wire-format identifier used in URIs and intents
 */
enum class M1K3Mode(val id: String) {
    Default("default"),
    Reading("reading"),
    Dictation("dictation"),
    Summarise("summarise"),
    Explain("explain");

    companion object {
        /**
         * Parse raw string into M1K3Mode.
         *
         * Case-insensitive. Unknown values default to [Default].
         *
         * @param raw String from URI query param or intent extra
         * @return Corresponding M1K3Mode
         */
        fun from(raw: String): M1K3Mode =
            entries.find { it.id.equals(raw, ignoreCase = true) } ?: Default
    }
}
