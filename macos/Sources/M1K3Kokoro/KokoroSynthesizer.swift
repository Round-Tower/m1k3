//
//  KokoroSynthesizer.swift
//  M1K3Kokoro
//
//  The neural synthesis core: text → phoneme tokens (KokoroG2P) → ONNX inference on
//  the staged kokoro-v1.0.onnx with the per-length voice style (KokoroVoices) → mono
//  float PCM @ 24 kHz. An actor so the (non-Sendable) ONNX session is loaded once and
//  serialised — concurrent speak() calls queue rather than double-load the ~326 MB model.
//
//  This is the verify-by-launch adapter (no `swift test` for live ORT inference); the
//  pieces it composes — G2P assembly, the npz style read — are pure + unit-tested, and
//  Phase-1 proved the ORT path matches the Python reference at 0.999 correlation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.6, Prior: Unknown
//

import Foundation
import OnnxRuntimeBindings

public actor KokoroSynthesizer {
    public struct SynthError: Error, CustomStringConvertible {
        public let description: String
    }

    /// Kokoro's native output sample rate.
    public static let sampleRate: Double = 24000

    private let modelDirectory: URL
    private let voice: String

    // Loaded-once state (actor-isolated; ORT types aren't Sendable).
    private var env: ORTEnv?
    private var session: ORTSession?
    private var voices: KokoroVoices?
    private var g2p: KokoroG2P?

    public init(modelDirectory: URL, voice: String = "bm_daniel") {
        self.modelDirectory = modelDirectory
        self.voice = voice
    }

    public var isLoaded: Bool {
        session != nil
    }

    /// Eagerly load the model/voices/dictionary (e.g. at voice-prepare time) so the
    /// first spoken utterance isn't gated on the ~326 MB session init.
    public func preload() throws {
        try loadIfNeeded()
    }

    /// Synthesize `text` to mono float PCM @ 24 kHz. Empty result ⇒ nothing to say
    /// (all words out-of-vocabulary); the caller should fall back.
    public func synthesize(text: String, speed: Float = 1.0) throws -> [Float] {
        try loadIfNeeded()
        guard let session, let voices, let g2p else {
            throw SynthError(description: "synthesizer not loaded")
        }

        let phonemes = g2p.phonemeTokens(text)
        guard !phonemes.isEmpty else { return [] }
        let style = try voices.style(voice: voice, tokenCount: phonemes.count)
        let modelTokens: [Int64] = [0] + phonemes.map(Int64.init) + [0]

        let tokenData = NSMutableData(bytes: modelTokens, length: modelTokens.count * 8)
        let styleData = NSMutableData(bytes: style, length: style.count * 4)
        var speedValue = speed
        let speedData = NSMutableData(bytes: &speedValue, length: 4)

        let tokensTensor = try ORTValue(
            tensorData: tokenData, elementType: .int64,
            shape: [1, NSNumber(value: modelTokens.count)]
        )
        let styleTensor = try ORTValue(
            tensorData: styleData, elementType: .float, shape: [1, 256]
        )
        let speedTensor = try ORTValue(
            tensorData: speedData, elementType: .float, shape: [1]
        )

        let outputs = try session.run(
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

    private func loadIfNeeded() throws {
        if session != nil { return }
        let modelURL = modelDirectory.appendingPathComponent("kokoro-v1.0.onnx")
        let voicesURL = modelDirectory.appendingPathComponent("voices-v1.0.bin")
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: modelURL.path),
              fileManager.fileExists(atPath: voicesURL.path)
        else {
            throw SynthError(description: "model files not staged at \(modelDirectory.path)")
        }
        let env = try ORTEnv(loggingLevel: .warning)
        let session = try ORTSession(env: env, modelPath: modelURL.path, sessionOptions: nil)
        let voices = try KokoroVoices(contentsOf: voicesURL)
        let g2p = try KokoroG2P.bundled()
        self.env = env
        self.session = session
        self.voices = voices
        self.g2p = g2p
    }
}
