//
//  KokoroSpeechProvider.swift
//  M1K3Kokoro
//
//  The premium "M1K3 Voice" tier — Kokoro, an offline neural TTS that runs fully
//  on-device. Isolated in its own target (like M1K3MLX / M1K3WhisperKit) so the
//  heavy ONNX runtime + grapheme→phoneme machinery the synthesis spike will add
//  never touches the core build.
//
//  THIS SESSION = the seam + the real download, NOT the synthesis kernel:
//    • `prepare(progress:)` genuinely downloads the Kokoro model + voices from the
//      public release into the app container, reporting real 0…1 progress — so the
//      whole download → ModelPreloading → ready UX is proven end-to-end and the
//      weights are staged on disk, ready for the spike.
//    • `speak(_:)` HONESTLY delegates to a private AVSpeech fallback for now. Audio
//      is still Apple's voice this session; the onboarding copy says "preparing the
//      neural voice", and nothing claims live neural synthesis. See the TODO below.
//
//  The spike replaces ONLY the body of `synthesize(_:)` (phonemize → ONNX → PCM →
//  AVAudioPlayerNode) — every caller, the onboarding flow, and the seam are unchanged.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.55, Prior: Unknown

import Foundation
import M1K3Inference
import M1K3Voice

public final class KokoroSpeechProvider: SpeechProviderWithLifecycle, ModelPreloading, @unchecked Sendable {
    public let name = "kokoro"

    /// Public release of the Kokoro v1.0 ONNX weights (matches the filenames M1K3
    /// already ships under `models/kokoro/`). The spike loads these.
    private static let modelURL = URL(
        string: "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx"
    )!
    private static let voicesURL = URL(
        string: "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin"
    )!

    private let lock = NSLock()
    private var _ready = false
    /// Until the real ONNX neural synthesis lands, M1K3 Voice speaks through the
    /// EFFECT-PROCESSED Apple voice — Apple's synthesizer run through M1K3's voice
    /// effect chain — so it already sounds distinct from the Built-in tier. When
    /// Kokoro's neural PCM arrives it flows through the same chain + renderer.
    private let renderer: any SpeechProviderWithLifecycle
    private let modelDirectory: URL

    public init(
        renderer: (any SpeechProviderWithLifecycle)? = nil,
        modelDirectory: URL? = nil
    ) {
        self.renderer = renderer ?? EffectfulSpeechProvider(chain: .m1k3Character)
        self.modelDirectory = modelDirectory ?? Self.defaultModelDirectory()
    }

    // MARK: - SpeechProvider

    /// Available only once the model is staged — so the router/swap won't select
    /// M1K3 Voice until `prepare` has succeeded.
    public var isAvailable: Bool {
        lock.withLock { _ready }
    }

    /// Whether both weight files are already on disk — lets the app restore the
    /// M1K3 Voice tier on launch without kicking a fresh download.
    public var isModelStaged: Bool {
        let fileManager = FileManager.default
        return fileManager.fileExists(atPath: modelDirectory.appendingPathComponent("kokoro-v1.0.onnx").path)
            && fileManager.fileExists(atPath: modelDirectory.appendingPathComponent("voices-v1.0.bin").path)
    }

    public func speak(_ utterance: SpeechUtterance) async {
        await synthesize(utterance)
    }

    public func stop() async {
        await renderer.stop()
    }

    public func isSpeaking() async -> Bool {
        await renderer.isSpeaking()
    }

    // MARK: - Lifecycle callbacks (forwarded to the renderer so the avatar reacts)

    public var onSpeakingStarted: (@Sendable () -> Void)? {
        get { renderer.onSpeakingStarted }
        set { renderer.onSpeakingStarted = newValue }
    }

    public var onSpeakingEnded: (@Sendable () -> Void)? {
        get { renderer.onSpeakingEnded }
        set { renderer.onSpeakingEnded = newValue }
    }

    // MARK: - Synthesis

