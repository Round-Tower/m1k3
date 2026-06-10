//
//  ChatSession.swift
//  M1K3Chat
//
//  The chat state the SwiftUI shell binds to. @MainActor + @Observable so views
//  observe `messages`/`isResponding` directly; all mutation is main-actor hopped,
//  which is also where SwiftUI wants its updates. The logic that actually carries
//  bugs — folding a streamed answer into displayable state — lives here under
//  test, not in a View, so the shell stays dumb (per the app-shell plan).
//
//  Streaming normalisation: the InferenceProvider contract is "cumulative-or-delta
//  (backend-defined)". Apple Foundation Models yields cumulative snapshots; the
//  lighter providers yield deltas. `fold` detects which per-chunk — if the chunk
//  extends what we have (`hasPrefix`), it's a snapshot and replaces; otherwise
//  it's a delta and appends. One reducer renders both correctly.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Knowledge
import Observation

/// The seam ChatSession drives. RAGResponder conforms; tests inject fakes so the
/// reducer is exercised without a model, store, or embedder.
///
/// The two requirements are cross-defaulted: implement either and the other
/// follows (implementing neither recurses — conformers must provide one).
public protocol RAGResponding: Sendable {
    /// Stream the answer. The stream is the model's RAW output — citation validation
    /// can't happen mid-stream, so the CALLER must validate the accumulated text once
    /// the stream finishes (see `ChatSession.send`, which runs `CitationValidator`
    /// against `sources`). This is the deliberate counterpart to the blocking
    /// `RAGResponder.answer()`, which validates internally before returning.
    func answerStreaming(
        _ question: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>)

    /// As above, additionally reporting progress (retrieval, agent thinking,
    /// tool use) so the UI can show what's happening before tokens arrive.
    func answerStreaming(
        _ question: String,
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>)
}

public extension RAGResponding {
    func answerStreaming(
        _ question: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question, onActivity: { _ in })
    }

    func answerStreaming(
        _ question: String,
        onActivity _: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question)
    }
}

/// One turn in the transcript. `sources` are the chunks the answer was grounded
/// in (attached before tokens arrive, so the UI can show provenance up front).
public struct ChatMessage: Identifiable, Sendable, Equatable, Codable {
    public enum Role: Sendable, Equatable, Codable { case user, assistant }
    public enum Status: Sendable, Equatable, Codable {
        case complete
        case streaming
        case failed(String)
    }

    public let id: UUID
    public let role: Role
    public var text: String
    public var sources: [ChunkHit]
    /// Citations the model made that were validated against `sources` (hallucinated
    /// ones are stripped from `text`). Derived at response time — deliberately omitted
    /// from `CodingKeys` so it isn't persisted and old transcripts still decode.
    public var citations: [Citation] = []
    /// What the responder is doing right now ("Searching the web…"), shown while
    /// `.streaming` and no tokens have arrived. Transient — omitted from
    /// `CodingKeys` like `citations`.
    public var activityLabel: String?
    public var status: Status

    enum CodingKeys: String, CodingKey {
        case id, role, text, sources, status
    }

    public init(
        id: UUID = UUID(),
        role: Role,
        text: String,
        sources: [ChunkHit] = [],
        citations: [Citation] = [],
        status: Status
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.sources = sources
        self.citations = citations
        self.status = status
    }
}

/// Persists the chat transcript to a JSON file so a relaunch keeps the
/// conversation. Pure file I/O over Codable messages — no AppKit, fully testable.
public struct ChatTranscriptStore: Sendable {
    private let url: URL

    public init(url: URL) {
        self.url = url
    }

    public func load() -> [ChatMessage] {
        guard let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([ChatMessage].self, from: data)) ?? []
    }

    public func save(_ messages: [ChatMessage]) {
        guard let data = try? JSONEncoder().encode(messages) else { return }
        try? data.write(to: url, options: .atomic)
    }
}

@MainActor
@Observable
public final class ChatSession {
    public private(set) var messages: [ChatMessage] = []
    public private(set) var isResponding = false

    private let responder: any RAGResponding
    private let transcript: ChatTranscriptStore?

    public init(responder: any RAGResponding, transcript: ChatTranscriptStore? = nil) {
        self.responder = responder
        self.transcript = transcript
        if let transcript {
            messages = transcript.load()
        }
    }

    /// Send a user turn and stream the grounded answer into a new assistant
    /// message. Blank input and re-entrant sends are no-ops.
    public func send(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isResponding else { return }

        messages.append(ChatMessage(role: .user, text: trimmed, status: .complete))
        let assistantID = UUID()
        messages.append(ChatMessage(id: assistantID, role: .assistant, text: "", status: .streaming))

        isResponding = true
        defer { isResponding = false }

        do {
            let (sources, stream) = try await responder.answerStreaming(
                trimmed,
                onActivity: { [weak self] activity in
                    Task { @MainActor in
                        self?.update(assistantID) {
                            $0.activityLabel = ActivityLabeler.label(for: activity)
                        }
                    }
                }
            )
            update(assistantID) { $0.sources = sources }
            for await chunk in stream {
                update(assistantID) {
                    $0.text = Self.fold($0.text, chunk)
                    // Real tokens replace the activity label.
                    $0.activityLabel = nil
                }
            }
            // Now the full answer is in hand: strip any citations the model invented
            // (not grounded in the retrieved sources) and record the validated ones.
            let finalText = messages.first { $0.id == assistantID }?.text ?? ""
            let validation = await CitationValidator.validate(responseText: finalText, against: sources)
            update(assistantID) {
                // Flatten model markdown + tidy whitespace once the full text
                // is in hand (ReadingText renders plain text).
                $0.text = MessageTextPolish.polish(validation.cleanedText)
                $0.citations = validation.validated
                $0.activityLabel = nil
                $0.status = .complete
            }
        } catch {
            update(assistantID) { msg in
                msg.status = .failed(String(describing: error))
                msg.activityLabel = nil
                if msg.text.isEmpty {
                    msg.text = ChatFailureMessage.userFacing(for: error)
                }
            }
        }
        transcript?.save(messages)
    }

    /// Clear the transcript (and its persisted file).
    public func clear() {
        messages = []
        transcript?.save([])
    }

    /// Fold a streamed chunk into the accumulated answer. A chunk that extends
    /// the current text is a cumulative snapshot (replace); anything else is a
    /// delta (append). Handles both provider contracts with one rule.
    static func fold(_ current: String, _ chunk: String) -> String {
        chunk.hasPrefix(current) ? chunk : current + chunk
    }

    private func update(_ id: UUID, _ mutate: (inout ChatMessage) -> Void) {
        guard let index = messages.firstIndex(where: { $0.id == id }) else { return }
        mutate(&messages[index])
    }
}
