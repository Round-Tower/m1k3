//
//  AppEnvironment.swift
//  M1K3App
//
//  The composition root: wires the pure package brain into one object the SwiftUI
//  views observe. Everything here is a thin assembly of types that are already
//  tested in the package — no business logic lives in the app target.
//
//  Sandbox note: the KnowledgeStore is opened under Application Support, which the
//  App Sandbox redirects into the app container. This is the path the unit tests
//  (in-memory) can't exercise, so it's pinned explicitly here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
//
//  Review: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8 — selecting MLX
//  Gemma now preloads the weights (`preloadGemma`) and folds download progress
//  into the observable `modelLoad: ModelLoadState`, mirroring the existing
//  embeddings-switch pattern; the embedder switch routes its own download % into
//  `embeddingStatus`. Fixed the "Gemma 4" runtime labels (Gemma 3 is current).

import Foundation
import M1K3Avatar
import M1K3Calls
import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import M1K3MLX
import M1K3Voice
import M1K3WhisperKit
import Observation

/// The inference backends the runtime picker offers. Only Apple Foundation
/// Models is wired for the MVP; MLX / LiteRT Gemma are reserved slots that light
/// up in the heavy-dependency session.
enum RuntimeOption: String, CaseIterable, Identifiable {
    case appleFoundationModels = "Apple Foundation Models"
    case mlxGemma = "MLX Gemma 3"
    case liteRTGemma = "LiteRT Gemma 3"

    var id: String {
        rawValue
    }

    /// Wired and selectable today.
    var isReady: Bool {
        self == .appleFoundationModels || self == .mlxGemma
    }

    var subtitle: String {
        switch self {
        case .appleFoundationModels: "On-device, cheap & fast. The MVP brain."
        case .mlxGemma: "Metal in-process Gemma 3 (1B, 4-bit). Downloads on first use."
        case .liteRTGemma: "LiteRT-LM Gemma. Spike — not yet wired."
        }
    }

    var systemImage: String {
        switch self {
        case .appleFoundationModels: "apple.logo"
        case .mlxGemma: "cpu"
        case .liteRTGemma: "flask"
        }
    }
}

@MainActor
@Observable
final class AppEnvironment {
    let store: KnowledgeStore
    let provider: any InferenceProvider
    let responder: RAGResponder
    let speech: AVSpeechProvider
    let chat: ChatSession

    private let embedder: SwappableEmbeddingService
    private let ingester: DocumentIngester
    private let runtimeSelection: RuntimeSelectionBox
    /// Call intelligence: encrypted-at-rest persistence + indexing into the SAME
    /// knowledge graph as documents (so calls are RAG-searchable) + two-stage
    /// summary. Composed from the M1K3Calls seams.
    private let callPersistence: any CallPersistence
    private let callIngester: CallIngester
    private let callSummarizer: SummarizationPipeline
    /// Recording is consent-gated (legal: call recording needs consent) and captured
    /// behind the AudioRecorder seam.
    private let consentGate = RecordingConsentGate(store: UserDefaultsConsentStore())
    /// Stereo capture: near-end mic + far-end system audio (ScreenCaptureKit),
    /// muxed so the diarizer can separate speakers. Degrades to mono mic if
    /// screen-recording permission is absent.
    private let recorder = StereoCallRecorder()
    /// The MLX provider for the currently-selected brain (Lil = Qwen / Big =
    /// Gemma). Rebuilt when the brain changes; held directly so the onboarding /
    /// picker can warm it and stream download progress to the UI.
    private var currentMLXProvider: MLXGemmaProvider
    /// The single MLX slot behind `RuntimeOption.mlxGemma` in the façade; re-pointed
    /// at `currentMLXProvider` whenever the brain switches between Lil and Big, so
    /// the swap is seen without rebuilding the RAGResponder.
    private let swappableMLX: SwappableInferenceProvider
    /// Voice input: WhisperKit (high accuracy, needs a model) routed ahead of
    /// Apple Speech (system framework, always available). Held directly so the
    /// app can load WhisperKit's model on demand.
    private let transcription: TranscriptionRouter
    private let whisperKit: WhisperKitProvider
    private var dictationProvider: (any TranscriptionProvider)?
    private var dictationTask: Task<Void, Never>?
    /// Batch (file → segments) transcription for recorded calls. Closes the mic
    /// path: a stopped recording runs through the SAME CallIntelligencePipeline the
    /// import path proves. Opt-in (needs a model) like live WhisperKit.
    private let batchTranscriber: WhisperKitBatchTranscriber