    private func synthesize(_ utterance: SpeechUtterance) async {
        // TODO(kokoro-spike): replace this with real NEURAL synthesis — phonemize
        // (G2P) → ONNX inference on the staged kokoro-v1.0.onnx + voices-v1.0.bin →
        // PCM → the same effect chain + renderer used below. The model is already
        // on disk (see `prepare`); only the PCM SOURCE changes. Until then, M1K3
        // Voice = Apple speech run through M1K3's voice-effect chain (a real,
        // distinct character — not the neural voice yet, but never silent and never
        // identical to Built-in).
        await renderer.speak(utterance)
    }

    // MARK: - ModelPreloading

    /// Stage the Kokoro weights into the app container, reporting real download
    /// progress. Idempotent — returns instantly once both files are present.
    public func prepare(progress: @escaping @Sendable (Double) -> Void) async throws {
        // Already staged this session — nothing to do. (The app layer also guards
        // concurrent calls via `isPreparingVoice`; this is the idempotent fast path.)
        if lock.withLock({ _ready }) {
            progress(1)
            return
        }
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: modelDirectory, withIntermediateDirectories: true)

        let modelDest = modelDirectory.appendingPathComponent("kokoro-v1.0.onnx")
        let voicesDest = modelDirectory.appendingPathComponent("voices-v1.0.bin")

        if fileManager.fileExists(atPath: modelDest.path),
           fileManager.fileExists(atPath: voicesDest.path)
        {
            lock.withLock { _ready = true }
            progress(1)
            return
        }

        // The model is ~326 MB, voices ~28 MB — weight the combined bar by size so
        // it advances proportionally rather than jumping at the file boundary.
        let modelWeight = 0.92
        let voicesWeight = 0.08

        if !fileManager.fileExists(atPath: modelDest.path) {
            try await FileDownloader.download(Self.modelURL, to: modelDest) { fraction in
                progress(fraction * modelWeight)
            }
        }
        if !fileManager.fileExists(atPath: voicesDest.path) {
            try await FileDownloader.download(Self.voicesURL, to: voicesDest) { fraction in
                progress(modelWeight + fraction * voicesWeight)
            }
        }

        lock.withLock { _ready = true }
        progress(1)
    }

    private static func defaultModelDirectory() -> URL {
        let base = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("M1K3/kokoro", isDirectory: true)
    }
}

// MARK: - Progress-reporting download

/// A small URLSession download wrapper that reports a 0…1 fraction and moves the
/// finished file to a destination. Lives here (not in the app) so the whole staging
/// path is in the isolated Kokoro target.
private final class FileDownloader: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let progress: @Sendable (Double) -> Void
    private let destination: URL
    private var continuation: CheckedContinuation<Void, Error>?

    private init(destination: URL, progress: @escaping @Sendable (Double) -> Void) {
        self.destination = destination
        self.progress = progress
    }

    static func download(
        _ url: URL,
        to destination: URL,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        let downloader = FileDownloader(destination: destination, progress: progress)
        try await downloader.run(url)
    }

    private func run(_ url: URL) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            self.continuation = continuation
            let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
            session.downloadTask(with: url).resume()
            session.finishTasksAndInvalidate()
        }
    }

    func urlSession(
        _: URLSession,
        downloadTask _: URLSessionDownloadTask,
        didWriteData _: Int64,
        totalBytesWritten written: Int64,
        totalBytesExpectedToWrite expected: Int64
    ) {
        guard expected > 0 else { return }
        progress(min(max(Double(written) / Double(expected), 0), 1))
    }

    func urlSession(
        _: URLSession,
        downloadTask _: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        // Must move the temp file out synchronously — it's deleted when this returns.
        do {
            let fileManager = FileManager.default
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.moveItem(at: location, to: destination)
            continuation?.resume()
        } catch {
            continuation?.resume(throwing: error)
        }
        continuation = nil
    }

    func urlSession(_: URLSession, task _: URLSessionTask, didCompleteWithError error: Error?) {
        if let error {
            continuation?.resume(throwing: error)
            continuation = nil
        }
    }
}
