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
import os
import Speech

/// `@unchecked Sendable`: all mutable recognition state is guarded by `lock`;
/// the audio-tap closure only appends to the (lock-held) request.
public final class AppleSpeechTranscriber: TranscriptionProvider, @unchecked Sendable {
    public let name = "Apple Speech"

    private static let log = Logger(subsystem: "dev.murphysig.M1K3", category: "stt")

    private let locale: Locale
    private let audioEngine = AVAudioEngine()
    private let lock = NSLock()
    /// Serialises engine teardown/reinstall (stop + removeTap + installTap)
    /// between `stopListening` and the route-change handler, which now run on
    /// different threads. SEPARATE from `lock` on purpose: the tap closure takes
    /// `lock`, so guarding `audioEngine.stop()` with it would re-introduce the
    /// inversion `stopListening` documents. The tap closure never takes this one.
    private let engineLock = NSLock()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?
    /// Observes `.AVAudioEngineConfigurationChange` so a route flip (a Bluetooth
    /// mic connecting, or capture forcing the A2DP→HFP profile switch) reinstalls
    /// the tap on the NEW input format instead of leaving it deaf on the old one.
    private var configObserver: NSObjectProtocol?

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
        engineLock.withLock {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        let (request, task, continuation, observer) = lock.withLock {
            let captured = (self.request, self.task, self.continuation, self.configObserver)
            self.request = nil
            self.task = nil
            self.continuation = nil
            self.configObserver = nil
            return captured
        }
        if let observer { NotificationCenter.default.removeObserver(observer) }
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

        // A Bluetooth mic engaging (or TCC settling) can leave the input format
        // degenerate at this instant; refuse a dead tap rather than capture
        // silence. The route-change observer below reinstalls once it's ready.
        guard installInputTap() else {
            Self.log.error("mic input route not ready — could not start listening")
            stopListening()
            continuation.finish()
            return
        }
        observeConfigurationChanges()

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

    /// Install the mic tap against the CURRENT input format, refusing a
    /// degenerate 0-Hz / 0-channel format — an unsettled route (Bluetooth mic
    /// still engaging, TCC not yet granted). Installing a tap with that format
    /// invalidates the HAL AudioUnit (-10877) and captures nothing. Returns
    /// false when the route isn't ready, so the caller can wait for the
    /// route-change observer to reinstall.
    @discardableResult
    private func installInputTap() -> Bool {
        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        Self.log.notice(
            "stt mic input format \(format.sampleRate, privacy: .public)Hz ch=\(format.channelCount, privacy: .public)"
        )
        guard MicTapFormatGate.isUsable(
            sampleRate: format.sampleRate, channelCount: format.channelCount
        ) else {
            Self.log.error("degenerate mic format — not installing tap (route not ready)")
            return false
        }
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
            self?.lock.withLock { self?.request?.append(buffer) }
        }
        return true
    }

    private func observeConfigurationChanges() {
        let observer = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange, object: audioEngine, queue: nil
        ) { [weak self] _ in
            self?.handleConfigurationChange()
        }
        lock.withLock { self.configObserver = observer }
    }

    /// The audio route changed — a Bluetooth mic connecting, or starting capture
    /// forcing the headset's A2DP→HFP profile switch. The installed tap is bound
    /// to the OLD input format and now delivers nothing, so reinstall against the
    /// new format and restart. Engine ops run OUTSIDE the lock: `audioEngine.stop()`
    /// blocks on in-flight tap callbacks and those take the lock (the inversion
    /// `stopListening` documents).
    private func handleConfigurationChange() {
        guard lock.withLock({ request != nil }) else { return } // not listening
        Self.log.notice("audio route changed — reinstalling mic tap at the new format")
        engineLock.withLock {
            audioEngine.inputNode.removeTap(onBus: 0)
            if audioEngine.isRunning { audioEngine.stop() }
            guard installInputTap() else { return }
            audioEngine.prepare()
            do {
                try audioEngine.start()
            } catch {
                Self.log.error("engine restart after route change failed: \(error.localizedDescription, privacy: .public)")
            }
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
