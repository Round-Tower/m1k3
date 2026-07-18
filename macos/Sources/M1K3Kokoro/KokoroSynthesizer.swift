//
//  KokoroSynthesizer.swift
//  M1K3Kokoro
//
//  The neural synthesis core: text → phoneme tokens (KokoroG2P) → MLX inference on
//  the staged Kokoro weights (config.json + model.safetensors) with the per-length
//  voice style (KokoroVoices) → mono float PCM @ 24 kHz.
//
//  An actor guards the load lifecycle, but the OS-blocking work — the weight-load
//  and the per-chunk forward pass — runs on `Task.detached`, NOT on the actor's
//  cooperative thread (blocking a cooperative-pool thread for seconds is the classic
//  Swift-concurrency starvation anti-pattern). Loading is single-flight via a stored
//  Task: concurrent callers await the same load rather than double-reading the model.
//
//  This is the verify-by-launch adapter (no `swift test` for live MLX/Metal
//  inference — the metallib wall, see `../../CLAUDE.md`); the pieces it composes —
//  G2P assembly, the npz style read, the token-boundary assembly — are pure +
//  unit-tested, and Phase-1 proved the (prior ORT) path matches the Python
//  reference at 0.999 correlation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.6, Prior: Unknown
//  Review: claude (PR #13) — hoisted blocking load/inference off the cooperative pool
//  via Task.detached + single-flight load Task. Confidence 0.7.
//  Review: Kev + claude-fable-5, 2026-06-11 — synthesizeStream: sentence-chunked
//  synthesis (SpeechChunker) with per-chunk word timelines; synthesize() now
//  concatenates the stream — the silent 510-token truncation is gone.
//  Confidence 0.8.
//  Review: Kev + claude-fable-5, 2026-07-18 — the ONNX Runtime backend replaced
//  with a pure-MLX one (the vendored StyleTTS2/Kokoro port under
//  `MLX/Vendored/`, MIT, Blaizzy/mlx-audio-swift): `Loaded` now holds a
//  `KokoroModel` instead of an `ORTEnv`/`ORTSession`; `infer` runs its forward
//  pass instead of an ORT `run()`. Every caller of this file, KokoroG2P,
//  KokoroVoices, and the actor/single-flight/Task.detached structure are
//  UNCHANGED — the vocab ids KokoroG2P emits were verified byte-for-byte
//  against the new weights' `config.json` vocab map before this port began
//  (punctuation ids 1–15, space=16, and every inflection-suffix phoneme id
//  the G2P hardcodes all match). This removes the onnxruntime dependency
//  entirely (the visionOS unlock — onnxruntime-swift-package-manager had no
//  xrOS slice). Confidence 0.75 (the pure/testable seams — token assembly,
//  weight-key sanitize — are red-first pinned; live synthesis is
//  verify-by-launch/SelfTest, the metallib wall blocks it under `swift test`).
//

import Foundation
import M1K3Voice
import MLX

/// One synthesized piece of an utterance: its audio and the word timing for
/// exactly that audio. `timeline.text` is the FULL utterance text (ranges
/// already offset); times are relative to the CHUNK's own start — the playback
/// layer anchors them globally as it schedules.
public struct SynthesizedChunk: Sendable {
    public let samples: [Float]
    public let timeline: SpokenWordTimeline

    public init(samples: [Float], timeline: SpokenWordTimeline) {
        self.samples = samples
        self.timeline = timeline
    }
}

