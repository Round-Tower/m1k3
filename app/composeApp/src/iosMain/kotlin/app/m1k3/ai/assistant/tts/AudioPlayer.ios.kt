package app.m1k3.ai.assistant.tts

import app.m1k3.ai.domain.tts.AudioSample

/**
 * AudioPlayer - iOS stub implementation
 *
 * Will use AVAudioEngine for playback.
 *
 * TODO: Implement AVAudioEngine integration via cinterop
 * TODO: Handle audio session categories (AVAudioSession)
 */
class IosAudioPlayer {

    val isPlaying: Boolean = false

    fun play(sample: AudioSample) {
        // TODO: Use AVAudioEngine to play float32 samples
        // 1. Create AVAudioPCMBuffer from sample.samples
        // 2. Schedule buffer on AVAudioPlayerNode
        // 3. Start engine if not running
    }

    fun stop() {
        // TODO: Stop AVAudioPlayerNode
    }

    fun release() {
        stop()
    }
}