    /// True while the mic is live. `liveTranscript` is the best-so-far text shown
    /// in the input bar as the user speaks.
    private(set) var isListening = false
    private(set) var liveTranscript = ""
    /// WhisperKit model-load state — mirrors `modelLoad` for the brain tier.
    private(set) var whisperLoad: ModelLoadState = .idle
    private var isPreparingWhisper = false

    /// The avatar companion state — driven by this environment at each transition
    /// (listening → thinking → generating → speaking → idle).
    private(set) var avatar = AvatarController()

    private static let embedderPrefersMLXKey = "embedder.prefersMLX"
    static let selectedBrainKey = "selectedBrain"
    /// Whether the user has completed brain selection — gates the onboarding flow.
    static let hasChosenBrainKey = "hasChosenBrain"

    /// The chosen brain (Mini / Lil / Big). Restored on launch, persisted on change.
    private(set) var selectedBrain: BrainTier = .mini

    /// The downloading brain's name for progress labels (e.g. "Big M1K3").
    var downloadingBrainName: String {
        selectedBrain.displayName
    }

    /// Whether retrieval is on semantic MLX embeddings (vs the Hashing fallback).
    private(set) var usingMLXEmbeddings = false
    /// True while a full re-embed of the store is in flight.
    private(set) var isReindexing = false
    /// Last embeddings-switch outcome, surfaced to Settings.
    private(set) var embeddingStatus: String?

    /// Runtime picker selection. Changing it re-points the inference façade at
    /// the chosen backend for the next turn — no rebuild, transcript preserved.
    var selectedRuntime: RuntimeOption = .appleFoundationModels {
        didSet {
            runtimeSelection.value = selectedRuntime
            // Warm MLX on selection so the ~1GB first-use download shows a
            // progress bar in Settings rather than a silent hang on the first
            // chat turn. Cheap/no-op once the weights are cached. Switching away
            // cancels the warm-up — the SingleFlightLoader lets any in-flight
            // download finish + cache; we just stop tracking it and clear the bar.
            preloadTask?.cancel()
            if selectedRuntime == .mlxGemma {
                preloadTask = Task { await preloadGemma() }
            } else {
                preloadTask = nil
                if modelLoad.isActive { modelLoad = .idle }
            }
        }
    }

    /// The in-flight MLX warm-up, so switching away can cancel it (idempotent).
    private var preloadTask: Task<Void, Never>?

    /// Progress of warming the MLX Gemma weights, surfaced in Settings (and the
    /// chat send path). Stays `.idle` for the Apple Foundation Models default.
    private(set) var modelLoad: ModelLoadState = .idle

    /// Last ingest outcome, surfaced to the UI.
    private(set) var lastIngestStatus: String?
    private(set) var isIngesting = false
    /// Count of indexed knowledge items, for the document drawer / settings.
    private(set) var indexedItemCount = 0

    /// Call import outcome + count, surfaced to the Calls UI.
    private(set) var lastCallStatus: String?
    private(set) var isImportingCall = false
    private(set) var callCount = 0

    /// True while the mic is capturing a call (drives the recording indicator).
    private(set) var isRecording = false
    /// The most recent recording. Held so it can be (re)processed once the batch
    /// transcription model is ready.
    private(set) var lastRecordingURL: URL?
    /// True while a recorded call is being transcribed → summarised → indexed.
    private(set) var isTranscribingCall = false
    /// True while the batch transcription model is downloading/loading.
    private(set) var isPreparingBatchTranscription = false
    /// Whether recorded calls can be transcribed now (model loaded).
    var batchTranscriptionReady: Bool {
        batchTranscriber.isAvailable
    }

