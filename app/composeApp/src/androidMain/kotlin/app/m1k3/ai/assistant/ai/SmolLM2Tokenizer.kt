package app.m1k3.ai.assistant.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 間 AI - SmolLM2 Tokenizer
 *
 * BPE tokenizer for SmolLM2-360M-Instruct model.
 * Converts text to token IDs and vice versa.
 *
 * Vocabulary:
 * - 49,152 tokens
 * - GPT-2 style BPE
 * - Special tokens: <|endoftext|>, <|im_start|>, <|im_end|>
 */
class SmolLM2Tokenizer(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var vocabMap: Map<String, Int> = emptyMap()
    private var idToToken: Map<Int, String> = emptyMap()
    private var merges: List<Pair<String, String>> = emptyList()

    // Special tokens
    private val BOS_TOKEN = "<|im_start|>"
    private val EOS_TOKEN = "<|im_end|>"
    private val PAD_TOKEN = "<|endoftext|>"

    private val BOS_ID = 1
    private val EOS_ID = 2

    var isInitialized = false
        private set

    /**
     * Load tokenizer vocabulary and merges from assets
     */
    fun initialize() {
        try {
            // Load vocabulary (token -> ID mapping)
            vocabMap = loadVocabulary()
            idToToken = vocabMap.entries.associate { (k, v) -> v to k }

            // Load BPE merges
            merges = loadMerges()

            isInitialized = true

            println("✅ Tokenizer initialized")
            println("   Vocabulary size: ${vocabMap.size}")
            println("   Merges: ${merges.size}")

        } catch (e: Exception) {
            // Fallback to simple character-level tokenization if files missing
            println("⚠️  Tokenizer files not found, using simple tokenization")
            vocabMap = createSimpleVocab()
            idToToken = vocabMap.entries.associate { (k, v) -> v to k }
            isInitialized = true
        }
    }

    /**
     * Encode text to token IDs
     */
    fun encode(text: String): LongArray {
        if (!isInitialized) {
            throw IllegalStateException("Tokenizer not initialized")
        }

        // Simple word-level tokenization for now
        // TODO: Implement proper BPE encoding
        val words = text.lowercase().split(Regex("\\s+"))

        val tokens = mutableListOf<Long>()
        tokens.add(BOS_ID.toLong()) // Start token

        for (word in words) {
            val tokenId = vocabMap[word] ?: vocabMap["<unk>"] ?: 0
            tokens.add(tokenId.toLong())
        }

        tokens.add(EOS_ID.toLong()) // End token

        return tokens.toLongArray()
    }

    /**
     * Decode token IDs to text
     */
    fun decode(tokenIds: LongArray): String {
        if (!isInitialized) {
            throw IllegalStateException("Tokenizer not initialized")
        }

        val words = tokenIds
            .filter { it != BOS_ID.toLong() && it != EOS_ID.toLong() }
            .mapNotNull { idToToken[it.toInt()] }

        return words.joinToString(" ")
    }

    /**
     * Get vocabulary size
     */
    fun getVocabSize(): Int = vocabMap.size

    // Helper functions

    private fun loadVocabulary(): Map<String, Int> {
        return try {
            val inputStream = context.assets.open("tokenizer/vocab.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonText = reader.readText()
            reader.close()

            json.decodeFromString<Map<String, Int>>(jsonText)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadMerges(): List<Pair<String, String>> {
        return try {
            val inputStream = context.assets.open("tokenizer/merges.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            val merges = mutableListOf<Pair<String, String>>()

            reader.forEachLine { line ->
                if (line.isNotBlank() && !line.startsWith("#")) {
                    val parts = line.split(" ")
                    if (parts.size == 2) {
                        merges.add(parts[0] to parts[1])
                    }
                }
            }

            reader.close()
            merges

        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Create simple vocabulary for testing without tokenizer files
     */
    private fun createSimpleVocab(): Map<String, Int> {
        val commonWords = listOf(
            "<pad>", "<|im_start|>", "<|im_end|>", "<unk>",
            "hello", "hi", "how", "are", "you", "i", "am", "fine",
            "what", "is", "your", "name", "my", "间", "ai",
            "can", "help", "me", "yes", "no", "please", "thank", "thanks",
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "as", "about"
        )

        return commonWords.mapIndexed { index, word -> word to index }.toMap()
    }
}

/**
 * Tokenizer configuration
 */
@Serializable
data class TokenizerConfig(
    val vocab_size: Int = 49152,
    val model_type: String = "gpt2",
    val bos_token: String = "<|im_start|>",
    val eos_token: String = "<|im_end|>",
    val unk_token: String = "<unk>",
    val pad_token: String = "<|endoftext|>"
)
