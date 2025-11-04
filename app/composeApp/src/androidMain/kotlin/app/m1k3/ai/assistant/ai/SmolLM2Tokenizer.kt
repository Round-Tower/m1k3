package app.m1k3.ai.assistant.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * M1K3 AI - SmolLM2 Tokenizer
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
            println("📚 Loading SmolLM2 tokenizer vocabulary...")
            vocabMap = loadVocabulary()

            if (vocabMap.isEmpty()) {
                println("⚠️  vocab.json is empty or failed to load, using fallback")
                vocabMap = createSimpleVocab()
            }

            idToToken = vocabMap.entries.associate { (k, v) -> v to k }

            // Load BPE merges
            merges = loadMerges()

            isInitialized = true

            val usingFallback = vocabMap.size < 1000  // Real GPT-2 vocab has 49152 tokens
            if (usingFallback) {
                println("⚠️  Using fallback vocabulary (${vocabMap.size} tokens)")
                println("   NOTE: Text may not have spaces - real vocab.json needed")
            } else {
                println("✅ Tokenizer initialized with GPT-2 BPE")
                println("   Vocabulary size: ${vocabMap.size}")
                println("   Merges: ${merges.size}")
            }

        } catch (e: Exception) {
            // Fallback to simple character-level tokenization if files missing
            println("⚠️  Tokenizer initialization error: ${e.message}")
            println("   Using fallback simple tokenization")
            vocabMap = createSimpleVocab()
            idToToken = vocabMap.entries.associate { (k, v) -> v to k }
            isInitialized = true
        }
    }

    /**
     * Encode text to token IDs using BPE
     */
    fun encode(text: String): LongArray {
        if (!isInitialized) {
            throw IllegalStateException("Tokenizer not initialized")
        }

        // Byte-level encoding
        val bytes = text.toByteArray(Charsets.UTF_8)

        // Convert bytes to byte tokens
        val byteTokens = bytes.map { byte ->
            val unsigned = byte.toInt() and 0xFF
            byteToChar(unsigned)
        }

        // Apply BPE merges
        var tokens = byteTokens.toMutableList()

        for ((first, second) in merges) {
            val pair = first + second
            var i = 0
            while (i < tokens.size - 1) {
                if (tokens[i] == first && tokens[i + 1] == second) {
                    tokens[i] = pair
                    tokens.removeAt(i + 1)
                } else {
                    i++
                }
            }
        }

        // Convert to token IDs
        val tokenIds = tokens.mapNotNull { token ->
            vocabMap[token]?.toLong()
        }

        return tokenIds.toLongArray()
    }

    /**
     * Convert byte to character (GPT-2 byte encoding)
     */
    private fun byteToChar(byte: Int): String {
        // GPT-2 uses a custom byte encoding to ensure all bytes map to valid Unicode
        val c = when (byte) {
            in 33..126 -> byte.toChar()  // Printable ASCII
            in 161..172 -> byte.toChar()  // Latin-1 supplement
            in 174..255 -> byte.toChar()  // Latin-1 supplement
            else -> (256 + byte).toChar()  // Map to private use area
        }
        return c.toString()
    }

    /**
     * Decode token IDs to text using BPE
     */
    fun decode(tokenIds: LongArray): String {
        if (!isInitialized) {
            throw IllegalStateException("Tokenizer not initialized")
        }

        // Convert token IDs to tokens
        val tokens = tokenIds.toList().mapNotNull { id ->
            idToToken[id.toInt()]
        }

        // DEBUG: Log first few tokens to see what we're getting
        if (tokens.isNotEmpty()) {
            val firstFewTokens = tokens.take(10)
            println("🔍 DEBUG: First tokens: ${firstFewTokens.joinToString(", ") { "\"$it\"" }}")
        }

        // Filter out special tokens from output
        val filteredTokens = tokens.filter { token ->
            token != BOS_TOKEN &&
            token != EOS_TOKEN &&
            token != PAD_TOKEN &&
            token != "<unk>" &&
            token != "<pad>"
        }

        // Join tokens (BPE tokens concatenate without spaces)
        val tokenString = filteredTokens.joinToString("")

        // DEBUG: Log token string to see if spaces are present
        println("🔍 DEBUG: Token string (first 100 chars): ${tokenString.take(100)}")

        // Convert back to bytes using GPT-2 byte decoder
        val bytes = mutableListOf<Byte>()
        for (char in tokenString) {
            val charCode = char.code
            val byte = charToByte(charCode)

            // DEBUG: Log space character conversion specifically
            if (charCode == 288) {  // 'Ġ' (GPT-2 space)
                println("🔍 DEBUG: Found space char 'Ġ' (U+0120)")
                println("   charToByte(288) = $byte")
                println("   Expected: 32 (space byte)")
            }

            if (byte != null) {
                bytes.add(byte)
            } else {
                // This should never happen with proper GPT-2 BPE vocab!
                // All valid GPT-2 tokens should map to bytes via charToByte()
                println("⚠️ WARNING: Unmapped character U+${charCode.toString(16).uppercase()} ('$char')")
                println("   charToByte($charCode) returned null")
                println("   This indicates the vocab or decode logic has an issue!")
                // Skip unmapped characters - don't use UTF-8 fallback
                // (UTF-8 encoding breaks GPT-2 byte decoding for special chars like 'Ġ')
            }
        }

        // Convert bytes to UTF-8 string
        return try {
            val result = bytes.toByteArray().toString(Charsets.UTF_8)
            println("🔍 DEBUG: Decoded result (first 100 chars): ${result.take(100)}")

            // Clean up any residual special tokens that made it through
            // IMPORTANT: Do NOT trim() here! GPT-2 tokens often have leading/trailing spaces
            // that are semantically significant (e.g., "Ġwhich" = " which")
            result
                .replace(BOS_TOKEN, "")
                .replace(EOS_TOKEN, "")
                .replace(PAD_TOKEN, "")
        } catch (e: Exception) {
            println("⚠️ Tokenizer decode error: ${e.message}")
            // Fallback: return tokens as-is (without special tokens)
            filteredTokens.joinToString("")
        }
    }

    /**
     * Convert character back to byte (inverse of byteToChar)
     *
     * GPT-2 byte encoding maps:
     * - Printable ASCII (33-126) → same
     * - Latin-1 supplement (161-172, 174-255) → same
     * - Everything else (0-32, 127-160, 173) → 256 + byte
     */
    private fun charToByte(charCode: Int): Byte? {
        return when (charCode) {
            // Direct mappings (printable chars)
            in 33..126 -> charCode.toByte()  // Printable ASCII
            in 161..172 -> charCode.toByte()  // Latin-1 supplement
            in 174..255 -> charCode.toByte()  // Latin-1 supplement
            // Private use area mappings (all other bytes)
            in 256..511 -> (charCode - 256).toByte()  // Maps 256-511 → 0-255
            else -> null  // Invalid char code
        }
    }

    /**
     * Get vocabulary size
     */
    fun getVocabSize(): Int = vocabMap.size

    /**
     * Test round-trip encoding/decoding to verify tokenizer correctness
     *
     * This validates that:
     * 1. Text encodes to token IDs correctly
     * 2. Token IDs decode back to the original text
     * 3. Spaces are preserved (GPT-2 'Ġ' character handling)
     * 4. Special tokens work correctly
     *
     * @return true if all tests pass, false otherwise
     */
    fun testRoundTrip(): Boolean {
        if (!isInitialized) {
            println("❌ Tokenizer not initialized - cannot run round-trip test")
            return false
        }

        println("\n" + "=".repeat(60))
        println("🧪 TOKENIZER ROUND-TRIP TEST")
        println("=".repeat(60))

        val testCases = listOf(
            "Hello world",
            "I am M1K3, your AI assistant.",
            "The quick brown fox jumps over the lazy dog.",
            "Testing spaces and punctuation: hello, how are you?",
            "Special chars: @#$%^&*()",
            "Numbers: 1234567890"
        )

        var allPassed = true

        testCases.forEachIndexed { index, originalText ->
            println("\n📝 Test ${index + 1}: \"$originalText\"")

            try {
                // Encode
                val tokens = encode(originalText)
                println("   Tokens (${tokens.size}): ${tokens.take(20).joinToString(", ")}")

                // Decode
                val decodedText = decode(tokens)
                println("   Decoded: \"$decodedText\"")

                // Compare
                val matches = decodedText.trim() == originalText.trim()
                if (matches) {
                    println("   ✅ PASS - Perfect round-trip!")
                } else {
                    println("   ❌ FAIL - Mismatch detected!")
                    println("      Expected: \"$originalText\"")
                    println("      Got:      \"$decodedText\"")

                    // Character-by-character comparison
                    val minLen = minOf(originalText.length, decodedText.length)
                    for (i in 0 until minLen) {
                        if (originalText[i] != decodedText[i]) {
                            println("      First diff at position $i:")
                            println("         Expected: '${originalText[i]}' (U+${originalText[i].code.toString(16).uppercase()})")
                            println("         Got:      '${decodedText[i]}' (U+${decodedText[i].code.toString(16).uppercase()})")
                            break
                        }
                    }
                    allPassed = false
                }

            } catch (e: Exception) {
                println("   ❌ ERROR: ${e.message}")
                e.printStackTrace()
                allPassed = false
            }
        }

        println("\n" + "=".repeat(60))
        if (allPassed) {
            println("✅ ALL TESTS PASSED - Tokenizer is working correctly!")
        } else {
            println("❌ SOME TESTS FAILED - Tokenizer needs fixes!")
        }
        println("=".repeat(60) + "\n")

        return allPassed
    }

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
     *
     * NOTE: This is a fallback for when proper GPT-2 BPE vocab is unavailable.
     * Includes space character and common punctuation to prevent text concatenation.
     */
    private fun createSimpleVocab(): Map<String, Int> {
        val commonTokens = listOf(
            // Special tokens
            "<pad>", "<|im_start|>", "<|im_end|>", "<unk>",

            // Whitespace and punctuation (CRITICAL for readability)
            " ", ".", ",", "!", "?", "\n", "\t",

            // Common words (without leading space - space is separate token)
            "hello", "hi", "how", "are", "you", "i", "am", "fine",
            "what", "is", "your", "name", "my", "间", "ai", "m1k3",
            "can", "help", "me", "yes", "no", "please", "thank", "thanks",
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "as", "about",
            "this", "that", "it", "not", "be", "do", "have", "has"
        )

        return commonTokens.mapIndexed { index, word -> word to index }.toMap()
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
