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
//  Review: claude-opus-4-8, 2026-06-09 — single-flight the model load through
//  SingleFlightLoader to kill the same check-then-act double-download race as the
//  batch transcriber. Confidence 0.8.

import AVFoundation
import Foundation
import M1K3Inference
import M1K3Voice
@preconcurrency import WhisperKit

/// `@unchecked Sendable`: WhisperKit + the active streamer/continuation are
/// guarded by `lock`; WhisperKit's own components are actor/queue-isolated.
public final class WhisperKitProvider: TranscriptionProvider, @unchecked Sendable {
    public let name = "WhisperKit"

    private let lock = NSLock()
    private var whisperKit: WhisperKit?
    private var streamer: AudioStreamTranscriber?
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?
    private var lastText = ""

    /// Coalesces concurrent model loads into one in-flight download; the loaded
    /// model is cached in `whisperKit` for the sync `isAvailable`/`startListening`
    /// reads.
    private let loader: SingleFlightLoader<LoadedWhisperKit>

    private let model: String
    private let downloadBase: URL?

    /// - Parameter model: WhisperKit variant. `base.en` balances size (~142MB)
    ///   and accuracy; `tiny.en` (~75MB) downloads faster, `small.en` is sharper.
    /// - Parameter downloadBase: Root URL for model storage. Pass the app's
    ///   Application Support directory so downloads land in a stable sandbox path
    ///   rather than the HuggingFace cache default (which can produce broken partial
    ///   downloads in sandboxed apps).
    public init(model: String = "base.en", downloadBase: URL? = nil) {
        self.model = model
        self.downloadBase = downloadBase
        loader = SingleFlightLoader { progress in
            progress(0.05)
            let config = WhisperKitConfig(model: model, verbose: false, prewarm: true, load: true)
            config.downloadBase = downloadBase
            return try LoadedWhisperKit(kit: await WhisperKit(config))
        }
    }

    /// Whether the model's CoreML bundle is already on disk — the guard for
    /// auto-loading on launch WITHOUT risking a silent ~142MB re-download (mirrors
    /// Kokoro's `isModelStaged`). WhisperKit lays weights down at
    /// `<base>/models/argmaxinc/whisperkit-coreml/openai_whisper-<variant>/`; the
    /// AudioEncoder bundle's presence is the "complete, not half-fetched" signal.
    public var isModelDownloaded: Bool {
        guard let downloadBase else { return false }
        let bundle = downloadBase
            .appendingPathComponent("models/argmaxinc/whisperkit-coreml/openai_whisper-\(model)")
            .appendingPathComponent("AudioEncoder.mlmodelc")
        return FileManager.default.fileExists(atPath: bundle.path)
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
    /// Concurrent callers share ONE load via the loader; the result is cached for
    /// the sync reads.
    public func prepareModel(progress: @escaping @Sendable (Double) -> Void) async throws {
        if lock.withLock({ whisperKit != nil }) { progress(1.0); return }
        let loaded = try await loader.value(progress: progress)
        lock.withLock { whisperKit = loaded.kit }
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
            // `clean` strips both `<|…|>` control tokens AND non-speech annotations
            // ([BLANK_AUDIO], [Music]…) — the latter were surviving into voice-first
            // and being sent to the model as if they were a spoken turn.
            let onState: AudioStreamTranscriberCallback = { [weak self] _, newState in
                guard let self else { return }
                let text = WhisperTranscriptText.clean(newState.currentText)
                // "Waiting for speech..." is WhisperKit's pre-speech placeholder string.
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
                // skipSpecialTokens defaults to false → tokens in the text; turn it on
                // (the stripper above is the belt-and-braces backstop).
                decodingOptions: DecodingOptions(skipSpecialTokens: true),
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

/// `@unchecked Sendable` box so a loaded `WhisperKit` can cross the
/// `SingleFlightLoader` actor boundary. Justified for the same reason as the
/// providers' class-level annotation: WhisperKit's components are actor/queue-
/// isolated, and the instance is only handed straight into an NSLock-guarded
/// store on the far side — never mutated across threads.
struct LoadedWhisperKit: @unchecked Sendable {
    let kit: WhisperKit
}