    init() throws {
        let url = try Self.storeURL()
        store = try KnowledgeStore(path: url.path)

        // Embeddings define the stored vector space, so the choice must persist
        // across launches (Hashing query vectors against MLX-stored vectors would
        // not match). Honour the saved preference; switching at runtime re-embeds
        // the whole store (see switchEmbeddings).
        let preferMLX = UserDefaults.standard.bool(forKey: Self.embedderPrefersMLXKey)
        let baseEmbedder: any EmbeddingService = preferMLX
            ? MLXEmbeddingService()
            : HashingEmbeddingService()
        let swappable = SwappableEmbeddingService(baseEmbedder)
        embedder = swappable
        usingMLXEmbeddings = preferMLX

        // Restore the chosen brain (default Mini = Apple Foundation Models).
        let brain = UserDefaults.standard.string(forKey: Self.selectedBrainKey)
            .flatMap(BrainTier.init(rawValue:)) ?? .mini
        selectedBrain = brain

        // Generation IS swappable. The façade forwards to whichever backend the
        // brain selects; AFM is Mini + the fallback, the MLX slot is Lil/Big. The
        // slot starts on the chosen brain's model (or Big's, when Mini is active and
        // MLX isn't needed yet) so switching Lil↔Big just re-points the slot.
        let runtimeForBrain: RuntimeOption =
            brain.backing == .appleFoundationModels ? .appleFoundationModels : .mlxGemma
        let selection = RuntimeSelectionBox(runtimeForBrain)
        runtimeSelection = selection
        let afm = AppleFoundationModelsProvider()
        let initialMLXModelID = brain.mlxModelID ?? BrainTier.big.mlxModelID ?? ""
        let gemma = MLXGemmaProvider(modelID: initialMLXModelID)
        currentMLXProvider = gemma
        let mlxSlot = SwappableInferenceProvider(gemma)
        swappableMLX = mlxSlot
        let runtimeProvider = RuntimeInferenceProvider(
            selection: selection,
            backends: [
                .appleFoundationModels: afm,
                .mlxGemma: mlxSlot,
            ],
            fallback: afm
        )
        provider = runtimeProvider
        responder = RAGResponder(store: store, embedder: embedder, provider: runtimeProvider)
        speech = AVSpeechProvider()

        // Voice input: WhisperKit first (better accuracy, but unavailable until
        // its model loads), Apple Speech as the always-on fallback. So dictation
        // works on Apple Speech day one and upgrades to WhisperKit on demand.
        let whisperDownloadBase = url.deletingLastPathComponent()
        let whisper = WhisperKitProvider(downloadBase: whisperDownloadBase)
        whisperKit = whisper
        transcription = TranscriptionRouter(providers: [whisper, AppleSpeechTranscriber()])
        batchTranscriber = WhisperKitBatchTranscriber(downloadBase: whisperDownloadBase)

        ingester = DocumentIngester(store: store, embedder: embedder)
        let transcriptURL = url.deletingLastPathComponent().appendingPathComponent("transcript.json")
        chat = ChatSession(responder: responder, transcript: ChatTranscriptStore(url: transcriptURL))

        // Calls: encrypted-at-rest store (key from the Keychain), indexed into the
        // same knowledge graph, summarised by AFM (quick) + the active runtime (deep).
        let callsURL = url.deletingLastPathComponent().appendingPathComponent("calls.sqlite")
        callPersistence = Self.makeCallPersistence(at: callsURL)
        callIngester = CallIngester(store: store, embedder: embedder)
        callSummarizer = SummarizationPipeline(quickProvider: afm, deepProvider: runtimeProvider)

        refreshCounts()

        // Wire avatar ↔ speech after all stored properties are initialized.
        speech.onSpeakingStarted = { [weak self] in
            Task { @MainActor [weak self] in self?.avatar.setActivity(.speaking) }
        }
        speech.onSpeakingEnded = { [weak self] in
            Task { @MainActor [weak self] in self?.avatar.resetToIdle() }
        }

        // Warm a restored MLX brain (Lil/Big) on launch so it's ready to answer;
        // Mini (Apple) needs nothing. Setting selectedRuntime drives the existing
        // preload path + the progress UI. No-op/fast once the weights are cached.
        if brain.mlxModelID != nil {
            selectedRuntime = .mlxGemma
        }
    }

