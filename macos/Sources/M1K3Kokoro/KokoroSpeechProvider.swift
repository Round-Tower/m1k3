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
//      neural voice", and nothing claims live neural synthesis. (Superseded — see
//      the 2026-06-11 review note below.)
//
//  The spike replaces ONLY the body of `synthesize(_:)` (phonemize → ONNX → PCM →
//  AVAudioPlayerNode) — every caller, the onboarding flow, and the seam are unchanged.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.55, Prior: Unknown
//  Review: Kev + claude-fable-5, 2026-06-11 — neural path now STREAMS chunks to
//  the renderer (speak(stream:)) with word timelines; Apple fallback only when
//  nothing was spoken at all; word-timing callbacks forwarded like lifecycle.
//  Confidence 0.8.
//  Review: Kev + claude-fable-5, 2026-07-16 (concurrency deep pass) — the
//  download path validated nothing: URLSession delivers HTTP 4xx/5xx as a
//  completed download, so an error body was staged as weights and the
//  file-existence fast path then trusted it forever (neural voice silently
//  dead, Apple fallback, no retry). Now: 2xx status gate in the delegate, a
//  plausibility floor that self-heals already-poisoned stages, and a
//  cancellation handler on the download await (all pinned in
//  KokoroDownloadValidationTests; the swallowed-preload-failure fallback
//  design is deliberately untouched).

import Foundation
import M1K3Inference
import M1K3LogCore
import M1K3Voice
import os

public final class KokoroSpeechProvider: SpeechProviderWithWordTiming, ModelPreloading, @unchecked Sendable {
    public let name = "kokoro"

    private static let log = M1K3Log.logger(.voice)

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
    /// Renders Kokoro's neural PCM (and the Apple-voice fallback) through M1K3's voice
    /// effect chain + playback. Concrete `EffectfulSpeechProvider`, not the protocol:
    /// the raw-PCM neural path needs `speak(rawPCM:)`, which the `SpeechProvider`
    /// protocol is too narrow to express.
    private let renderer: EffectfulSpeechProvider
    private let synthesizer: KokoroSynthesizer
    private let modelDirectory: URL

