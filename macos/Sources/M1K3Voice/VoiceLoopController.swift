//
//  VoiceLoopController.swift
//  M1K3Voice
//
//  The driver for voice-first mode: executes VoiceLoopMachine commands against
//  the real seams (mic stream, chat turn, TTS) via injected closures, so the
//  loop logic stays testable with fakes and this class owns only task
//  lifetimes. Main-actor: every event funnels through dispatch(), state is
//  observable for the UI.
//
//  Task rules (pinned by tests):
//  • The listen task consumes the transcript stream; a ~tick poll closes the
//    listen by SILENCE (SilenceEndpointer) because recognizer finality can lag
//    seconds behind the user being done.
//  • The turn task is UNSTRUCTURED and held — exit() does not cancel it. The
//    answer still lands in the chat transcript; the machine (in .ended) just
//    never speaks it.
//  • speak is enqueue-style: completion arrives via speechDidEnd(), wired from
//    the speech provider's onSpeakingEnded by the app layer.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (behavior
//  test-pinned with fakes; real-seam wiring is the app layer's). Prior: Unknown.
//

import Foundation
import Observation

/// A user-facing turn failure (the message is shown, not logged-and-lost).
public struct VoiceTurnFailure: Error, Equatable, Sendable {
    public let message: String

    public init(message: String) {
        self.message = message
    }
}

@MainActor
@Observable
public final class VoiceLoopController {
    /// The loop's real-world effects, injected as closures so tests fake them
    /// and the app layer adapts its existing seams without this type importing
    /// chat/avatar machinery.
    public struct Dependencies {
        public var startListening: @MainActor () throws -> AsyncStream<TranscriptSegment>
        public var stopListening: @MainActor () -> Void
        public var runTurn: @MainActor (String) async -> Result<String, VoiceTurnFailure>
        public var speak: @MainActor (String) async -> Void
        public var stopSpeaking: @MainActor () async -> Void

        public init(
            startListening: @escaping @MainActor () throws -> AsyncStream<TranscriptSegment>,
            stopListening: @escaping @MainActor () -> Void,
            runTurn: @escaping @MainActor (String) async -> Result<String, VoiceTurnFailure>,
            speak: @escaping @MainActor (String) async -> Void,
            stopSpeaking: @escaping @MainActor () async -> Void
        ) {
            self.startListening = startListening
            self.stopListening = stopListening
            self.runTurn = runTurn
            self.speak = speak
            self.stopSpeaking = stopSpeaking
        }
    }

    public private(set) var state: VoiceLoopState = .idle
    public private(set) var lastError: String?

    private var machine = VoiceLoopMachine()
    private let dependencies: Dependencies
    private let echoGrace: Duration
    private let endpointTick: Duration
    private let silence: Duration

    private var listenTask: Task<Void, Never>?
    private var endpointTask: Task<Void, Never>?
    /// Held, never cancelled by exit — see the header.
    private var turnTask: Task<Void, Never>?
    private var speakTask: Task<Void, Never>?
    private var accumulator = TranscriptAccumulator()
    private var endpointer: SilenceEndpointer

    public init(
        dependencies: Dependencies,
        silence: Duration = .seconds(1.6),
        holdSilence: Duration = .seconds(3.0),
        maxWait: Duration = .seconds(20),
        echoGrace: Duration = .milliseconds(350),
        endpointTick: Duration = .milliseconds(300)
    ) {
        self.dependencies = dependencies
        self.silence = silence
        self.echoGrace = echoGrace
        self.endpointTick = endpointTick
        endpointer = SilenceEndpointer(silence: silence, holdSilence: holdSilence, maxWait: maxWait)
    }

    // MARK: - User intents

    public func begin() {
        lastError = nil
        dispatch(.begin)
    }

    public func interrupt() {
        dispatch(.interrupt)
    }

    public func mute() {
        dispatch(.mute)
    }

    public func exit() {
        dispatch(.exit)
    }

    /// Wire from the speech provider's onSpeakingEnded.
    public func speechDidEnd() {
        dispatch(.speechFinished)
    }

    // MARK: - Reducer plumbing

    private func dispatch(_ event: VoiceLoopEvent) {
        let commands = machine.handle(event)
        state = machine.state
        for command in commands {
            execute(command)
        }
    }

    private func execute(_ command: VoiceLoopCommand) {
        switch command {
        case let .startListening(afterEchoGrace):
            startListen(graced: afterEchoGrace)

        case .stopListening:
            listenTask?.cancel()
            listenTask = nil
            endpointTask?.cancel()
            endpointTask = nil
            dependencies.stopListening()

        case let .runTurn(question):
            turnTask = Task { [weak self] in
                guard let dependencies = self?.dependencies else { return }
                switch await dependencies.runTurn(question) {
                case let .success(answer):
                    self?.dispatch(.answerReady(answer))
                case let .failure(failure):
                    self?.lastError = failure.message
                    self?.dispatch(.answerFailed(failure.message))
                }
            }

        case let .speak(answer):
            speakTask = Task { [weak self] in
                await self?.dependencies.speak(answer)
            }

        case .stopSpeaking:
            // The cancel is advisory (speak providers are enqueue-style and
            // don't observe it) — the audio actually stops via the
            // stopSpeaking dependency below.
            speakTask?.cancel()
            speakTask = nil
            Task { [weak self] in await self?.dependencies.stopSpeaking() }
        }
    }

    // MARK: - Listening internals

    private func startListen(graced: Bool) {
        accumulator = TranscriptAccumulator()
        endpointer.reset()
        let grace = graced ? echoGrace : .zero
        listenTask = Task { [weak self] in
            if grace > .zero { try? await Task.sleep(for: grace) }
            guard let self, !Task.isCancelled else { return }
            let stream: AsyncStream<TranscriptSegment>
            do {
                stream = try dependencies.startListening()
            } catch {
                lastError = error.localizedDescription
                dispatch(.mute) // park: mic unavailable
                return
            }
            startEndpointTick()
            for await segment in stream {
                guard !Task.isCancelled else { return }
                accumulator.ingest(segment)
                endpointer.ingest(partial: accumulator.text, at: ContinuousClock.now)
                dispatch(.partial(accumulator.text))
            }
            // Stream ended (recognizer finality). If a silence endpoint already
            // moved the machine on, this event is stale and dropped there.
            guard !Task.isCancelled else { return }
            dispatch(.endpointed(sanitizedUtterance()))
        }
    }

    /// The final transcript, hygiene-cleaned for the model (repetition / silence
    /// hallucinations / whitespace). An all-noise utterance becomes "" so the
    /// machine's empty-listen guard parks/re-listens instead of running a ghost turn.
    private func sanitizedUtterance() -> String {
        TranscriptSanitizer.clean(accumulator.text, confidence: accumulator.confidence)
    }

    private func startEndpointTick() {
        endpointTask?.cancel()
        endpointTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: self?.endpointTick ?? .milliseconds(300))
                guard let self, !Task.isCancelled else { return }
                if endpointer.shouldEndpoint(at: ContinuousClock.now) {
                    dispatch(.endpointed(sanitizedUtterance()))
                    return
                }
            }
        }
    }
}