    /// Build the encrypted call store. Falls back to an in-memory (non-persistent)
    /// store if the Keychain key can't be obtained, so a key hiccup degrades the
    /// calls feature rather than crashing the app.
    private static func makeCallPersistence(at url: URL) -> any CallPersistence {
        do {
            let key = try StoredKeyProvider(store: KeychainKeyStore()).symmetricKey()
            return try GRDBCallPersistence(path: url.path, coder: EncryptedCallCoder(key: key))
        } catch {
            return (try? GRDBCallPersistence()) ?? NullCallPersistence()
        }
    }

    /// Whether the on-device model is actually available on this machine.
    var providerAvailable: Bool {
        provider.isAvailable
    }

    /// Ingest a user-selected / dropped file (PDF or text) into the store.
    func ingest(url: URL) async {
        isIngesting = true
        defer { isIngesting = false }

        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        let title = url.deletingPathExtension().lastPathComponent
        do {
            let result: DocumentIngester.IngestResult
            if url.pathExtension.lowercased() == "pdf" {
                let data = try Data(contentsOf: url)
                result = try await ingester.ingestPDF(title: title, data: data, sourceRef: url.absoluteString)
            } else {
                let text = try String(contentsOf: url, encoding: .utf8)
                result = try await ingester.ingest(title: title, text: text, sourceRef: url.absoluteString)
            }
            let dedup = result.wasDeduped ? " (already indexed)" : ""
            lastIngestStatus = "Indexed “\(title)” — \(result.chunkCount) chunks\(dedup)."
            refreshCounts()
        } catch {
            lastIngestStatus = "Couldn’t index “\(title)”: \(error.localizedDescription)"
        }
    }

    /// Send a user message: drives avatar thinking → generating → idle, then
    /// hands off to ChatSession. The speech delegate handles the speaking→idle
    /// transition if the user taps Speak on the response.
    func send(_ text: String) async {
        avatar.setActivity(.thinking)
        await chat.send(text)
        // Only reset to idle if the avatar isn't already in a speaking state
        // (e.g. auto-TTS path sets .speaking before we return here).
        if case .speaking = avatar.state.activity { return }
        avatar.resetToIdle()
    }

    /// Speak text via the TTS provider. The onSpeakingStarted/Ended delegate
    /// callbacks drive avatar .speaking → .idle; no manual state change needed here.
    func speak(_ text: String) async {
        await speech.speak(text)
    }

    func stopSpeaking() async {
        await speech.stop()
        avatar.resetToIdle()
    }

    // MARK: - Voice input

    /// Whether any recogniser can serve right now (WhisperKit-if-loaded, else
    /// Apple Speech). Drives the mic button's enabled state.
    var canDictate: Bool {
        transcription.activeProvider != nil
    }

    /// The engine voice input would currently use, for the Settings label.
    var activeTranscriberName: String {
        transcription.activeProviderName ?? "Unavailable"
    }

    /// Download + load WhisperKit's model so voice input upgrades from Apple
    /// Speech to WhisperKit (the router prefers it once available).
    func enableWhisperKit() async {
        guard !isPreparingWhisper else { return }
        isPreparingWhisper = true
        whisperLoad = .progress(0)
        do {
            try await whisperKit.prepareModel { fraction in
                // Guard against a late progress hop overwriting .ready — same race
                // fixed in preloadGemma (the .downloading check is the fence).
                Task { @MainActor in
                    if case .downloading = self.whisperLoad {
                        self.whisperLoad = .progress(fraction)
                    }
                }
            }
            isPreparingWhisper = false
            whisperLoad = .ready
        } catch {
            isPreparingWhisper = false
            whisperLoad = .failed(message: error.localizedDescription)
        }
    }

    /// Choose a brain: persist it, re-point the active provider, and (for Lil/Big)
    /// warm the model so onboarding / Settings show a real download bar. Mini is
    /// instant — Apple Foundation Models, no download.
    func selectBrain(_ tier: BrainTier) {
        selectedBrain = tier
        UserDefaults.standard.set(tier.rawValue, forKey: Self.selectedBrainKey)
        UserDefaults.standard.set(true, forKey: Self.hasChosenBrainKey)
        if let modelID = tier.mlxModelID {
            let mlx = MLXGemmaProvider(modelID: modelID)
            currentMLXProvider = mlx
            swappableMLX.setProvider(mlx)
            selectedRuntime = .mlxGemma // didSet warms it + streams progress
        } else {
            selectedRuntime = .appleFoundationModels // didSet clears the bar
        }
    }

