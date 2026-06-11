//
//  VoiceLoopMachine.swift
//  M1K3Voice
//
//  The pure reducer behind voice-first mode's hands-free conversation loop:
//  listen → endpoint → run the turn → speak the answer → listen again. Events
//  in, commands out, zero side effects — VoiceLoopController executes the
//  commands against the real mic/chat/speech seams.
//
//  Design rules pinned by the tests:
//  • Half-duplex: the mic is never armed while thinking or speaking (no echo).
//  • Re-listens after speech carry an echo grace (speaker tail audio).
//  • Two consecutive empty listens park the mic — no infinite re-arm loop.
//  • Stale events in the wrong state are dropped (the late speechFinished a
//    barge-in's stop() produces; an answerReady landing after exit).
//  • exit is terminal and never cancels the in-flight turn — the answer still
//    lands in the chat transcript, it just isn't spoken.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, every
//  transition test-pinned). Prior: Unknown.
//

import Foundation

public enum VoiceLoopState: Equatable, Sendable {
    /// Mode active, mic parked (muted, gave up after empty listens, or an error).
    case idle
    case listening(partial: String)
    case awaitingAnswer(question: String)
    case speaking(answer: String)
    /// Terminal — the mode was exited.
    case ended
}

public enum VoiceLoopEvent: Equatable, Sendable {
    /// Enter the mode / tap-to-talk from a parked idle.
    case begin
    /// Cumulative live transcript while listening.
    case partial(String)
    /// The utterance ended (recognizer finality or silence endpoint).
    case endpointed(String)
    case answerReady(String)
    case answerFailed(String)
    /// Natural TTS completion (onSpeakingEnded).
    case speechFinished
    /// User barge-in (click/Space while speaking).
    case interrupt
    /// Park the mic without leaving the mode.
    case mute
    case exit
}

public enum VoiceLoopCommand: Equatable, Sendable {
    /// `afterEchoGrace` delays arming the mic briefly so the speaker tail of
    /// the just-finished utterance isn't transcribed.
    case startListening(afterEchoGrace: Bool)
    case stopListening
    case runTurn(String)
    case speak(String)
    case stopSpeaking
}

public struct VoiceLoopMachine: Sendable {
    public private(set) var state: VoiceLoopState = .idle
    /// Park after this many empty listens in a row.
    private var consecutiveEmptyListens = 0
    private static let maxEmptyListens = 2

    public init() {}

    public mutating func handle(_ event: VoiceLoopEvent) -> [VoiceLoopCommand] {
        if case .ended = state { return [] }
        switch event {
        case .begin:
            guard case .idle = state else { return [] }
            state = .listening(partial: "")
            return [.startListening(afterEchoGrace: false)]

        case let .partial(text):
            guard case .listening = state else { return [] }
            state = .listening(partial: text)
            return []

        case let .endpointed(text):
            guard case .listening = state else { return [] }
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return emptyListenEnded() }
            consecutiveEmptyListens = 0
            state = .awaitingAnswer(question: trimmed)
            return [.stopListening, .runTurn(trimmed)]

        case let .answerReady(answer):
            guard case .awaitingAnswer = state else { return [] }
            state = .speaking(answer: answer)
            return [.speak(answer)]

        case .answerFailed:
            guard case .awaitingAnswer = state else { return [] }
            state = .idle
            return []

        case .speechFinished:
            guard case .speaking = state else { return [] }
            state = .listening(partial: "")
            return [.startListening(afterEchoGrace: true)]

        case .interrupt:
            guard case .speaking = state else { return [] }
            state = .listening(partial: "")
            return [.stopSpeaking, .startListening(afterEchoGrace: true)]

        case .mute:
            switch state {
            case .listening:
                state = .idle
                return [.stopListening]
            case .idle, .awaitingAnswer, .speaking, .ended:
                return []
            }

        case .exit:
            state = .ended
            return [.stopSpeaking, .stopListening]
        }
    }

    private mutating func emptyListenEnded() -> [VoiceLoopCommand] {
        consecutiveEmptyListens += 1
        if consecutiveEmptyListens >= Self.maxEmptyListens {
            state = .idle
            return [.stopListening]
        }
        state = .listening(partial: "")
        return [.stopListening, .startListening(afterEchoGrace: false)]
    }
}
