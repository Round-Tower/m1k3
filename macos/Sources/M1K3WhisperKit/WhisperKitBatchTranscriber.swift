//
//  WhisperKitBatchTranscriber.swift
//  M1K3WhisperKit
//
//  Batch (file → segments) transcription via WhisperKit — the engine the
//  M1K3Calls `BatchTranscriptionProvider` seam was built for. Records-then-
//  processes: a finished `.caf`/audio URL in, timestamped `CallTranscriptSegment`s
//  out, feeding the SAME `CallIntelligencePipeline` the transcript-import path
//  already proves. The Gemma-4 E4B shadow provider drops in behind the same seam.
//
//  Split, as everywhere in this package: the WhisperKit/CoreML call is a thin
//  verify-by-launch adapter (needs a downloaded model + a real file); the segment
//  MAPPING — trim, strip special tokens, drop non-speech markers, order — is a
//  pure static func, unit-tested without a model.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.7,
//  Prior: the internal call-pipeline project WhisperKit-batch (Kev) — re-pointed at M1K3's
//  file-based BatchTranscriptionProvider seam; buffer/PowerEfficiency surface dropped.

import Foundation
import M1K3Calls
@preconcurrency import WhisperKit

/// `@unchecked Sendable`: the loaded model is guarded by `lock`; WhisperKit's own
/// components are actor/queue-isolated. Mirrors `WhisperKitProvider` (live STT).
public final class WhisperKitBatchTranscriber: BatchTranscriptionProvider, @unchecked Sendable {
    public let name = "WhisperKit (batch)"

    private let model: String
    private let downloadBase: URL?
    private let lock = NSLock()
    private var whisperKit: WhisperKit?

    /// - Parameter model: WhisperKit variant. `base.en` (~142MB) balances size and
    ///   accuracy; batch transcription is latency-tolerant, so `small.en` is a fine
    ///   sharper upgrade. Can share a variant with the live provider's cache.
    /// - Parameter downloadBase: Root URL for model storage. Should match the live
    ///   `WhisperKitProvider`'s base so both share the same cached weights.
    public init(model: String = "base.en", downloadBase: URL? = nil) {
        self.model = model
        self.downloadBase = downloadBase
    }

    /// Available only on Apple Silicon once a model has been loaded.
    public var isAvailable: Bool {
        #if arch(arm64)
            return lock.withLock { whisperKit != nil }
        #else
            return false
        #endif
    }

    /// Download + load the WhisperKit model, reporting coarse progress. Idempotent.
    public func prepareModel(progress: @escaping @Sendable (Double) -> Void) async throws {
        if lock.withLock({ whisperKit != nil }) { progress(1.0); return }
        progress(0.05)
        let config = WhisperKitConfig(model: model, verbose: false, prewarm: true, load: true)
        config.downloadBase = downloadBase
        let kit = try await WhisperKit(config)
        lock.withLock { whisperKit = kit }
        progress(1.0)
    }

    public func transcribe(fileURL: URL) async throws -> [CallTranscriptSegment] {
        guard let kit = lock.withLock({ whisperKit }) else {
            throw CallIntelligenceError.noTranscriberAvailable
        }
        let results: [TranscriptionResult]
        do {
            results = try await kit.transcribe(audioPath: fileURL.path)
        } catch {
            throw CallIntelligenceError.transcriptionFailed(error.localizedDescription)
        }
        let spans = results.flatMap(\.segments).map {
            (start: TimeInterval($0.start), text: $0.text)
        }
        return Self.makeSegments(from: spans)
    }

    // MARK: - Pure mapping (unit-tested)

    /// Turn WhisperKit's timed spans into ordered, cleaned `CallTranscriptSegment`s:
    /// strip special tokens, trim, drop empties + whole-bracket non-speech markers,
    /// sort by start time. Speaker attribution is left to the DiarizationAligner.
    public static func makeSegments(
        from spans: [(start: TimeInterval, text: String)]
    ) -> [CallTranscriptSegment] {
        spans
            .map { (start: $0.start, text: clean($0.text)) }
            .filter { !$0.text.isEmpty }
            .sorted { $0.start < $1.start }
            .map { CallTranscriptSegment(text: $0.text, startTime: $0.start) }
    }

    /// Strip WhisperKit `<|…|>` special tokens, trim, and treat a whole-bracket
    /// non-speech marker (`[BLANK_AUDIO]`, `[Music]`) as empty. Brackets embedded in
    /// real speech (`words [pause] more`) are preserved.
    private static func clean(_ raw: String) -> String {
        let stripped = raw
            .replacingOccurrences(of: "<\\|[^|]*\\|>", with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if stripped.range(of: "^\\[[^\\[\\]]*\\]$", options: .regularExpression) != nil {
            return ""
        }
        return stripped
    }
}