    /// Warm the current brain's MLX weights, folding download progress into
    /// `modelLoad` so the UI shows a real bar. Idempotent: returns fast once the
    /// model is cached. Runs off the main actor; progress hops back to update state.
    private func preloadGemma() async {
        // No re-entrancy guard needed: switching brains cancels any prior warm-up
        // first, and the SingleFlightLoader dedupes the underlying download even
        // if two briefly overlap. On cancellation we write nothing — whoever is
        // the current selection (the didSet, or a newer warm-up) owns modelLoad,
        // so a late-completing cancelled task can't clobber it.
        modelLoad = .progress(0)
        let mlx = currentMLXProvider
        do {
            try await mlx.prepare { fraction in
                // Only apply while still downloading. A progress callback fired
                // near completion enqueues its main-actor hop *after* the `.ready`
                // write below; the guard makes that stale hop a no-op instead of
                // regressing the bar to 99% forever.
                Task { @MainActor in
                    if case .downloading = self.modelLoad { self.modelLoad = .progress(fraction) }
                }
            }
            if Task.isCancelled { return }
            modelLoad = .ready
        } catch is CancellationError {
            // Deliberately switched away mid-load — leave modelLoad to the current
            // selection (the didSet already cleared the bar).
        } catch {
            if !Task.isCancelled { modelLoad = .failed(message: error.localizedDescription) }
        }
    }

    /// Switch the active embedder and re-embed the whole store so retrieval moves
    /// to (or back from) semantic MLX vectors. The choice persists; on failure the
    /// façade rolls back so query + stored vectors stay in the same space.
    func switchEmbeddings(toMLX useMLX: Bool) async {
        guard useMLX != usingMLXEmbeddings, !isReindexing else { return }
        isReindexing = true
        embeddingStatus = useMLX
            ? "Loading MLX embedder and rebuilding the index…"
            : "Rebuilding the index with the Hashing embedder…"
        defer { isReindexing = false }

        let newEmbedder: any EmbeddingService = useMLX
            ? MLXEmbeddingService(onLoadProgress: { fraction in
                Task { @MainActor in
                    self.embeddingStatus = "Downloading embedder… \(Int((fraction * 100).rounded()))%"
                }
            })
            : HashingEmbeddingService()
        embedder.setEmbedder(newEmbedder)
        do {
            let count = try await store.reindexEmbeddings(using: newEmbedder)
            usingMLXEmbeddings = useMLX
            UserDefaults.standard.set(useMLX, forKey: Self.embedderPrefersMLXKey)
            let label = useMLX ? "MLX bge_small" : "Hashing"
            embeddingStatus = "Reindexed \(count) chunk\(count == 1 ? "" : "s") with \(label)."
        } catch {
            // Reindex writes atomically, so the store still matches the previous
            // embedder — roll the façade back to it.
            embedder.setEmbedder(usingMLXEmbeddings ? MLXEmbeddingService() : HashingEmbeddingService())
            embeddingStatus = "Couldn’t switch embeddings: \(error.localizedDescription)"
        }
    }

    /// All indexed items, newest first, for the document manager.
    func documents() -> [KnowledgeItem] {
        ((try? store.allItems()) ?? []).sorted { $0.createdAt > $1.createdAt }
    }

    /// Chunk count for one item (shown in the document list).
    func chunkCount(for id: UUID) -> Int {
        (try? store.chunks(forItem: id).count) ?? 0
    }

    /// Delete an item and everything it owns; refresh the counts.
    @discardableResult
    func deleteDocument(id: UUID) -> Bool {
        let deleted = (try? store.deleteItem(id: id)) ?? false
        if deleted { refreshCounts() }
        return deleted
    }

    // MARK: - Calls

