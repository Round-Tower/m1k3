package app.m1k3.ai.assistant.tts

import ai.onnxruntime.*
import android.content.Context
import android.util.Log
import app.m1k3.ai.domain.tts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KokoroTtsEngine - Kokoro-82M TTS via ONNX Runtime
 *
 * On-device text-to-speech synthesis using Kokoro's INT8 quantized model.
 * Produces 24kHz mono audio with SOTA quality.
 *
 * Requires:
 * - kokoro-v1.0-int8.onnx in assets/models/kokoro/
 * - voices-v1.0.bin in assets/models/kokoro/
 *
 * @param context Android Context for asset loading (null for unit testing)
 */
class KokoroTtsEngine(
    private val context: Context?
) : TtsEngine {

    companion object {
        private const val TAG = "KokoroTtsEngine"
        private const val MODEL_PATH = "models/kokoro/onnx/model_q8f16.onnx"
        private const val VOICES_DIR = "models/kokoro/voices"
        private const val VOICE_EMBEDDING_DIM = 256
    }

    override val sampleRate: Int = AudioSample.KOKORO_SAMPLE_RATE
    override var isLoaded: Boolean = false
        private set

    @Volatile
    private var isLoading: Boolean = false

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var voiceEmbeddings: Map<String, FloatArray> = emptyMap()
    private var phonemizer: Phonemizer? = null

    override suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        // Guard against concurrent loads
        if (isLoaded) return@withContext Result.success(Unit)
        if (isLoading) {
            Log.w(TAG, "Model load already in progress, skipping")
            return@withContext Result.failure(RuntimeException("Load already in progress"))
        }
        isLoading = true

        val ctx = context ?: run {
            isLoading = false
            return@withContext Result.failure(
                RuntimeException("Context required to load model")
            )
        }

        try {
            Log.d(TAG, "Loading Kokoro-82M INT8 model...")

            // Initialize ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()

            // Check model exists
            val modelExists = try {
                ctx.assets.open(MODEL_PATH).close()
                true
            } catch (_: Exception) { false }

            if (!modelExists) {
                val error = RuntimeException("Kokoro model not found at $MODEL_PATH")
                Log.e(TAG, "Model not found: $error")
                return@withContext Result.failure(error)
            }

            // Create optimized session
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

                // Try NNAPI for hardware acceleration
                try {
                    addNnapi()
                    Log.d(TAG, "NNAPI acceleration enabled")
                } catch (_: Exception) {
                    Log.w(TAG, "NNAPI not available, using CPU")
                }
            }

            // Load model
            val modelBytes = ctx.assets.open(MODEL_PATH).use { it.readBytes() }
            session = ortEnv!!.createSession(modelBytes, sessionOptions)
            Log.d(TAG, "ONNX model loaded (${modelBytes.size / 1024 / 1024}MB)")

            // Load voice embeddings
            voiceEmbeddings = loadVoiceEmbeddings(ctx)
            Log.d(TAG, "Voice embeddings loaded: ${voiceEmbeddings.keys}")

            // Initialize phonemizer
            phonemizer = Phonemizer()
            Log.d(TAG, "Phonemizer initialized")

            isLoaded = true
            isLoading = false
            Log.d(TAG, "Kokoro TTS ready")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Kokoro model", e)
            isLoading = false
            cleanup()
            Result.failure(e)
        }
    }

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        speed: Float
    ): TtsResult = withContext(Dispatchers.IO) {
        if (!isLoaded || session == null) {
            return@withContext TtsResult.Error(
                TtsErrorCode.MODEL_NOT_LOADED,
                "Call loadModel() first"
            )
        }

        try {
            // Guard: empty/blank text produces no audio
            if (text.isBlank()) {
                return@withContext TtsResult.Error(
                    TtsErrorCode.PHONEMIZATION_FAILED,
                    "Cannot synthesize empty text"
                )
            }

            val clampedSpeed = speed.coerceIn(0.5f, 2.0f)

            // 1. Phonemize text
            val tokens = phonemizer?.phonemize(text)
            if (tokens == null || tokens.isEmpty()) {
                return@withContext TtsResult.Error(
                    TtsErrorCode.PHONEMIZATION_FAILED,
                    "Failed to phonemize: $text"
                )
            }

            // 2. Get voice embedding
            val voiceEmbed = voiceEmbeddings[voice.id]
                ?: voiceEmbeddings.values.firstOrNull()
                ?: return@withContext TtsResult.Error(
                    TtsErrorCode.INVALID_VOICE,
                    "Voice ${voice.id} not found"
                )

            // 3. Run ONNX inference
            val audioSamples = runInference(tokens, voiceEmbed, clampedSpeed)

            TtsResult.Success(AudioSample(audioSamples, sampleRate))

        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, e.message ?: "Unknown error")
        }
    }

    override suspend fun synthesizeStreaming(
        text: String,
        voice: Voice,
        speed: Float,
        onChunk: (AudioSample) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoaded) {
            return@withContext Result.failure(RuntimeException("Model not loaded"))
        }

        try {
            // For streaming: split text into sentences, synthesize each
            val sentences = splitIntoSentences(text)

            for (sentence in sentences) {
                val result = synthesize(sentence, voice, speed)
                when (result) {
                    is TtsResult.Success -> onChunk(result.audio)
                    is TtsResult.Error -> {
                        Log.w(TAG, "Chunk synthesis failed: ${result.message}")
                        // Continue with remaining sentences
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {
        cleanup()
    }

    // ===== Private Implementation =====

    private fun cleanup() {
        try {
            session?.close()
            // Note: OrtEnvironment is shared, don't close it
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
        session = null
        ortEnv = null
        voiceEmbeddings = emptyMap()
        phonemizer = null
        isLoaded = false
    }

    /**
     * Load individual voice embedding .bin files from assets.
     *
     * Each voice is a separate file: voices/bm_daniel.bin (~510KB)
     * Format: raw float32 array (little-endian) representing the voice style vector.
     *
     * Voice IDs match filenames directly (e.g., "bm_daniel" -> bm_daniel.bin).
     */
    private fun loadVoiceEmbeddings(ctx: Context): Map<String, FloatArray> {
        val embeddings = mutableMapOf<String, FloatArray>()

        try {
            val voiceFiles = ctx.assets.list(VOICES_DIR) ?: emptyArray()
            Log.d(TAG, "Found ${voiceFiles.size} voice files in $VOICES_DIR")

            for (file in voiceFiles) {
                if (!file.endsWith(".bin")) continue

                try {
                    val voiceId = file.removeSuffix(".bin")
                    val bytes = ctx.assets.open("$VOICES_DIR/$file").use { it.readBytes() }
                    val floats = bytesToFloatArray(bytes)

                    embeddings[voiceId] = floats
                    Log.d(TAG, "Loaded voice '$voiceId' (${floats.size} floats)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load voice file: $file", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate voice files", e)
        }

        if (embeddings.isEmpty()) {
            Log.w(TAG, "No voice embeddings loaded, using placeholder")
            embeddings["bm_daniel"] = FloatArray(VOICE_EMBEDDING_DIM) { 0.01f }
        }

        return embeddings
    }

    /**
     * Convert raw bytes to float array (little-endian float32).
     */
    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }

    /**
     * Run ONNX inference to generate audio samples.
     *
     * Follows same pattern as MiniLmEmbeddingEngine:
     * Create tensors -> Run inference -> Extract output -> Cleanup tensors
     */
    private fun runInference(
        tokens: IntArray,
        voiceEmbed: FloatArray,
        speed: Float
    ): FloatArray {
        val env = ortEnv ?: throw RuntimeException("ONNX environment not initialized")
        val sess = session ?: throw RuntimeException("ONNX session not initialized")

        // Create input tensors
        // Text tokens: [1, seq_len] - padded with 0 at start and end
        val paddedTokens = LongArray(tokens.size + 2)
        paddedTokens[0] = 0L
        for (i in tokens.indices) {
            paddedTokens[i + 1] = tokens[i].toLong()
        }
        paddedTokens[paddedTokens.size - 1] = 0L

        val textTensor = OnnxTensor.createTensor(
            env,
            java.nio.LongBuffer.wrap(paddedTokens),
            longArrayOf(1, paddedTokens.size.toLong())
        )

        // Voice embedding: [1, 256] - indexed from voice pack by token count
        // Voice file is [N, 1, 256], we select frame at index = num_tokens
        val styleIndex = tokens.size.coerceIn(0, voiceEmbed.size / VOICE_EMBEDDING_DIM - 1)
        val styleOffset = styleIndex * VOICE_EMBEDDING_DIM
        val styleSlice = voiceEmbed.copyOfRange(styleOffset, styleOffset + VOICE_EMBEDDING_DIM)

        val voiceTensor = OnnxTensor.createTensor(
            env,
            java.nio.FloatBuffer.wrap(styleSlice),
            longArrayOf(1, VOICE_EMBEDDING_DIM.toLong())
        )

        // Speed: [1]
        val speedTensor = OnnxTensor.createTensor(
            env,
            java.nio.FloatBuffer.wrap(floatArrayOf(speed)),
            longArrayOf(1)
        )

        try {
            val inputs = mapOf(
                "input_ids" to textTensor,
                "style" to voiceTensor,
                "speed" to speedTensor
            )

            val outputs = sess.run(inputs)

            // Extract audio samples from output
            // Output shape varies by model version - handle common formats
            val audioOutput = try {
                @Suppress("UNCHECKED_CAST")
                val result = outputs[0].value
                when (result) {
                    is Array<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val arr = result as Array<FloatArray>
                        arr[0]
                    }
                    is FloatArray -> result
                    else -> {
                        Log.w(TAG, "Unexpected output type: ${result?.javaClass}")
                        FloatArray(0)
                    }
                }
            } finally {
                outputs.close()
            }

            return audioOutput
        } finally {
            // Cleanup tensors to prevent native memory leaks
            textTensor.close()
            voiceTensor.close()
            speedTensor.close()
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text.split(Regex("[.!?]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
