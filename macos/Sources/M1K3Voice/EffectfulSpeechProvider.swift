//
//  EffectfulSpeechProvider.swift
//  M1K3Voice
//
//  Gives M1K3 its own voice TODAY by running Apple's synthesized speech through the
//  pure VoiceEffectChain before playback: AVSpeechSynthesizer.write → [Float] PCM →
//  effect chain → AVAudioEngine. So "M1K3 Voice" sounds distinct from the plain
//  Built-in tier even before the Kokoro neural model lands — and when it does, its
//  PCM flows through the exact same chain + playback path (this provider becomes
//  Kokoro's downstream renderer).
//
//  The DSP is unit-tested in VoiceEffectTests; THIS file is the thin AVFoundation
//  adapter — verify-by-launch. Any failure in the effect path falls back to plain
//  Apple speech, so M1K3 is never left silent.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.55, Prior: Unknown
//  Review: claude-opus-4-8, 2026-06-09 (PR #10) — stop() fires onSpeakingEnded once
//  (was double-firing on interrupted playback); speak() now interrupts an in-flight
//  utterance so a concurrent call can't leak play()'s continuation. Confidence 0.75.
//  Review: Kev + claude-fable-5, 2026-06-11 — Apple path gains word timing via
//  delegate↔buffer correlation in SynthBox (markers deliver nothing — probed);
//  speak(_:) routes through the streaming session; finishPlayback() now stops
//  the node (a drained player reads isPlaying forever — integration-test catch).
//  Confidence 0.75.
//  Review: Kev + claude-fable-5, 2026-07-16, Confidence 0.85 (concurrency deep
//  pass, finding 9) — SynthBox resumed its offline-render continuation ONLY on
//  write's zero-frame sentinel, so a stop/barge-in (stopSpeaking(at:.immediate))
//  mid-render never delivered it and the continuation LEAKED — every caller
//  awaiting speak() hung forever (the MCP speak HTTP call, the voice loop). Added
//  didFinish (idempotent success backstop when no sentinel arrives) and didCancel
//  (resume exactly once with CancellationError, sharing finish's done flag), plus
//  a `catch is CancellationError` in speak(_:) so a barge-in ends silently rather
//  than re-speaking the stopped utterance via plainSpeak. This is the Apple
//  offline-render path (the M1K3 Voice + Built-in tiers, and Kokoro's Apple
//  FALLBACK — Kokoro's neural path plays its own PCM and doesn't hit
//  synthesizeToFloats). The exactly-once contract is unit-tested
//  (SynthBoxCancellationTests, headless); the real cancelled-render WIRING (a
//  live AVSpeechSynthesizer delivering didCancel) stays verify-by-launch.
//  Review fold (same PR, bot catch): SynthBox now carries the AVSpeechUtterance
//  it OWNS and guards every delegate callback on `utterance === owner`. The
//  synthesizer.delegate is a single shared property reassigned per render, so
//  without this a barge-in's stale didCancel for utterance A could land on B's
//  box and silence B (the catch-CancellationError makes it never speak) — the
//  opposite of the fix. ingest also short-circuits once `done`.

import AVFoundation
import Foundation

public final class EffectfulSpeechProvider: NSObject, SpeechProviderWithWordTiming, @unchecked Sendable {
    public let name = "m1k3-effect-voice"

    // `chain`/`player`/`configureEngineIfNeeded`/`streamingSession` are internal
    // (not private) for EffectfulSpeechProvider+Streaming.swift — the chunked
    // playback path lives there to keep this file inside the length budget.
    let chain: VoiceEffectChain
    private let synthesizer = AVSpeechSynthesizer()
    private let engine = AVAudioEngine()
    let player = AVAudioPlayerNode()
    /// Used only when the effect/render path fails — M1K3 still speaks, just dry.
    private let plainFallback: AVSpeechProvider

    public var onSpeakingStarted: (@Sendable () -> Void)?
    public var onSpeakingEnded: (@Sendable () -> Void)?
    /// Word-timing seam (SpeechProviderWithWordTiming): the utterance timeline as
    /// soon as it is known (growing per chunk), and the word currently being heard.
    public var onTimelineReady: (@Sendable (SpokenWordTimeline) -> Void)?
    public var onWordSpoken: (@Sendable (Range<Int>) -> Void)?

