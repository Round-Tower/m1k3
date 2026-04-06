package app.m1k3.ai.domain.stt

import kotlinx.coroutines.flow.StateFlow

/**
 * Speech-to-Text Engine Interface.
 *
 * Platform-agnostic contract for voice input.
 * Android: SpeechRecognizer, iOS: SFSpeechRecognizer (future)
 *
 * State machine: IDLE -> LISTENING -> PROCESSING -> RESULT/ERROR -> IDLE
 */
interface SttEngine {
    /** Current STT state as observable flow */
    val state: StateFlow<SttState>

    /** Start listening for speech */
    fun startListening()

    /** Stop listening and process final result */
    fun stopListening()

    /** Cancel without processing */
    fun cancel()

    /** Release resources */
    fun release()

    /** Whether STT is available on this device */
    fun isAvailable(): Boolean
}

/**
 * STT state machine.
 */
sealed class SttState {
    /** Ready to listen */
    data object Idle : SttState()

    /** Actively listening for speech */
    data class Listening(
        /** Partial transcript while listening */
        val partialText: String = ""
    ) : SttState()

    /** Processing speech after user stops talking */
    data object Processing : SttState()

    /** Final recognized text */
    data class Result(val text: String) : SttState()

    /** Recognition failed */
    data class Error(val message: String) : SttState()
}

/**
 * Check if STT is actively listening.
 */
val SttState.isListening: Boolean
    get() = this is SttState.Listening

/**
 * Check if STT is in any active state (listening or processing).
 */
val SttState.isActive: Boolean
    get() = this is SttState.Listening || this is SttState.Processing