    public init(
        renderer: EffectfulSpeechProvider? = nil,
        modelDirectory: URL? = nil,
        voice: String = "bm_daniel"
    ) {
        let directory = modelDirectory ?? Self.defaultModelDirectory()
        self.modelDirectory = directory
        self.renderer = renderer ?? EffectfulSpeechProvider(chain: .m1k3Character)
        synthesizer = KokoroSynthesizer(modelDirectory: directory, voice: voice)
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

    public var onTimelineReady: (@Sendable (SpokenWordTimeline) -> Void)? {
        get { renderer.onTimelineReady }
        set { renderer.onTimelineReady = newValue }
    }

    public var onWordSpoken: (@Sendable (Range<Int>) -> Void)? {
        get { renderer.onWordSpoken }
        set { renderer.onWordSpoken = newValue }
    }

    // MARK: - Synthesis

    /// Neural path: text → sentence chunks → G2P → ONNX (kokoro-v1.0.onnx) → mono
    /// PCM @ 24 kHz streamed to the renderer chunk-by-chunk, each with its word
    /// timeline — playback starts after the first sentence, long answers are never
    /// truncated, and the karaoke highlight tracks the audio. Falls back to the
    /// effect-processed Apple voice when nothing was spoken at all (model not
    /// staged, all-OOV text, ORT error before the first chunk); a mid-utterance
    /// failure ends the utterance with what was already scheduled instead of
    /// re-speaking it all in the fallback voice.
    private func synthesize(_ utterance: SpeechUtterance) async {
        let chunks = synthesizer.synthesizeStream(text: utterance.text)
        let timed = AsyncThrowingStream<TimedPCMChunk, Error> { continuation in
            let task = Task {
                do {
                    for try await chunk in chunks {
                        continuation.yield(TimedPCMChunk(samples: chunk.samples, timeline: chunk.timeline))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
        let spoke = await renderer.speak(stream: timed, sampleRate: KokoroSynthesizer.sampleRate)
        if !spoke {
            await renderer.speak(utterance) // nothing phonemized/synthesized → Apple fallback
        }
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

        // Self-heal a poisoned stage: a pre-status-check download could stage an
        // HTTP error body as weights, and file-existence alone would then trust
        // it forever (Apple-voice fallback with no retry path). An implausibly
        // small file is deleted here so the download blocks below re-fetch it.
        for (dest, floor) in [
            (modelDest, KokoroDownloadValidation.modelFloorBytes),
            (voicesDest, KokoroDownloadValidation.voicesFloorBytes),
        ] where fileManager.fileExists(atPath: dest.path)
            && !KokoroDownloadValidation.isPlausibleStage(dest, floorBytes: floor)
        {
            Self.log.warning(
                "Kokoro staged file \(dest.lastPathComponent, privacy: .public) is implausibly small — discarding and re-downloading"
            )
            try? fileManager.removeItem(at: dest)
        }

        if fileManager.fileExists(atPath: modelDest.path),
           fileManager.fileExists(atPath: voicesDest.path)
        {
            lock.withLock { _ready = true }
            do {
                try await synthesizer.preload()
            } catch {
                // Apple-voice fallback is the intended recovery; the missing signal isn't.
                Self.log.warning("Kokoro preload failed: \(error.localizedDescription, privacy: .public)")
            }
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
        do {
            try await synthesizer.preload()
        } catch {
            // Same as the on-disk path above: Apple-voice fallback is the intended
            // recovery, but a "neural voice ready" that then can't synthesise needs a signal.
            Self.log.warning("Kokoro preload failed: \(error.localizedDescription, privacy: .public)")
        }
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

// MARK: - Download validation

/// The two checks that keep a bad download from permanently bricking the neural
/// voice (2026-07-16 concurrency deep pass): URLSession delivers HTTP 4xx/5xx as
/// a COMPLETED download, so an error body would be staged as weights and
/// file-existence would trust it forever. Status validation stops future bad
/// stages; the plausibility floor also self-heals installs already poisoned.
enum KokoroDownloadValidation {
    /// Deliberately far below the real payloads (~326 MB model, ~28 MB voices):
    /// the floors only need to reject staged HTML error pages (a few KB).
    static let modelFloorBytes: Int64 = 50 * 1024 * 1024
    static let voicesFloorBytes: Int64 = 1024 * 1024

    /// HTTP responses must be 2xx; anything else (file://, nil) is not this
    /// check's concern and passes through.
    static func isAcceptable(_ response: URLResponse?) -> Bool {
        guard let http = response as? HTTPURLResponse else { return true }
        return (200 ..< 300).contains(http.statusCode)
    }

    static func isPlausibleStage(_ url: URL, floorBytes: Int64) -> Bool {
        guard let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? Int64 else {
            return false
        }
        return size >= floorBytes
    }
}

/// The downloaded "weights" were an HTTP error body — surfaced instead of staged.
struct KokoroDownloadHTTPError: LocalizedError {
    let statusCode: Int
    var errorDescription: String? {
        "Kokoro download failed with HTTP status \(statusCode)"
    }
}

// MARK: - Progress-reporting download

/// A small URLSession download wrapper that reports a 0…1 fraction and moves the
/// finished file to a destination. Lives here (not in the app) so the whole staging
/// path is in the isolated Kokoro target. `internal` (not private) so the
/// cancellation and staging contracts are pinned in KokoroDownloadValidationTests
/// via file:// URLs — no network in the suite.
///
/// `@unchecked Sendable` safety: the continuation lives behind `lock` and is
/// consumed by `takeContinuation()` — an atomic take-and-nil — so exactly one of
/// its resumers (the delegate callbacks on URLSession's serial queue, or the
/// cancellation handler on whatever thread cancellation lands) ever receives it.
/// Resume-exactly-once holds by construction, with NO dependency on URLSession
/// delivering a delegate callback for a task cancelled before resume().
final class FileDownloader: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let progress: @Sendable (Double) -> Void
    private let destination: URL
    private let lock = NSLock()
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

    /// Atomically claim the continuation: whoever takes it resumes it; everyone
    /// else sees nil and no-ops.
    private func takeContinuation() -> CheckedContinuation<Void, Error>? {
        lock.withLock {
            let taken = continuation
            continuation = nil
            return taken
        }
    }

    private func run(_ url: URL) async throws {
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        let task = session.downloadTask(with: url)
        // Cancellation-responsive without trusting delegate ordering: onCancel
        // claims the continuation itself, and the pre-cancelled entry path is
        // caught by the isCancelled check below — so a cancelled prepare never
        // rides out ~326 MB and the continuation can never leak, whatever
        // URLSession does with a task cancelled before resume().
        defer { session.finishTasksAndInvalidate() }
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                lock.withLock { self.continuation = continuation }
                // onCancel may already have fired (a task cancelled before this
                // body ran) — it found no continuation to claim, so claim it here.
                if Task.isCancelled {
                    takeContinuation()?.resume(throwing: CancellationError())
                    return
                }
                task.resume()
            }
        } onCancel: {
            task.cancel()
            takeContinuation()?.resume(throwing: CancellationError())
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
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        // URLSession treats HTTP 4xx/5xx as a COMPLETED download — without this
        // check the error body would be staged as model weights (and trusted by
        // every future launch). Fail loudly instead; nothing touches the disk.
        guard KokoroDownloadValidation.isAcceptable(downloadTask.response) else {
            let status = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? -1
            takeContinuation()?.resume(throwing: KokoroDownloadHTTPError(statusCode: status))
            return
        }
        // Must move the temp file out synchronously — it's deleted when this returns.
        do {
            let fileManager = FileManager.default
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.moveItem(at: location, to: destination)
            takeContinuation()?.resume()
        } catch {
            takeContinuation()?.resume(throwing: error)
        }
    }

    func urlSession(_: URLSession, task _: URLSessionTask, didCompleteWithError error: Error?) {
        if let error {
            takeContinuation()?.resume(throwing: error)
        }
    }
}