    @MainActor private var engineConfigured = false
    @MainActor private var configuredSampleRate: Double = 0
    /// Cached speaking flag — flipped by the fireSpeaking* helpers at the exact
    /// seams that fire onSpeakingStarted/Ended, so isSpeaking() never has to poll
    /// the audio engine (a cross-QoS lock-hold + a Thread-Performance-Checker hang
    /// risk when read ~30 fps). See SpeakingState.swift.
    @MainActor private var speakingState = SpeakingState()
    /// The in-flight playback wait, so `stop()` can resume it (the .dataPlayedBack
    /// completion is NOT delivered after player.stop(), which would otherwise hang).
    @MainActor private var playbackContinuation: CheckedContinuation<Void, Never>?
    /// The in-flight chunked playback (speak(stream:)), so `stop()` can cancel it.
    @MainActor var streamingSession: StreamingPlaybackSession?
    /// Observes `.AVAudioEngineConfigurationChange` so an output-route flip (BLE
    /// headphones arriving, AirPods leaving) rebinds playback to the NEW default
    /// device instead of leaving the voice pinned to the old one. Same pattern as
    /// AppleSpeechTranscriber's input-side observer.
    private var configObserver: NSObjectProtocol?

    public init(chain: VoiceEffectChain = .m1k3Character, fallback: AVSpeechProvider = AVSpeechProvider()) {
        self.chain = chain
        plainFallback = fallback
        super.init()
        configObserver = NotificationCenter.default.addObserver(
            forName: .AVAudioEngineConfigurationChange, object: engine, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.handleEngineConfigurationChange() }
        }
    }

    deinit {
        if let configObserver {
            NotificationCenter.default.removeObserver(configObserver)
        }
    }

    /// The output route changed (live 2026-07-02: BLE headphones connected
    /// mid-session got system sounds but no M1K3 voice until relaunch). The
    /// engine STOPS itself when this fires, so an in-flight utterance's
    /// .dataPlayedBack completion will never arrive — unwind it exactly like
    /// stop() does (finishPlayback resumes play()'s wait, which fires its single
    /// onSpeakingEnded as it unwinds), and drop the configured flag so the next
    /// utterance reconnects + restarts the engine against the new default device.
    @MainActor
    private func handleEngineConfigurationChange() {
        engineConfigured = false
        streamingSession?.cancel()
        streamingSession = nil
        finishPlayback()
        speakingState.end()
    }

    public var isAvailable: Bool {
        true
    }

    public func speak(_ utterance: SpeechUtterance) async {
        // A new utterance interrupts any in-flight one. Without this, a second
        // concurrent speak() would overwrite play()'s playbackContinuation and the
        // first speak() would suspend forever — and AppEnvironment.speak() does not
        // serialise calls. stop() resumes the prior playback and fires its single
        // onSpeakingEnded before we start fresh.
        // TODO(#52 review residual): if two speak() calls race such that BOTH
        // observe isSpeaking() == false before either starts rendering, the
        // loser's SynthBox is never the synthesizer's current delegate — its
        // didCancel/didFinish land on the winner's box, are (correctly)
        // ignored by the utterance === owner guard, and the loser's
        // continuation never resolves. Pre-existing hazard, now the last leak
        // standing after the #52 fix; the clean fix is serialising speak()
        // entry (an actor-held gate), not weakening the identity guard.
        if await isSpeaking() { await stop() }
        do {
            let (samples, sampleRate, wordOnsets) = try await synthesizeToFloats(utterance)
            guard !samples.isEmpty, sampleRate > 0 else {
                await plainSpeak(utterance)
                return
            }
            // Word onsets recorded during the offline render (the delegate fires
            // interleaved with buffer delivery — probe-verified; the marker-callback
            // API delivers nothing on system voices) give the Apple path an exact
            // timeline through the same streaming/clock machinery Kokoro uses.
            let timeline = AppleWordTiming.timeline(
                text: utterance.text,
                onsets: wordOnsets,
                totalSamples: samples.count,
                sampleRate: sampleRate
            )
            let chunk = TimedPCMChunk(samples: samples, timeline: timeline)
            let spoke = await speak(stream: .single(chunk), sampleRate: sampleRate)
            if !spoke { await plainSpeak(utterance) }
        } catch is CancellationError {
            // A barge-in/stop cancelled the offline render mid-flight (SynthBox's
            // didCancel). Cancellation is NOT a failure — end silently rather than
            // re-speaking the just-stopped utterance in the plain voice (the
            // fallback-never-silent doctrine is about genuine failures).
            return
        } catch {
            await plainSpeak(utterance)
        }
    }

    /// Play externally-synthesized mono PCM (e.g. Kokoro's neural audio) through the
    /// same effect chain + engine + lifecycle callbacks as `speak(_:)`. Lets a neural
    /// provider reuse this hardened playback path instead of re-implementing AVAudioEngine.
    ///
    /// `play()` owns ALL lifecycle signalling — it fires `onSpeakingStarted` before
    /// scheduling and `onSpeakingEnded` as it unwinds, and `isSpeaking()` is computed
    /// from `player.isPlaying` — so the neural path drives the avatar mouth + speaking
    /// state identically to `speak(_:)`. No callback is bypassed.
    public func speak(rawPCM samples: [Float], sampleRate: Double) async {
        if await isSpeaking() { await stop() }
        guard !samples.isEmpty, sampleRate > 0 else { return }
        do {
            let processed = chain.process(samples, sampleRate: sampleRate)
            try await play(processed, sampleRate: sampleRate)
        } catch {
            // Playback (engine/buffer) failed — there's no text to re-speak here, so
            // this one utterance is dropped. The caller chooses neural-vs-Apple upstream.
        }
    }

    public func stop() async {
        let wasActive = await isSpeaking()
        let hadPlayback = await MainActor.run { () -> Bool in
            player.stop()
            _ = synthesizer.stopSpeaking(at: .immediate)
            // Resume any playback wait — .dataPlayedBack won't fire after stop().
            let had = playbackContinuation != nil || streamingSession != nil
            streamingSession?.cancel()
            streamingSession = nil
            finishPlayback()
            // Clear the cached flag NOW so isSpeaking() reads false the instant
            // stop() returns — the interrupted task's fireSpeakingEnded (which also
            // clears it, idempotently) may not unwind until a later actor hop.
            speakingState.end()
            return had
        }
        await plainFallback.stop()
        // When we cancelled active effectful playback, finishPlayback() (or the
        // session cancel) resumes the wait inside play()/speak(stream:), which fires
        // onSpeakingEnded as it unwinds — so fire here only when there was no
        // playback to resume (cancelled mid-synthesis), otherwise the avatar gets a
        // spurious second "ended" event.
        if wasActive, !hadPlayback { await MainActor.run { fireSpeakingEnded() } }
    }

    public func isSpeaking() async -> Bool {
        // Reads the cached flag, NOT the engine: player.isPlaying / synthesizer
        // .isSpeaking take an internal AVAudioEngine lock the render thread holds,
        // so polling them from a high-QoS caller inverts priority (the TPC hang
        // risk). A live streaming session still counts even before its first buffer
        // is scheduled (chunk 1 still synthesizing) — that term is a cheap nil check.
        await MainActor.run { speakingState.isSpeaking(streamingActive: streamingSession != nil) }
    }

    // Internal (not private) so EffectfulSpeechProvider+Streaming.swift can fire
    // them too — same reason `player`/`streamingSession` are internal.

    /// Flip the cached flag ON and fire onSpeakingStarted as one step, so the flag
    /// can never drift from the callback. Called at every playback-start seam.
    @MainActor
    func fireSpeakingStarted() {
        speakingState.begin()
        onSpeakingStarted?()
    }

    /// Flip the cached flag OFF and fire onSpeakingEnded as one step. Called at
    /// every playback-end seam (and, defensively, from stop()).
    @MainActor
    func fireSpeakingEnded() {
        speakingState.end()
        onSpeakingEnded?()
    }

    // MARK: - Synthesis → PCM

    // @MainActor: AVSpeechSynthesizer.write must be invoked on the main thread
    // (same contract as .speak), and building `spoken` here keeps it main-isolated.
    /// Mono PCM samples + their sample rate + word onsets (text range, sample offset)
    /// recorded from the willSpeakRange delegate during the offline render.
    private typealias PCM = ([Float], Double, [WordOnset])

    @MainActor
    private func synthesizeToFloats(_ utterance: SpeechUtterance) async throws -> PCM {
        let spoken = AVSpeechUtterance(string: utterance.text)
        spoken.rate = utterance.rate
        spoken.pitchMultiplier = utterance.pitch
        if let id = utterance.voiceIdentifier,
           let voice = AVSpeechSynthesisVoice(identifier: id)
        {
            spoken.voice = voice
        }

        let box = SynthBox(owner: spoken)
        synthesizer.delegate = box // weak; the write callbacks keep `box` alive
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PCM, Error>) in
            box.onDone = { continuation.resume(with: $0) }
            synthesizer.write(spoken) { buffer in
                box.ingest(buffer)
            }
        }
    }

    // MARK: - Playback

    @MainActor
    private func play(_ samples: [Float], sampleRate: Double) async throws {
        guard let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: sampleRate,
            channels: 1,
            interleaved: false
        ),
            let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count))
        else {
            throw EffectfulSpeechError.bufferAllocationFailed
        }

        buffer.frameLength = AVAudioFrameCount(samples.count)
        if let channel = buffer.floatChannelData {
            samples.withUnsafeBufferPointer { source in
                if let base = source.baseAddress {
                    channel[0].update(from: base, count: samples.count)
                }
            }
        }

        try configureEngineIfNeeded(format: format)

        fireSpeakingStarted()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            playbackContinuation = continuation
            player.scheduleBuffer(
                buffer, at: nil, options: [], completionCallbackType: .dataPlayedBack
            ) { [weak self] _ in
                Task { @MainActor in self?.finishPlayback() }
            }
            player.play()
        }
        fireSpeakingEnded()
    }

    /// Resume the playback wait exactly once — from either the .dataPlayedBack
    /// completion (normal end) or stop() (cancelled). Halts the node either way:
    /// a drained AVAudioPlayerNode keeps "playing" forever otherwise, leaving
    /// isSpeaking() stuck true after the utterance.
    @MainActor
    private func finishPlayback() {
        player.stop()
        playbackContinuation?.resume()
        playbackContinuation = nil
    }

    @MainActor
    func configureEngineIfNeeded(format: AVAudioFormat) throws {
        // Reconnect when the sample rate changes (e.g. a different system voice) —
        // scheduling a buffer whose format mismatches the connection asserts.
        // Also require the engine to actually be RUNNING: it stops itself on an
        // output-route change (see handleEngineConfigurationChange) or a render
        // error, and scheduling onto a stopped engine plays silence forever.
        if engineConfigured, configuredSampleRate == format.sampleRate, engine.isRunning { return }
        if engineConfigured {
            player.stop()
            engine.disconnectNodeOutput(player)
        } else {
            engine.attach(player)
        }
        engine.connect(player, to: engine.mainMixerNode, format: format)
        if !engine.isRunning { try engine.start() }
        engineConfigured = true
        configuredSampleRate = format.sampleRate
    }

    // MARK: - Fallback

    private func plainSpeak(_ utterance: SpeechUtterance) async {
        await MainActor.run { fireSpeakingStarted() }
        await plainFallback.speak(utterance)
        await MainActor.run { fireSpeakingEnded() }
    }
}

