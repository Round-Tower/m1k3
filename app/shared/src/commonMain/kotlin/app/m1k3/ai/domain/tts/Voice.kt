package app.m1k3.ai.domain.tts

/**
 * Voice - TTS voice options
 *
 * Domain entity - Pure Kotlin, no platform dependencies.
 *
 * Represents available voices for text-to-speech synthesis.
 * Kokoro voices are the primary implementation with Daniel as default.
 *
 * **Usage:**
 * ```kotlin
 * val voice = Voice.default  // Daniel (British Male)
 * val allVoices = Voice.all()
 * ```
 */
sealed class Voice(
    val id: String,
    val displayName: String,
    val language: String
) {
    /**
     * Kokoro TTS voices - SOTA quality, 82M parameter model
     *
     * All Kokoro voices use the same model with different voice embeddings.
     * Daniel is the default for M1K3.
     */
    sealed class Kokoro(
        id: String,
        displayName: String,
        language: String = "en"
    ) : Voice(id, displayName, language) {

        /**
         * Daniel - British Male (M1K3's signature voice)
         *
         * Professional, precise, conversational tone.
         * Highest quality male voice in Kokoro lineup.
         */
        data object Daniel : Kokoro("bm_daniel", "Daniel (British Male)")

        /**
         * American Female - Clear, friendly voice
         */
        data object AmericanFemale : Kokoro("af", "American Female")

        /**
         * American Male - Natural, conversational voice
         */
        data object AmericanMale : Kokoro("am", "American Male")

        /**
         * British Female - Refined, articulate voice
         */
        data object BritishFemale : Kokoro("bf", "British Female")

        /**
         * British Male - Classic British accent
         */
        data object BritishMale : Kokoro("bm", "British Male")
    }

    companion object {
        /**
         * Default voice - Daniel (British Male)
         *
         * M1K3's signature voice, used across all platforms.
         */
        val default: Voice get() = Kokoro.Daniel

        /**
         * Get all available voices
         *
         * @return List of all Voice options
         */
        fun all(): List<Voice> = listOf(
            Kokoro.Daniel,
            Kokoro.AmericanFemale,
            Kokoro.AmericanMale,
            Kokoro.BritishFemale,
            Kokoro.BritishMale
        )

        /**
         * Find voice by ID
         *
         * @param id Voice identifier
         * @return Voice if found, null otherwise
         */
        fun findById(id: String): Voice? = all().find { it.id == id }
    }
}
