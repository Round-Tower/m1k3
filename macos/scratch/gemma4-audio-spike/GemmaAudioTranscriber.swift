//
//  GemmaAudioTranscriber.swift  (SPIKE SKETCH — not wired, not compiled)
//  scratch/gemma4-audio-spike
//
//  Design sketch grounded in the REAL `gemma-4-swift-mlx` API (read from source
//  2026-06-06). Gemma 4 audio is BATCH (file, ≤30s, low-level multimodal path) —
//  NOT live streaming — so this is a P7 (call-transcription) tool, not the P6 mic
//  button. It deliberately does NOT conform to the live `TranscriptionProvider`
//  seam (startListening/AsyncStream); it offers a file→[TranscriptSegment] batch
//  method instead (see SPIKE.md option A/B).
//
//  `// TODO(device)` = the low-level multimodal call I can't compile/run here.
//  Promote to an isolated package/target only after the benchmark gate.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.4 (spike), Prior: Unknown

import AVFoundation
import Foundation
import M1K3Voice

// import Gemma4Swift   // TODO(device): VincentGourbin/gemma-4-swift-mlx — ISOLATED package only

/// Batch transcription via Gemma 4 E4B native audio. Loads once, transcribes audio
/// files by chunking into ≤30s windows (the model's hard cap) and concatenating.
public final class GemmaAudioTranscriber: @unchecked Sendable {
    /// The model's hard limit: 30s @ 16kHz (per AudioProcessor source).
    public static let maxChunkSeconds: Double = 30

    private let model: Model
    private let lock = NSLock()
    private var pipeline: AnyObject? // TODO(device): Gemma4Pipeline

    public enum Model: String, Sendable {
        case e4b4bit = "mlx-community/gemma-4-e4b-it-4bit" // spike default — audio tower, ~laptop-sized
        case e2b4bit = "mlx-community/gemma-4-e2b-it-4bit" // smaller/faster, lower accuracy
    }

    public init(model: Model = .e4b4bit) {
        self.model = model
    }

    public var isAvailable: Bool {
        #if arch(arm64)
            return lock.withLock { pipeline != nil }
        #else
            return false
        #endif
    }

    public func prepareModel(progress: @escaping @Sendable (Double) -> Void) async throws {
        if lock.withLock({ pipeline != nil }) { progress(1.0); return }
        progress(0.05)
        // TODO(device):
        //   let p = Gemma4Pipeline()
        //   try await p.load(.e4b4bit, downloadIfNeeded: true)   // reuse ModelLoadState UI
        //   lock.withLock { pipeline = p }
        progress(1.0)
    }

    /// Transcribe an audio file by splitting into ≤30s windows, transcribing each via
    /// the multimodal audio path, and timestamping segments by window offset.
    public func transcribe(fileURL: URL) async throws -> [TranscriptSegment] {
        guard lock.withLock({ pipeline != nil }) else { throw GemmaAudioError.modelNotLoaded }
        let windows = try Self.chunkWindows(fileURL: fileURL, maxSeconds: Self.maxChunkSeconds)
        var segments: [TranscriptSegment] = []
        for _ in windows {
            // TODO(device): the LOW-LEVEL multimodal call (chat() is text-only):
            //   let features = try await AudioProcessor.processAudio(url: window.url)   // log-mel→Conformer
            //   let prompt = Gemma4Processor.build(text: "Transcribe the speech verbatim.",
            //                                      hasAudio: true, numAudioTokens: features.tokenCount)
            //   let text = try await multimodalModel.generate(prompt, audio: features)
            //   For P7 diarization, try: "Transcribe and label each speaker." → parse turns.
            let text = "" // placeholder
            if !text.isEmpty {
                segments.append(TranscriptSegment(text: text, isFinal: true))
            }
        }
        return segments
    }

    // MARK: - Pure, testable: window planning (the one bit with real edge cases)

    public struct Window: Equatable, Sendable {
        public let url: URL
        public let startSeconds: Double
    }

    /// Plan ≤maxSeconds windows over a file's duration. (The actual PCM slicing is
    /// TODO(device); this is the offset math, which IS unit-testable when promoted.)
    static func chunkWindows(fileURL: URL, maxSeconds: Double) throws -> [Window] {
        let asset = AVURLAsset(url: fileURL)
        let duration = CMTimeGetSeconds(asset.duration)
        guard duration > 0 else { return [] }
        var windows: [Window] = []
        var start = 0.0
        while start < duration {
            windows.append(Window(url: fileURL, startSeconds: start)) // TODO(device): slice to a temp file
            start += maxSeconds
        }
        return windows
    }
}

public enum GemmaAudioError: Error, Sendable {
    case modelNotLoaded
}