enum EffectfulSpeechError: Error {
    case bufferAllocationFailed
}

/// A word onset captured during the offline render: where the word sits in the
/// utterance text (UTF-16, from the delegate's NSRange) and how many samples had
/// been rendered when the synthesizer announced it — i.e. the word's start offset.
struct WordOnset: Equatable {
    let textRange: Range<Int>
    let sampleOffset: Int
}

/// Accumulates the synthesizer's PCM chunks (float / int16 / int32) into one mono
/// Float buffer and resumes exactly once when the final (empty) buffer arrives.
/// Also records `willSpeakRangeOfSpeechString` onsets against the samples rendered
/// so far — the delegate interleaves with buffer delivery during `write`
/// (probe-verified), which is how the Apple path gets word timing (the
/// marker-callback API delivers nothing on system voices).
///
/// `@unchecked Sendable` safety: `onDone` is written once on the main actor (inside
/// `synthesizeToFloats`) *before* `synthesizer.write` can deliver any callback, so
/// that write happens-before every read in `finish()`. `samples`/`sampleRate`/`done`/
/// `wordOnsets` are all `lock`-guarded, and the `done` flag guarantees `finish()`
/// resumes the continuation at most once even though `write` callbacks arrive on a
/// background thread.
/// `internal` (not `private`) so M1K3VoiceTests can pin the exactly-once
/// resume contract directly — the delegate cancel/finish backstops below are
/// verify-by-launch to WIRE (a real cancelled AVSpeechSynthesizer render), but
/// the exactly-once guarantee they rely on is pure and unit-tested.
final class SynthBox: NSObject, AVSpeechSynthesizerDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var samples: [Float] = []
    private var sampleRate: Double = 0
    private var done = false
    private var wordOnsets: [WordOnset] = []
    var onDone: ((Result<([Float], Double, [WordOnset]), Error>) -> Void)?

    /// The utterance THIS box is rendering. `AVSpeechSynthesizer.delegate` is a
    /// single shared property, and each `synthesizeToFloats` call reassigns it to
    /// a fresh box — so on a barge-in (stop utterance A, start utterance B) a
    /// straggling didFinish/didCancel for A can be delivered AFTER the delegate
    /// already points at B's box. Every delegate callback carries the utterance
    /// it's about, so we guard `utterance === owner` and ignore foreign events —
    /// otherwise A's stale cancel would resume B's continuation with a spurious
    /// CancellationError and silence B entirely. (`ingest` needs no such guard:
    /// it's driven by `write`'s per-call completion closure, which captures its
    /// own box directly rather than routing through the shared delegate.)
    private let owner: AVSpeechUtterance

    init(owner: AVSpeechUtterance) {
        self.owner = owner
        super.init()
    }

    func speechSynthesizer(
        _: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString characterRange: NSRange,
        utterance: AVSpeechUtterance
    ) {
        guard utterance === owner else { return }
        lock.withLock {
            let range = characterRange.location ..< characterRange.location + characterRange.length
            wordOnsets.append(WordOnset(textRange: range, sampleOffset: samples.count))
        }
    }

    /// Success backstop. `write`'s zero-frame sentinel (`ingest`) normally
    /// resumes first; if it never arrives (some voices don't emit it), this
    /// delegate callback still completes the continuation. Idempotent via the
    /// `done` flag, so the common case where the sentinel won already ran is a
    /// harmless no-op. Guarded on `owner` so a stale finish for a superseded
    /// utterance can't complete THIS box's render.
    func speechSynthesizer(_: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard utterance === owner else { return }
        finish()
    }

    /// Cancel path — the fix for the leaked continuation. `stop()` calls
    /// `synthesizer.stopSpeaking(at:.immediate)` mid-render; the zero-frame
    /// sentinel then never arrives, so without this the continuation (and every
    /// caller awaiting `speak`) would suspend forever. Resume exactly once with
    /// `CancellationError` — NOT the partial samples (would play audio the user
    /// just stopped) and NOT a generic error (would trigger the plain-voice
    /// re-speak fallback). `speak(_:)` swallows the CancellationError silently.
    /// Guarded on `owner` so a barge-in's cancel for the PREVIOUS utterance can't
    /// silence the successor (see the `owner` doc).
    func speechSynthesizer(_: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        guard utterance === owner else { return }
        cancel()
    }

    func ingest(_ buffer: AVAudioBuffer) {
        // Already resumed (a didFinish/didCancel raced ahead of the real terminal
        // buffer): drop trailing audio rather than grow a payload nobody reads.
        if lock.withLock({ done }) { return }
        guard let pcm = buffer as? AVAudioPCMBuffer else { return }
        let frames = Int(pcm.frameLength)
        if frames == 0 {
            finish()
            return
        }
        var chunk = [Float](repeating: 0, count: frames)
        if let floatData = pcm.floatChannelData {
            for index in 0 ..< frames {
                chunk[index] = floatData[0][index]
            }
        } else if let int16Data = pcm.int16ChannelData {
            for index in 0 ..< frames {
                chunk[index] = Float(int16Data[0][index]) / 32768
            }
        } else if let int32Data = pcm.int32ChannelData {
            for index in 0 ..< frames {
                chunk[index] = Float(int32Data[0][index]) / Float(Int32.max)
            }
        }
        lock.withLock {
            sampleRate = pcm.format.sampleRate
            samples.append(contentsOf: chunk)
        }
    }

    private func finish() {
        let payload: ([Float], Double, [WordOnset])? = lock.withLock {
            guard !done else { return nil }
            done = true
            return (samples, sampleRate, wordOnsets)
        }
        guard let payload else { return }
        onDone?(.success(payload))
    }

    /// Resume exactly once with a cancellation, sharing `finish`'s `done` guard
    /// so cancel-then-late-sentinel (or a double cancel) is a no-op.
    private func cancel() {
        let shouldResume = lock.withLock {
            guard !done else { return false }
            done = true
            return true
        }
        guard shouldResume else { return }
        onDone?(.failure(CancellationError()))
    }
}
