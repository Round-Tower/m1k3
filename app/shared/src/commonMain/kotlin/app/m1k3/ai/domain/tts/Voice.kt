package app.m1k3.ai.domain.tts

/**
 * Voice - TTS voice options for Kokoro-82M.
 *
 * Domain entity — pure Kotlin, no platform dependencies.
 *
 * Voice IDs match asset filenames exactly:
 *   Voice.Kokoro.Daniel.id == "bm_daniel" → loads bm_daniel.bin
 *   Voice.Kokoro.Bella.id  == "af_bella"  → loads af_bella.bin
 *
 * Kokoro naming convention: {locale_gender}_{name}
 *   af = American Female, am = American Male
 *   bf = British Female,  bm = British Male
 */
sealed class Voice(
    val id: String,
    val displayName: String,
    val language: String
) {
    sealed class Kokoro(
        id: String,
        displayName: String,
        language: String = "en"
    ) : Voice(id, displayName, language) {

        /**
         * Daniel — British Male. M1K3's signature voice.
         * Professional, warm, conversational. The default.
         */
        data object Daniel : Kokoro("bm_daniel", "Daniel")

        /**
         * Bella — American Female. Warm and natural.
         * Great for casual conversation and companion-mode responses.
         */
        data object Bella : Kokoro("af_bella", "Bella")

        /**
         * Emma — British Female. Clear and articulate.
         * Shares Daniel's British accent — feels cohesive as M1K3's alternate voice.
         */
        data object Emma : Kokoro("bf_emma", "Emma")
    }

    companion object {
        val default: Voice get() = Kokoro.Daniel

        fun all(): List<Voice> = listOf(
            Kokoro.Daniel,
            Kokoro.Bella,
            Kokoro.Emma
        )

        fun findById(id: String): Voice? = all().find { it.id == id }
    }
}