    /// Import a plain-text transcript as a call: parse → summarise → persist
    /// (encrypted) → index into the knowledge graph (so it's RAG-searchable).
    /// The headless, no-mic path to a real call while recording is still to come.
    func importCallTranscript(url: URL) async {
        isImportingCall = true
        defer { isImportingCall = false }

        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        let title = url.deletingPathExtension().lastPathComponent
        do {
            let text = try String(contentsOf: url, encoding: .utf8)
            let segments = TranscriptImporter.parse(text)
            guard !segments.isEmpty else {
                lastCallStatus = "“\(title)” had no readable lines."
                return
            }
            let transcript = segments
                .map { ($0.speaker.map { "\($0): " } ?? "") + $0.text }
                .joined(separator: "\n")
            let summary = await callSummarizer.summarize(transcript: transcript)
            let session = CallSession(
                startedAt: Date(),
                title: title,
                segments: segments,
                quickSummary: summary.quick,
                fullSummary: summary.full
            )
            try callPersistence.save(session)
            try await callIngester.ingest(session)
            refreshCounts()
            lastCallStatus = "Imported “\(title)” — \(segments.count) lines, indexed + searchable."
        } catch {
            lastCallStatus = "Couldn’t import “\(title)”: \(error.localizedDescription)"
        }
    }

    /// All stored calls, newest first.
    func calls() -> [CallSession] {
        (try? callPersistence.loadAll()) ?? []
    }

    /// Delete a call from both the encrypted store and the knowledge graph.
    @discardableResult
    func deleteCall(id: UUID) -> Bool {
        let removed = (try? callPersistence.delete(id: id)) ?? false
        _ = try? store.deleteItem(id: id) // the call's graph node shares the call UUID
        if removed { refreshCounts() }
        return removed
    }

    // MARK: - Recording (consent-gated)

    /// Whether recording can start without re-asking for consent.
    var recordingPreAuthorised: Bool {
        consentGate.isPreAuthorised
    }

    /// Log a consent affirmation, then start recording.
    func affirmConsentAndRecord(scope: ConsentScope) async {
        consentGate.affirm(scope: scope, at: Date())
        await startRecording()
    }

    /// Start capturing — only call once consent is in hand (pre-authorised or just
    /// affirmed). Captures the far-end (system audio) too when screen-recording
    /// permission is granted; falls back to mono mic otherwise. Surfaces failures
    /// rather than crashing.
    func startRecording() async {
        guard !isRecording else { return }
        do {
            let stereo = try await recorder.start()
            isRecording = true
            lastCallStatus = stereo
                ? "Recording… (both sides — speakers will be separated)"
                : "Recording… (mic only — grant Screen Recording to capture the other side)"
        } catch {
            lastCallStatus = "Couldn’t start recording: \(error.localizedDescription)"
        }
    }

    /// Stop capturing. If the batch transcription model is ready, the recording is
    /// transcribed → summarised → encrypted → indexed straight away (the SAME
    /// CallIntelligencePipeline the import path proves). If not, it's held and the
    /// user is pointed at the Settings enable; `enableCallTranscription` picks it up.
    func stopRecording() async {
        guard isRecording else { return }
        let url = await recorder.stop()
        isRecording = false
        lastRecordingURL = url
        guard let url else {
            lastCallStatus = "Nothing was recorded."
            return
        }
        if batchTranscriber.isAvailable {
            Task { await processRecording(url: url) }
        } else {
            lastCallStatus = "Recorded — enable call transcription in Settings to process it."
        }
    }

    private func refreshCounts() {
        indexedItemCount = (try? store.itemCount()) ?? indexedItemCount
        callCount = (try? callPersistence.loadAll().count) ?? callCount
    }

    private static func storeURL() throws -> URL {
        let fm = FileManager.default
        let base = try fm.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dir = base.appendingPathComponent("M1K3", isDirectory: true)
        try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("knowledge.sqlite")
    }
}

// MARK: - Recorded-call transcription

