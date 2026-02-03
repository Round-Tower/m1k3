package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.*

/**
 * KokoroTtsEngine - iOS stub implementation
 *
 * Will use ONNX Runtime iOS via cinterop with Core ML backend.
 *
 * Dependencies needed (in Podfile):
 * pod 'onnxruntime-objc', '~> 1.20.1'
 *
 * TODO: Implement ONNX Runtime iOS integration via cinterop
 * TODO: Implement AVAudioEngine playback
 * TODO: Add Core ML backend for hardware acceleration
 */
class IosTtsEngine : TtsEngine {

    override val sampleRate: Int = AudioSample.KOKORO_SAMPLE_RATE
    override var isLoaded: Boolean = false
        private set

    override suspend fun loadModel(): Result<Unit> {
        // TODO: Load kokoro-v1.0-int8.onnx from app bundle
        // Use NSBundle.mainBundle to locate model file
        // Create ORTSession via ObjC interop
        return Result.failure(
            UnsupportedOperationException("iOS TTS not yet implemented")
        )
    }

    override suspend fun synthesize(
        text: String,
        voice: Voice,
        speed: Float
    ): TtsResult {
        if (!isLoaded) {
            return TtsResult.Error(
                TtsErrorCode.MODEL_NOT_LOADED,
                "iOS TTS not yet implemented"
            )
        }
        // TODO: Run ONNX inference via ObjC interop
        return TtsResult.Error(TtsErrorCode.SYNTHESIS_FAILED, "Not implemented")
    }

    override suspend fun synthesizeStreaming(
        text: String,
        voice: Voice,
        speed: Float,
        onChunk: (AudioSample) -> Unit
    ): Result<Unit> {
        return Result.failure(
            UnsupportedOperationException("iOS TTS not yet implemented")
        )
    }

    override fun release() {
        isLoaded = false
    }
}
