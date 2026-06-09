//
//  KokoroSynthesizer.swift
//  M1K3Kokoro
//
//  The neural synthesis core: text → phoneme tokens (KokoroG2P) → ONNX inference on
//  the staged kokoro-v1.0.onnx with the per-length voice style (KokoroVoices) → mono
//  float PCM @ 24 kHz.
//
//  An actor guards the load lifecycle, but the OS-blocking work — the ~326 MB session
//  init and the ~220 ms inference — runs on `Task.detached`, NOT on the actor's
//  cooperative thread (blocking a cooperative-pool thread for seconds is the classic
//  Swift-concurrency starvation anti-pattern). Loading is single-flight via a stored
//  Task: concurrent callers await the same load rather than double-reading the model.
//
//  This is the verify-by-launch adapter (no `swift test` for live ORT inference); the
//  pieces it composes — G2P assembly, the npz style read — are pure + unit-tested, and
//  Phase-1 proved the ORT path matches the Python reference at 0.999 correlation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.6, Prior: Unknown
//  Review: claude (PR #13) — hoisted blocking load/inference off the cooperative pool
//  via Task.detached + single-flight load Task. Confidence 0.7.
//

import Foundation
import OnnxRuntimeBindings

public actor KokoroSynthesizer {
    public struct SynthError: Error, CustomStringConvertible {
        public let description: String
    }

    /// Kokoro's native output sample rate.
    public static let sampleRate: Double = 24000

    /// Immutable loaded state. `@unchecked Sendable` is sound here: it is built once and
    /// never mutated, and ORT sessions are thread-safe for concurrent `run()`, so the
    /// box can be shared across the detached inference tasks without a data race.
    private final class Loaded: @unchecked Sendable {
        let env: ORTEnv
        let session: ORTSession
        let voices: KokoroVoices
        let g2p: KokoroG2P

        init(modelURL: URL, voicesURL: URL) throws {
            let fileManager = FileManager.default
            guard fileManager.fileExists(atPath: modelURL.path),
                  fileManager.fileExists(atPath: voicesURL.path)
            else {
                throw SynthError(description: "model files not staged at \(modelURL.deletingLastPathComponent().path)")
            }
            env = try ORTEnv(loggingLevel: .warning)
            session = try ORTSession(env: env, modelPath: modelURL.path, sessionOptions: nil)
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

    public var isLoaded: Bool {
        loaded != nil
    }

    /// Eagerly load the model/voices/dictionary (e.g. at voice-prepare time) so the
    /// first spoken utterance isn't gated on the ~326 MB session init.
    public func preload() async throws {
        _ = try await ensureLoaded()
    }

    /// Synthesize `text` to mono float PCM @ 24 kHz. Empty result ⇒ nothing to say
    /// (all words out-of-vocabulary); the caller should fall back.
    public func synthesize(text: String, speed: Float = 1.0) async throws -> [Float] {
        let box = try await ensureLoaded()
        let phonemes = box.g2p.phonemeTokens(text)
        guard !phonemes.isEmpty else { return [] }
        let style = try box.voices.style(voice: voice, tokenCount: phonemes.count)
        let modelTokens: [Int64] = [0] + phonemes.map(Int64.init) + [0]
        // Inference is OS-blocking; run it off the cooperative pool. Safe to run
        // concurrently on the shared session (ORT `run()` is thread-safe).
        return try await Task.detached(priority: .userInitiated) {
            try Self.infer(box, tokens: modelTokens, style: style, speed: speed)
        }.value
    }

    /// Single-flight load: the first caller creates the detached load Task and stores
    /// it; concurrent callers (actor reentrancy across the `await`) await the SAME task,
    /// so the ~326 MB model is read exactly once. The stored handle is cleared on
    /// failure so a later call can retry.
    private func ensureLoaded() async throws -> Loaded {
        if let loaded { return loaded }
        if let loadTask { return try await loadTask.value }

        let modelURL = modelDirectory.appendingPathComponent("kokoro-v1.0.onnx")
        let voicesURL = modelDirectory.appendingPathComponent("voices-v1.0.bin")
        let task = Task.detached(priority: .userInitiated) {
            try Loaded(modelURL: modelURL, voicesURL: voicesURL)
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

    /// Pure ORT forward pass on the loaded session. Runs off-actor (see `synthesize`).
    private static func infer(_ box: Loaded, tokens: [Int64], style: [Float], speed: Float) throws -> [Float] {
        let tokenData = NSMutableData(bytes: tokens, length: tokens.count * 8)
        let styleData = NSMutableData(bytes: style, length: style.count * 4)
        var speedValue = speed
        let speedData = NSMutableData(bytes: &speedValue, length: 4)

        let tokensTensor = try ORTValue(
            tensorData: tokenData, elementType: .int64,
            shape: [1, NSNumber(value: tokens.count)]
        )
        let styleTensor = try ORTValue(tensorData: styleData, elementType: .float, shape: [1, 256])
        let speedTensor = try ORTValue(tensorData: speedData, elementType: .float, shape: [1])

        let outputs = try box.session.run(
            withInputs: ["tokens": tokensTensor, "style": styleTensor, "speed": speedTensor],
            outputNames: ["audio"],
            runOptions: nil
        )
        guard let audio = outputs["audio"] else {
            throw SynthError(description: "model produced no audio output")
        }
        let audioData = try audio.tensorData()
        let count = audioData.length / 4
        var samples = [Float](repeating: 0, count: count)
        samples.withUnsafeMutableBytes { audioData.getBytes($0.baseAddress!, length: audioData.length) }
        return samples
    }
}
