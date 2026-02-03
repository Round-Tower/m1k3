package app.m1k3.ai.assistant.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import app.m1k3.ai.domain.tts.AudioSample

/**
 * AudioPlayer - Plays synthesized audio via Android AudioTrack
 *
 * Handles 24kHz mono float32 audio from Kokoro TTS.
 * Uses AudioAttributes.USAGE_ASSISTANT for proper audio focus.
 */
class AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
    }

    private var audioTrack: AudioTrack? = null

    /**
     * Whether audio is currently playing
     */
    val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * Play an audio sample.
     *
     * @param sample Audio data from TTS synthesis
     */
    fun play(sample: AudioSample) {
        if (sample.isEmpty) {
            Log.w(TAG, "Attempted to play empty audio sample")
            return
        }

        stop() // Stop any currently playing audio

        try {
            val bufferSize = sample.samples.size * 4 // float32 = 4 bytes

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sample.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(
                sample.samples,
                0,
                sample.samples.size,
                AudioTrack.WRITE_BLOCKING
            )
            audioTrack?.play()

            Log.d(TAG, "Playing ${sample.durationMs}ms of audio at ${sample.sampleRate}Hz")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            stop()
        }
    }

    /**
     * Play audio sample asynchronously with streaming.
     *
     * Uses AudioTrack in streaming mode for lower latency.
     *
     * @param sample Audio data from TTS synthesis
     */
    fun playStreaming(sample: AudioSample) {
        if (sample.isEmpty) return

        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sample.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )

            if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sample.sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()
            }

            audioTrack?.write(
                sample.samples,
                0,
                sample.samples.size,
                AudioTrack.WRITE_BLOCKING
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream audio", e)
        }
    }

    /**
     * Stop audio playback.
     */
    fun stop() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                    track.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio", e)
        }
        audioTrack = null
    }

    /**
     * Release all resources.
     */
    fun release() {
        stop()
    }
}
