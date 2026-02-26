package app.m1k3.ai.domain.activation

/**
 * ActivationSource - How M1K3 was activated.
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Each sealed subtype represents a distinct activation path / user state:
 * - **Hotword**: Hands-free, ambient ("Hey Mike")
 * - **Widget**: Glanceable tap from home/lock screen
 * - **ShareExtension**: Cross-app contextual share
 * - **AppIntent**: System-integrated (Siri / Spotlight / Shortcuts)
 * - **DeepLink**: Programmatic URI-based activation
 * - **GoogleAssistant**: "Hey Google, ask M1K3…" (Android)
 * - **GeminiExtension**: Gemini Extensions capability (Android)
 * - **Shortcut**: Direct shortcut invocation
 *
 * @see ActivationContext for the full activation payload
 */
sealed class ActivationSource {
    data object Hotword : ActivationSource()
    data object Widget : ActivationSource()
    data object ShareExtension : ActivationSource()
    data object AppIntent : ActivationSource()
    data object DeepLink : ActivationSource()
    data object GoogleAssistant : ActivationSource()
    data object GeminiExtension : ActivationSource()
    data object Shortcut : ActivationSource()

    companion object {
        /**
         * Parse raw string into ActivationSource.
         *
         * Case-insensitive. Unknown values default to [DeepLink].
         *
         * @param raw String from URI query param or intent extra
         * @return Corresponding ActivationSource
         */
        fun from(raw: String): ActivationSource = when (raw.lowercase()) {
            "hotword" -> Hotword
            "widget" -> Widget
            "share" -> ShareExtension
            "intent" -> AppIntent
            "google" -> GoogleAssistant
            "gemini" -> GeminiExtension
            "shortcut" -> Shortcut
            else -> DeepLink
        }
    }
}
