//
//  GemmaAudioTranscriber.swift  (SPIKE SKETCH — not wired, not compiled)
//  scratch/gemma4-audio-spike
//
//  Design sketch for the single-model voice path: Gemma 4 E4B native audio behind
//  the SAME `TranscriptionProvider` seam the shipped WhisperKit/AppleSpeech
//  providers use. The `// TODO(device)` markers are where the real audio-decode
//  API plugs in once a runtime is chosen (MLX-Swift `gemma-4-swift-mlx`, or
//  LiteRT-LM). Mirrors MLXGemmaProvider/WhisperKitProvider: `@unchecked Sendable`
//  + NSLock, `isAvailable=false` until a model loads, `prepareModel(progress:)`
//  reusing the model-download-UX pattern.
//
//  Intentionally lives in scratch/ — exploration, no tests, not built. Promote to
//  an isolated `M1K3GemmaAudio` target only after the benchmark gate (see SPIKE.md).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.4 (spike), Prior: Unknown

import Foundation
import M1K3Voice

// import MLXGemma4Audio   // TODO(device): whichever module exposes E4B audio decode

public final class GemmaAudioTranscriber: TranscriptionProvider, @unchecked Sendable {
    public let name = "Gemma 4 E4B"

    private let modelID: String
    private let lock = NSLock()
    private var model: AnyObject? // TODO(device): the loaded Gemma 4 audio model
    private var continuation: AsyncStream<TranscriptSegment>.Continuation?
    private var isCapturing = false

    /// Default to an E4B audio build (not 12B — too heavy for the mic button).
    public init(modelID: String = "google/gemma-4-E4B-it") {
        self.modelID = modelID
    }

    /// Unavailable until a model is loaded — so the router uses WhisperKit/Apple
    /// Speech until Gemma 4 audio is both downloaded AND has won the benchmark.
    public var isAvailable: Bool {
        #if arch(arm64)
            return lock.withLock { model != nil }
        #else
            return false
        #endif
    }

    /// Download + load E4B, reporting progress (reuse the ModelLoadState UI the way
    /// MLXGemmaProvider.prepare does). Coarse or fine depending on the runtime's API.
    public func prepareModel(progress: @escaping @Sendable (Double) -> Void) async throws {
        if lock.withLock({ model != nil }) { progress(1.0); return }
        progress(0.05)
        // TODO(device): let m = try await Gemma4Audio.load(modelID) { progress($0) }
        // lock.withLock { model = m }
        progress(1.0)
    }

    public func startListening() throws -> AsyncStream<TranscriptSegment> {
        guard lock.withLock({ model != nil }) else { throw GemmaAudioError.modelNotLoaded }
        stopListening()
        return AsyncStream { continuation in
            lock.withLock { self.continuation = continuation; self.isCapturing = true }
            Task {
                // TODO(device): start mic capture (AVAudioEngine tap → audio frames),
                // feed frames to the E4B audio decoder, and yield CUMULATIVE text per
                // partial so TranscriptAccumulator's "latest wins" fold applies:
                //
                //   for await running in model.streamTranscribe(frames) {
                //       continuation.yield(TranscriptSegment(text: running.text,
                //                                            isFinal: running.isFinal))
                //   }
                //
                // If E4B exposes per-speaker turns, carry them on a richer segment for
                // P7 (calls). Apply the SAME lock-not-held-across-engine-stop rule the
                // AppleSpeech deadlock fix taught us.
            }
        }
    }

    public func stopListening() {
        // TODO(device): stop mic capture OUTSIDE the lock (see AppleSpeechTranscriber).
        let continuation = lock.withLock { () -> AsyncStream<TranscriptSegment>.Continuation? in
            let c = self.continuation
            self.continuation = nil
            self.isCapturing = false
            return c
        }
        continuation?.finish()
    }
}

public enum GemmaAudioError: Error, Sendable {
    case modelNotLoaded
}
