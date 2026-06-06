//
//  WhisperKitProvider.swift
//  M1K3WhisperKit
//
//  High-accuracy on-device transcription via WhisperKit (CoreML). Isolated in
//  its own target — like M1K3MLX — so only this target and the app link the heavy
//  CoreML/model machinery; M1K3Voice stays system-frameworks-only.
//
//  WhisperKit needs a model downloaded + loaded before it can serve, so
//  `isAvailable` is false until `prepareModel` succeeds — which means the router
//  uses Apple Speech out of the box and switches to WhisperKit once its model is
//  ready. Live mic streaming runs through WhisperKit's `AudioStreamTranscriber`,
//  whose `currentText` is cumulative (matching TranscriptAccumulator's fold).
//
//  Verify-by-launch: model download + mic streaming need a real device + the mic
//  entitlement; the seam it plugs into (router, accumulator) is unit-tested.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.6,
//  Prior: the internal call-pipeline project WhisperKitProvider (Kev) — re-pointed at M1K3's live
//  TranscriptionProvider seam + AudioStreamTranscriber (the prior call-pipeline's was buffer-pump),
//  ModelManaged/PowerEfficiency surface dropped.

import AVFoundation
import Foundation
import M1K3Voice
@preconcurrency import WhisperKit

/// `@unchecked Sendable`: WhisperKit + the active streamer/continuation are
/// guarded by `lock`; WhisperKit's own components are actor/queue-isolated.
public final class WhisperKitProvider: TranscriptionProvider, @unchecked Sendable {
    public let name = "WhisperKit"

    private let model: String
    private let lock = NSLock()
    private var whisperKit: WhisperKit?
    private var streamer: AudioStreamTranscriber?
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?
    private var lastText = ""

    /// - Parameter model: WhisperKit variant. `base.en` balances size (~142MB)
    ///   and accuracy; `tiny.en` (~75MB) downloads faster, `small.en` is sharper.
    public init(model: String = "base.en") {
        self.model = model
    }

    /// Available only on Apple Silicon once a model has been loaded. Until then
    /// the router falls back to Apple Speech.
    public var isAvailable: Bool {
        #if arch(arm64)
            return lock.withLock { whisperKit != nil }
        #else
            return false
        #endif
    }

    /// Download + load the WhisperKit model, reporting coarse progress (WhisperKit
    /// init bundles download + load). Idempotent: returns fast if already loaded.
    public func prepareModel(progress: @escaping @Sendable (Double) -> Void) async throws {
        if lock.withLock({ whisperKit != nil }) { progress(1.0); return }
        progress(0.05)
        let kit = try await WhisperKit(WhisperKitConfig(model: model, verbose: false, prewarm: true, load: true))
        lock.withLock { whisperKit = kit }
        progress(1.0)
    }

    public func startListening() throws -> AsyncStream<TranscriptSegment> {
        guard let kit = lock.withLock({ whisperKit }), let tokenizer = kit.tokenizer else {
            throw WhisperKitProviderError.modelNotLoaded
        }
        stopListening()

        return AsyncStream { continuation in
            lock.withLock {
                self.continuation = continuation
                self.lastText = ""
            }

            // Yield cumulative text whenever WhisperKit's running transcription grows.
            let onState: AudioStreamTranscriberCallback = { [weak self] _, newState in
                guard let self else { return }
                let text = newState.currentText.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty, text != "Waiting for speech..." else { return }
                self.lock.withLock { self.lastText = text }
                continuation.yield(TranscriptSegment(text: text, isFinal: false))
            }

            let streamer = AudioStreamTranscriber(
                audioEncoder: kit.audioEncoder,
                featureExtractor: kit.featureExtractor,
                segmentSeeker: kit.segmentSeeker,
                textDecoder: kit.textDecoder,
                tokenizer: tokenizer,
                audioProcessor: kit.audioProcessor,
                decodingOptions: DecodingOptions(),
                stateChangeCallback: onState
            )
            lock.withLock { self.streamer = streamer }

            Task {
                do { try await streamer.startStreamTranscription() }
                catch { self.stopListening() }
            }
        }
    }

    public func stopListening() {
        let (streamer, continuation, finalText) = lock.withLock {
            (self.streamer, self.continuation, self.lastText)
        }
        Task { await streamer?.stopStreamTranscription() }
        if let continuation {
            if !finalText.isEmpty {
                continuation.yield(TranscriptSegment(text: finalText, isFinal: true))
            }
            continuation.finish()
        }
        lock.withLock {
            self.streamer = nil
            self.continuation = nil
            self.lastText = ""
        }
    }
}

public enum WhisperKitProviderError: Error, Sendable {
    case modelNotLoaded
}
