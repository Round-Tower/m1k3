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

import Foundation
import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import M1K3MLX
import M1K3Voice
import Observation

/// The inference backends the runtime picker offers. Only Apple Foundation
/// Models is wired for the MVP; MLX / LiteRT Gemma are reserved slots that light
/// up in the heavy-dependency session.
enum RuntimeOption: String, CaseIterable, Identifiable {
    case appleFoundationModels = "Apple Foundation Models"
    case mlxGemma = "MLX Gemma 4"
    case liteRTGemma = "LiteRT Gemma 4"

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

    private static let embedderPrefersMLXKey = "embedder.prefersMLX"

    /// Whether retrieval is on semantic MLX embeddings (vs the Hashing fallback).
    private(set) var usingMLXEmbeddings = false
    /// True while a full re-embed of the store is in flight.
    private(set) var isReindexing = false
    /// Last embeddings-switch outcome, surfaced to Settings.
    private(set) var embeddingStatus: String?

    /// Runtime picker selection. Changing it re-points the inference façade at
    /// the chosen backend for the next turn — no rebuild, transcript preserved.
    var selectedRuntime: RuntimeOption = .appleFoundationModels {
        didSet { runtimeSelection.value = selectedRuntime }
    }

    /// Last ingest outcome, surfaced to the UI.
    private(set) var lastIngestStatus: String?
    private(set) var isIngesting = false
    /// Count of indexed knowledge items, for the document drawer / settings.
    private(set) var indexedItemCount = 0

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

        // Generation IS swappable. The façade forwards to whichever backend the
        // picker selects; AFM is the default + fallback, MLX Gemma the main brain.
        let selection = RuntimeSelectionBox(.appleFoundationModels)
        runtimeSelection = selection
        let afm = AppleFoundationModelsProvider()
        let runtimeProvider = RuntimeInferenceProvider(
            selection: selection,
            backends: [
                .appleFoundationModels: afm,
                .mlxGemma: MLXGemmaProvider(),
            ],
            fallback: afm
        )
        provider = runtimeProvider
        responder = RAGResponder(store: store, embedder: embedder, provider: runtimeProvider)
        speech = AVSpeechProvider()
        ingester = DocumentIngester(store: store, embedder: embedder)
        chat = ChatSession(responder: responder)
        refreshCounts()
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

    /// Speak text via the TTS provider (provider hops to the main actor itself).
    func speak(_ text: String) async {
        await speech.speak(text)
    }

    func stopSpeaking() async {
        await speech.stop()
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
            ? MLXEmbeddingService()
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

    private func refreshCounts() {
        indexedItemCount = (try? store.itemCount()) ?? indexedItemCount
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