extension AppEnvironment {
    /// Download + load the batch transcription model, then process any recording
    /// that's waiting. Opt-in (heavy first download), mirroring `enableWhisperKit`.
    func enableCallTranscription() async {
        guard !isPreparingBatchTranscription, !batchTranscriber.isAvailable else { return }
        isPreparingBatchTranscription = true
        lastCallStatus = "Downloading call-transcription model…"
        do {
            try await batchTranscriber.prepareModel { fraction in
                // Only apply while still preparing — a late hop near 100% must not
                // clobber the terminal status (same race guarded in preloadGemma).
                Task { @MainActor in
                    if self.isPreparingBatchTranscription {
                        self.lastCallStatus = "Downloading call-transcription model… \(Int((fraction * 100).rounded()))%"
                    }
                }
            }
            isPreparingBatchTranscription = false
            if let url = lastRecordingURL {
                await processRecording(url: url)
            } else {
                lastCallStatus = "Call transcription ready."
            }
        } catch {
            isPreparingBatchTranscription = false
            lastCallStatus = "Couldn’t load call transcription: \(error.localizedDescription)"
        }
    }

    /// Transcribe a recorded file through the call pipeline, then persist (encrypted)
    /// + index into the knowledge graph — exactly the back half of `importCallTranscript`,
    /// so a recorded call and an imported one land identically.
    func processRecording(url: URL) async {
        guard !isTranscribingCall else { return }
        isTranscribingCall = true
        defer { isTranscribingCall = false }

        lastCallStatus = "Transcribing…"
        let title = "Call \(Self.callTitleFormatter.string(from: Date()))"
        // Stereo-channel diarization attributes speakers when each party is on its
        // own channel; it returns no turns for a mono file, so the pipeline simply
        // leaves the transcript unattributed (no error). FluidAudio (mono) drops in
        // behind the same seam later.
        let pipeline = CallIntelligencePipeline(
            transcriber: batchTranscriber,
            diarizer: StereoChannelDiarizer(),
            summarizer: callSummarizer
        )
        do {
            let session = try await pipeline.process(fileURL: url, title: title, startedAt: Date())
            guard !session.segments.isEmpty else {
                lastCallStatus = "Transcription produced no speech."
                return
            }
            try callPersistence.save(session)
            try await callIngester.ingest(session)
            lastRecordingURL = nil
            refreshCounts()
            lastCallStatus = "Transcribed “\(title)” — \(session.segments.count) segments, indexed."
        } catch {
            lastCallStatus = "Couldn’t transcribe the call: \(error.localizedDescription)"
        }
    }

    fileprivate static let callTitleFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()
}

// MARK: - Voice input (dictation)

//
// Extracted to a same-file extension (sees the class's private members) to keep
// the main class body under SwiftLint's type_body_length — the established fix in
// this file. Pure UI-driven orchestration over the tested TranscriptionRouter.

extension AppEnvironment {
    /// Tap-to-start / tap-to-stop. On stop (user tap OR the recogniser settling
    /// on a final result) the transcript is sent automatically.
    func toggleDictation() {
        if isListening { stopDictation() } else { startDictation() }
    }

    private func startDictation() {
        guard let provider = transcription.activeProvider else { return }
        dictationTask?.cancel() // belt-and-suspenders: never leave a prior consumer running
        dictationProvider = provider
        liveTranscript = ""
        isListening = true
        avatar.setActivity(.listening)
        do {
            let stream = try provider.startListening()
            dictationTask = Task { @MainActor in
                var accumulator = TranscriptAccumulator()
                for await segment in stream {
                    accumulator.ingest(segment)
                    liveTranscript = accumulator.text
                }
                await finishDictation(text: accumulator.text)
            }
        } catch {
            isListening = false
            dictationProvider = nil
            liveTranscript = ""
        }
    }

    /// Ask the active recogniser to stop; the stream then finishes and
    /// `finishDictation` auto-sends.
    private func stopDictation() {
        liveTranscript = "" // clear the ticker on the stop tap, not when the stream drains
        dictationProvider?.stopListening()
    }

    private func finishDictation(text: String) async {
        isListening = false
        liveTranscript = ""
        dictationProvider = nil
        dictationTask = nil
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            avatar.resetToIdle()
            return
        }
        await send(trimmed)
    }
}

/// Last-resort no-op store so a (near-impossible) persistence-init failure leaves
/// the app running with the calls feature simply inert, never crashing.
private struct NullCallPersistence: CallPersistence {
    func save(_: CallSession) throws {}
    func load(id _: UUID) throws -> CallSession? {
        nil
    }

    func loadAll() throws -> [CallSession] {
        []
    }

    func delete(id _: UUID) throws -> Bool {
        false
    }
}
