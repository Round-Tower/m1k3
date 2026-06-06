//
//  AppleSpeechTranscriber.swift
//  M1K3Voice
//
//  Live dictation via Apple's SFSpeechRecognizer + AVAudioEngine — system
//  frameworks, zero third-party dep, so this ships the speak→transcript→RAG loop
//  without a model download. On-device recognition is *required* (not just
//  preferred): M1K3 is on-device-only, so if the recogniser can't run offline we
//  report unavailable rather than silently sending audio to Apple's servers.
//
//  Yields the recogniser's *cumulative* best transcription per partial (matching
//  TranscriptAccumulator's "latest text wins" fold), then a final segment. The
//  whole class is verify-by-launch (needs a mic + Speech authorization), like
//  AVSpeechProvider — the pure pieces it feeds (segment, router, accumulator) are
//  unit-tested.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.7,
//  Prior: the internal call-pipeline project AppleSpeechTranscriber + LiveTranscriptionSession
//  (Kev) — collapsed to one live-session provider, cumulative text, on-device
//  forced, device-picker + call-domain fields dropped.

import AVFoundation
import Foundation
import Speech

/// `@unchecked Sendable`: all mutable recognition state is guarded by `lock`;
/// the audio-tap closure only appends to the (lock-held) request.
public final class AppleSpeechTranscriber: TranscriptionProvider, @unchecked Sendable {
    public let name = "Apple Speech"

    private let locale: Locale
    private let audioEngine = AVAudioEngine()
    private let lock = NSLock()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?

    public init(locale: Locale = .current) {
        self.locale = locale
    }

    /// Available when a recogniser exists for the locale, is ready, and supports
    /// on-device recognition (our privacy floor). Authorization is requested at
    /// `startListening` time, not here.
    public var isAvailable: Bool {
        guard let recognizer = SFSpeechRecognizer(locale: locale) else { return false }
        return recognizer.isAvailable && recognizer.supportsOnDeviceRecognition
    }

    public func startListening() throws -> AsyncStream<TranscriptSegment> {
        stopListening()
        return AsyncStream { continuation in
            lock.withLock { self.continuation = continuation }
            Task { await self.begin(continuation) }
        }
    }

    public func stopListening() {
        // Stop the engine + drain the tap OUTSIDE the lock: `audioEngine.stop()`
        // blocks until in-flight tap callbacks return, and those callbacks take
        // `lock` — holding it here would be a lock-inversion deadlock (which is
        // hit every time recognition settles, since the result callback calls
        // stopListening on `isFinal`).
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        let (request, task, continuation) = lock.withLock {
            let captured = (self.request, self.task, self.continuation)
            self.request = nil
            self.task = nil
            self.continuation = nil
            return captured
        }
        request?.endAudio()
        task?.cancel()
        continuation?.finish()
    }

    // MARK: - Private

    /// Request authorization, then wire the mic into a streaming recognition
    /// request. On any failure the stream simply finishes (the caller sees no
    /// segments and can surface "couldn't start listening").
    private func begin(_ continuation: AsyncStream<TranscriptSegment>.Continuation) async {
        guard await Self.authorized(),
              let recognizer = SFSpeechRecognizer(locale: locale),
              recognizer.isAvailable
        else {
            continuation.finish()
            return
        }

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.requiresOnDeviceRecognition = recognizer.supportsOnDeviceRecognition
        // Store the request before installing the tap, so the first audio buffers
        // aren't dropped by `self.request` still being nil when the tap fires.
        lock.withLock { self.request = request }

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.lock.withLock { self?.request?.append(buffer) }
        }

        let task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            guard let self else { return }
            if error != nil {
                self.stopListening()
                return
            }
            guard let result else { return }
            let text = result.bestTranscription.formattedString
            if !text.isEmpty {
                continuation.yield(TranscriptSegment(
                    text: text,
                    isFinal: result.isFinal,
                    confidence: Self.confidence(of: result.bestTranscription)
                ))
            }
            if result.isFinal { self.stopListening() }
        }

        lock.withLock { self.task = task }

        do {
            audioEngine.prepare()
            try audioEngine.start()
        } catch {
            stopListening()
        }
    }

    private static func authorized() async -> Bool {
        if SFSpeechRecognizer.authorizationStatus() == .authorized { return true }
        return await withCheckedContinuation { cont in
            SFSpeechRecognizer.requestAuthorization { cont.resume(returning: $0 == .authorized) }
        }
    }

    /// Mean per-segment confidence (0...1), or nil when the recogniser reports none.
    private static func confidence(of transcription: SFTranscription) -> Float? {
        let segments = transcription.segments
        guard !segments.isEmpty else { return nil }
        return segments.reduce(0) { $0 + $1.confidence } / Float(segments.count)
    }
}