public actor KokoroSynthesizer {
    public struct SynthError: Error, CustomStringConvertible {
        public let description: String
    }

    /// Kokoro's native output sample rate.
    public static let sampleRate: Double = 24000

    /// Immutable loaded state. `@unchecked Sendable` is sound here: it is built once and
    /// never mutated after `init`, and every inference call in this file runs
    /// SEQUENTIALLY (the per-chunk loop in `produceChunks` awaits each detached
    /// `infer` before starting the next) — never concurrently against the same
    /// `KokoroModel`, so the box can be shared across the detached inference tasks
    /// without a data race, whatever mlx-swift's own thread-safety story is for
    /// truly concurrent forward passes on one model instance.
    private final class Loaded: @unchecked Sendable {
        let model: KokoroModel
        let voices: KokoroVoices
        let g2p: KokoroG2P

        init(modelDirectory: URL, voicesURL: URL) throws {
            let fileManager = FileManager.default
            let configURL = modelDirectory.appendingPathComponent("config.json")
            let weightsURL = modelDirectory.appendingPathComponent("model.safetensors")
            guard fileManager.fileExists(atPath: configURL.path),
                  fileManager.fileExists(atPath: weightsURL.path),
                  fileManager.fileExists(atPath: voicesURL.path)
            else {
                throw SynthError(description: "model files not staged at \(modelDirectory.path)")
            }
            model = try KokoroModel.fromModelDirectory(modelDirectory)
            voices = try KokoroVoices(contentsOf: voicesURL)
            g2p = try KokoroG2P.bundled()
        }
    }

    private let modelDirectory: URL
    private let voice: String
    private var loaded: Loaded?
    private var loadTask: Task<Loaded, Error>?

    public init(modelDirectory: URL, voice: String = "bm_daniel") {
        self.modelDirectory = modelDirectory
        self.voice = voice
    }

    /// Eagerly load the model/voices/dictionary (e.g. at voice-prepare time) so the
    /// first spoken utterance isn't gated on the ~326 MB session init.
    public func preload() async throws {
        _ = try await ensureLoaded()
    }

    /// Synthesize `text` to mono float PCM @ 24 kHz. Empty result ⇒ nothing to say
    /// (all words out-of-vocabulary); the caller should fall back.
    ///
    /// Concatenates `synthesizeStream` — long text is sentence-chunked under the
    /// model's 510-token context, never truncated (the old single-pass cap
    /// silently dropped everything past ~150 words).
    public func synthesize(text: String, speed: Float = 1.0) async throws -> [Float] {
        var all: [Float] = []
        for try await chunk in synthesizeStream(text: text, speed: speed) {
            all.append(contentsOf: chunk.samples)
        }
        return all
    }

    /// Synthesize `text` chunk-by-chunk: sentence-aware pieces ≤ the model's
    /// 510-token context, each yielded with its word timeline as soon as its
    /// inference finishes — playback can start after the first sentence.
    public nonisolated func synthesizeStream(
        text: String,
        speed: Float = 1.0
    ) -> AsyncThrowingStream<SynthesizedChunk, Error> {
        AsyncThrowingStream { continuation in
            let producer = Task {
                do {
                    try await self.produceChunks(text: text, speed: speed) {
                        continuation.yield($0)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in producer.cancel() }
        }
    }

    private func produceChunks(
        text: String,
        speed: Float,
        yield: @Sendable (SynthesizedChunk) -> Void
    ) async throws {
        let box = try await ensureLoaded()
        let ranges = SpeechChunker.chunkRanges(
            text,
            tokenCount: { box.g2p.annotatedTokens(String($0)).tokens.count },
            maxTokens: KokoroG2P.maxTokens
        )
        let fullText = text as NSString
        for range in ranges {
            try Task.checkCancellation()
            let chunkText = fullText.substring(with: NSRange(location: range.lowerBound, length: range.count))
            let result = box.g2p.annotatedTokens(chunkText)
            guard !result.tokens.isEmpty else { continue } // all-OOV chunk: no audio to time
            let style = try box.voices.style(voice: voice, tokenCount: result.tokens.count)
            let modelTokens = KokoroMLXInput.modelTokens(result.tokens)
            // Inference is OS-blocking (Metal dispatch + a synchronous host-side
            // eval); run it off the cooperative pool. Sequential across chunks by
            // construction (this loop awaits each chunk before the next), so no
            // concurrent forward pass ever shares `box.model`.
            let samples = try await Task.detached(priority: .userInitiated) {
                try Self.infer(box, tokens: modelTokens, style: style, speed: speed)
            }.value
            let timeline = KokoroWordTiming.timeline(
                text: text,
                result: result,
                audioDuration: Double(samples.count) / Self.sampleRate,
                textOffset: range.lowerBound
            )
            yield(SynthesizedChunk(samples: samples, timeline: timeline))
        }
    }

    /// Single-flight load: the first caller creates the detached load Task and stores
    /// it; concurrent callers (actor reentrancy across the `await`) await the SAME task,
    /// so the model is read exactly once. The stored handle is cleared on
    /// failure so a later call can retry.
    private func ensureLoaded() async throws -> Loaded {
        if let loaded { return loaded }
        if let loadTask { return try await loadTask.value }

        let directory = modelDirectory
        let voicesURL = modelDirectory.appendingPathComponent("voices-v1.0.bin")
        let task = Task.detached(priority: .userInitiated) {
            try Loaded(modelDirectory: directory, voicesURL: voicesURL)
        }
        loadTask = task
        do {
            let box = try await task.value
            loaded = box
            loadTask = nil
            return box
        } catch {
            loadTask = nil
            throw error
        }
    }

    /// Pure MLX forward pass on the loaded model. Runs off-actor (see `synthesize`).
    private static func infer(_ box: Loaded, tokens: [Int32], style: [Float], speed: Float) throws -> [Float] {
        let inputIds = MLXArray(tokens).reshaped([1, -1])
        let refS = MLXArray(style).reshaped([1, style.count])
        let (audio, _) = box.model(inputIds: inputIds, refS: refS, speed: speed)
        return audio.reshaped([-1]).asArray(Float.self)
    }
}
