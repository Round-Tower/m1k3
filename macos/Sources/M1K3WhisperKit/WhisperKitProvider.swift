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
//  Prior: internal call-pipeline project, WhisperKitProvider (Kev) — re-pointed at M1K3's live
//  TranscriptionProvider seam + AudioStreamTranscriber (the prior call-pipeline's was buffer-pump),
//  ModelManaged/PowerEfficiency surface dropped.
//  Review: claude-opus-4-8, 2026-06-09 — single-flight the model load through
//  SingleFlightLoader to kill the same check-then-act double-download race as the
//  batch transcriber. Confidence 0.8.
//  Review: Kev + claude-fable-5, 2026-07-16 (concurrency deep pass, findings
//  2/13/14/19) — restart races closed STRUCTURALLY. The root cause was that every
//  AudioStreamTranscriber wrapped the SAME `kit.audioProcessor` (one shared engine
//  slot), so any streamer's `stopStreamTranscription()` tore down whichever
//  session currently held the mic — a fire-and-forget old-stop could kill the new
//  session's capture, and a stale callback could kill a live successor. An
//  adversarial verify pass (3 lenses) showed a generation-guard + "belt" could not
//  make the shared processor safe. Fix: each session builds its OWN
//  `AudioProcessor()` (the constructor already accepts one), so `stopRecording()`
//  is session-scoped and cross-session capture damage is impossible. Generation
//  stamping remains only for the SHARED transcript state: a stale onState must not
//  write `lastText` (which the next session's stop emits as its final segment —
//  the "previous utterance re-sent as a fresh turn" bug) nor finish a successor's
//  continuation. Verify-by-launch per this file's header. Named residual: a stop
//  landing inside `startStreamTranscription`'s own permission suspension can still
//  leave that session's OWN engine running briefly — narrow (immediate stop during
//  first-launch mic-permission resolution), contained to one session's processor,
//  ⌘R-owed.

import AVFoundation
import Foundation
import M1K3Inference
import M1K3Voice
import os
@preconcurrency import WhisperKit

/// `@unchecked Sendable`: WhisperKit + the active streamer/continuation are
/// guarded by `lock`; WhisperKit's own components are actor/queue-isolated.
public final class WhisperKitProvider: TranscriptionProvider, @unchecked Sendable {
    public let name = "WhisperKit"

    private static let log = Logger(subsystem: "app.m1k3", category: "stt")
    private let lock = NSLock()
    private var whisperKit: WhisperKit?
    private var streamer: AudioStreamTranscriber?
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?
    private var lastText = ""
    /// Session identity (guarded by `lock`). Bumped by every start AND stop so a
    /// stale streamer's late callbacks — a zombie onState, a superseded start
    /// task's error path, a cancelled old consumer — can prove they're stale and
    /// no-op instead of polluting the shared transcript state or finishing the
    /// wrong continuation.
    private var generation: UInt64 = 0

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
        Self.isModelDownloaded(model: model, downloadBase: downloadBase)
    }

    /// Static form so launch-migration code can ask "is variant X already on disk?"
    /// without constructing a provider. Same path contract as the instance read.
    public static func isModelDownloaded(model: String, downloadBase: URL?) -> Bool {
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
            let generation = lock.withLock {
                self.generation &+= 1
                self.continuation = continuation
                self.lastText = ""
                return self.generation
            }
            // Consumer cancellation must release this session's capture
            // structurally; gated on .cancelled (.finished only originates from
            // stopListening) and generation-scoped so a cancelled OLD consumer
            // can't tear down a newer session.
            continuation.onTermination = { [weak self] reason in
                if case .cancelled = reason { self?.stopListening(ifGeneration: generation) }
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
                // Generation-guard the SHARED transcript write: a stale streamer's
                // late callback must not overwrite `lastText` (which the current
                // session's stop would then emit as its final segment) nor yield
                // into a superseded session. Its own capture is on its own
                // processor, so there is nothing else to tear down here.
                let isCurrent = self.lock.withLock {
                    guard generation == self.generation else { return false }
                    self.lastText = text
                    return true
                }
                guard isCurrent else { return }
                continuation.yield(TranscriptSegment(text: text, isFinal: false))
            }

            let streamer = AudioStreamTranscriber(
                audioEncoder: kit.audioEncoder,
                featureExtractor: kit.featureExtractor,
                segmentSeeker: kit.segmentSeeker,
                textDecoder: kit.textDecoder,
                tokenizer: tokenizer,
                // A FRESH processor per session (not the shared kit.audioProcessor):
                // this session's stopRecording() then touches only THIS engine, so a
                // stale teardown can never kill a live successor's mic. The heavy
                // models above are stateless and safe to share.
                audioProcessor: AudioProcessor(),
                // skipSpecialTokens defaults to false → tokens in the text; turn it on
                // (the stripper above is the belt-and-braces backstop). Pin language to
                // English for the monolingual `.en` models (skip wasted, misfire-prone
                // detection); a future multilingual variant (no `.en` suffix) gets nil
                // → auto-detect. Derived from the model id so it stays variant-correct.
                decodingOptions: DecodingOptions(
                    language: model.hasSuffix(".en") ? "en" : nil, skipSpecialTokens: true
                ),
                stateChangeCallback: onState
            )
            let accepted = lock.withLock {
                guard generation == self.generation else { return false }
                self.streamer = streamer
                return true
            }
            guard accepted else {
                // A stop claimed the session while the streamer was being built;
                // it captured (and finished) this continuation already, and the
                // never-started streamer needs no teardown.
                continuation.finish()
                return
            }

            Task {
                // A stop may have claimed the session between accept and here.
                guard self.lock.withLock({ generation == self.generation }) else { return }
                do { try await streamer.startStreamTranscription() }
                catch {
                    // Mid-session stream failure (CoreML hiccup, audio route change,
                    // decoder error) otherwise looks identical to a normal silence
                    // endpoint downstream — leave a trail before tearing down.
                    // Generation-scoped: a superseded session's failure must not
                    // finish its successor's continuation.
                    Self.log.error("stream transcription failed: \(error.localizedDescription, privacy: .public)")
                    self.stopListening(ifGeneration: generation)
                }
            }
        }
    }

    public func stopListening() {
        stopListening(ifGeneration: nil)
    }

    /// Tear down the CURRENT session — but only if it is still the session the
    /// caller belongs to (`nil` = whatever is live now). Claiming bumps the
    /// generation (so in-flight start tasks and stale callbacks no-op) and
    /// fire-and-forgets the streamer's teardown. That teardown touches only this
    /// session's OWN AudioProcessor, so ordering it against a concurrent
    /// startListening (a different processor) is unnecessary — the shared-engine
    /// hazard the old `pendingStop` chain guarded no longer exists.
    private func stopListening(ifGeneration expected: UInt64?) {
        let claimed = lock.withLock {
            () -> (AudioStreamTranscriber?, AsyncStream<TranscriptSegment>.Continuation?, String)? in
            if let expected, expected != generation { return nil }
            generation &+= 1
            let captured = (streamer, continuation, lastText)
            streamer = nil
            continuation = nil
            lastText = ""
            return captured
        }
        guard let (stopped, continuation, finalText) = claimed else { return }
        Task { await stopped?.stopStreamTranscription() }
        if let continuation {
            if !finalText.isEmpty {
                continuation.yield(TranscriptSegment(text: finalText, isFinal: true))
            }
            continuation.finish()
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
