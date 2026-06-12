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
/// Default chain is strictly ONE-WAY (history → activity → plain): the richer
/// variants fall through to the plain one, which has NO default — so every
/// conformer must ground the chain, and a half-implemented conformer is a
/// compile error, never a mutual-default infinite recursion (the PR #15
/// review's blocking finding).
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

    /// As above, with a capped replay of recent turns so follow-up questions
    /// carry their context ("and in Fahrenheit?"). Responders that don't use
    /// history fall through to the history-free variant.
    func answerStreaming(
        _ question: String,
        history: [ChatTurn],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>)

    /// Sources gathered DURING the turn by tools the model called (e.g.
    /// search_knowledge). DESTRUCTIVE: this is a draining read — a second call
    /// returns empty. Call exactly once, after the stream completes; the hits
    /// merge into the message's sources and the citation validator's allow-list.
    /// Defaults to empty for responders without tools.
    func collectedSources() -> [ChunkHit]
}

public extension RAGResponding {
    func answerStreaming(
        _ question: String,
        onActivity _: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question)
    }

    func answerStreaming(
        _ question: String,
        history _: [ChatTurn],
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question, onActivity: onActivity)
    }

    func collectedSources() -> [ChunkHit] {
        []
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
    /// A reasoning model's `<think>…</think>` chain-of-thought, separated from
    /// the answer and surfaced (collapsibly) for transparency. Persisted as an
    /// optional key, so old transcripts (without it) still decode to nil.
    public var reasoning: String?
    public var status: Status

    enum CodingKeys: String, CodingKey {
        case id, role, text, sources, status, reasoning
    }

    public init(
        id: UUID = UUID(),
        role: Role,
        text: String,
        sources: [ChunkHit] = [],
        citations: [Citation] = [],
        reasoning: String? = nil,
        status: Status
    ) {
        self.id = id
        self.role = role
        self.text = text
        self.sources = sources
        self.citations = citations
        self.reasoning = reasoning
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

    /// The conversation the transcript belongs to. Rows are created LAZILY —
    /// only `send` writes — so empty conversations never reach the store.
    public private(set) var activeConversationID = UUID()
    /// nil until auto-titled (UI shows "New chat").
    public private(set) var activeTitle: String?
    /// Bumped on every persist/title/delete — the history drawer reads this
    /// observable counter to re-fetch summaries (the CallsView callCount trick).
    public private(set) var historyRevision = 0

    private let responder: any RAGResponding
    /// Internal (not private): ChatSession+Conversations.swift is a cross-file
    /// same-module extension and needs the seams.
    let history: (any ChatHistoryPersisting)?
    let titler: (any ConversationTitling)?
    /// nil = memory auto-capture not wired (feature off, tests unaffected).
    let distillation: MemoryDistillationCoordinator?
    /// Read fresh at every trigger — the Settings toggle applies immediately
    /// (the thinkingModeProvider pattern).
    let autoCaptureEnabled: @Sendable () -> Bool
    /// Test hook — `await titlingTask?.value` makes fire-and-forget titling
    /// deterministic in tests.
    private(set) var titlingTask: Task<Void, Never>?
    /// Test hook, same contract as titlingTask. Single-flight: a skipped
    /// trigger is caught at the next exit because the watermark didn't move.
    private(set) var distillationTask: Task<Void, Never>?

    /// Launch catch-up waits this long so it never contends with the user's
    /// first turn for the model.
    nonisolated static let launchCatchUpDelay: Duration = .seconds(5)
    /// A monster legacy transcript distills at most this many recent turns
    /// (DEFAULT-0 watermarks mean old conversations distill once, in full).
    nonisolated static let maxDistillationTurns = 40

    public init(
        responder: any RAGResponding,
        history: (any ChatHistoryPersisting)? = nil,
        titler: (any ConversationTitling)? = nil,
        distillation: MemoryDistillationCoordinator? = nil,
        autoCaptureEnabled: @escaping @Sendable () -> Bool = { true }
    ) {
        self.responder = responder
        self.history = history
        self.titler = titler
        self.distillation = distillation
        self.autoCaptureEnabled = autoCaptureEnabled
        // Relaunch restores the most recent conversation — the same feel the
        // single-transcript file gave, now one of many.
        if let history,
           let recent = try? history.list().first,
           let restored = try? history.loadMessages(id: recent.id)
        {
            messages = restored
            activeConversationID = recent.id
            activeTitle = recent.title
        }
        // Launch catch-up: a quit-without-switching leaves undistilled turns
        // behind; distill the restored conversation's backlog now (delayed —
        // never racing the user's first turn). App-quit-time distillation was
        // rejected: a fire-and-forget task racing termination loses silently.
        if distillation != nil, !messages.isEmpty {
            Task { [weak self] in
                try? await Task.sleep(for: Self.launchCatchUpDelay)
                self?.scheduleDistillationIfNeeded()
            }
        }
    }

    /// Send a user turn and stream the grounded answer into a new assistant
    /// message. Blank input and re-entrant sends are no-ops.
    public func send(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isResponding else { return }

        // Capture the replayable history BEFORE this turn joins the transcript:
        // completed turns only, answer text only (reasoning/sources never leave
        // the message). HistoryWindow caps how much of it reaches the prompt.
        let history = messages.compactMap { message -> ChatTurn? in
            guard case .complete = message.status, !message.text.isEmpty else { return nil }
            return ChatTurn(role: message.role == .user ? .user : .assistant, text: message.text)
        }

        messages.append(ChatMessage(role: .user, text: trimmed, status: .complete))
        let assistantID = UUID()
        messages.append(ChatMessage(id: assistantID, role: .assistant, text: "", status: .streaming))

        isResponding = true
        defer { isResponding = false }

        do {
            let (sources, stream) = try await responder.answerStreaming(
                trimmed,
                history: history,
                onActivity: { [weak self] activity in
                    Task { @MainActor in
                        self?.update(assistantID) {
                            $0.activityLabel = ActivityLabeler.label(for: activity)
                        }
                    }
                }
            )
            update(assistantID) { $0.sources = sources }
            // Route chunks to reasoning vs answer LIVE, so chain-of-thought
            // streams into the disclosure as it happens instead of flashing
            // raw <think> text in the bubble until the stream ends.
            var splitter = StreamingReasoningSplitter()
            for await chunk in stream {
                splitter.feed(chunk)
                let liveAnswer = splitter.answer
                let liveReasoning = splitter.reasoning
                update(assistantID) {
                    $0.text = liveAnswer
                    $0.reasoning = liveReasoning.isEmpty ? nil : liveReasoning
                    // Real tokens replace the activity label.
                    $0.activityLabel = nil
                }
            }
            splitter.finish()
            // Now the full text is in hand. Re-split the RAW stream as the
            // final authority (the live splitter only drives rendering), then
            // strip invented citations from the ANSWER and record the
            // validated ones. The allow-list covers BOTH the injected sources
            // and whatever the model retrieved itself via search_knowledge.
            let (reasoning, answer) = ReasoningSplit.split(splitter.raw)
            let mergedSources = Self.mergeSources(sources, responder.collectedSources())
            let validation = await CitationValidator.validate(responseText: answer, against: mergedSources)
            update(assistantID) {
                $0.sources = mergedSources
                // Flatten model markdown + tidy whitespace once the full text
                // is in hand (ReadingText renders plain text).
                $0.text = MessageTextPolish.polish(validation.cleanedText)
                $0.citations = validation.validated
                $0.reasoning = reasoning
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
        persistActiveConversation()
        scheduleTitlingIfNeeded(question: trimmed)
    }

    /// Save the live transcript to the active conversation's row and tell the
    /// drawer to refresh. Lazy row creation happens here — nowhere else writes.
    func persistActiveConversation() {
        guard let history else { return }
        try? history.save(id: activeConversationID, messages: messages, updatedAt: Date())
        historyRevision += 1
    }

    /// The one mutation point ChatSession+Conversations routes through —
    /// private(set) setters are file-scoped, so the cross-file extension
    /// cannot (and must not) touch the stored properties directly.
    func setActiveConversation(id: UUID, messages: [ChatMessage], title: String?) {
        self.messages = messages
        activeConversationID = id
        activeTitle = title
    }

    func noteHistoryChanged() {
        historyRevision += 1
    }

    /// Fire-and-forget auto-titling after a successful exchange of an untitled
    /// conversation. Never blocks send; failure or garbage output just means
    /// "retry after the next exchange". The conversation id is captured at
    /// spawn so a title resolving after a switch/new lands on the RIGHT row.
    private func scheduleTitlingIfNeeded(question: String) {
        guard let history, let titler, activeTitle == nil, titlingTask == nil else { return }
        guard let answer = messages.last, answer.role == .assistant,
              case .complete = answer.status, !answer.text.isEmpty else { return }
        let conversationID = activeConversationID
        let answerText = answer.text
        titlingTask = Task { [weak self] in
            defer { self?.titlingTask = nil }
            guard let raw = try? await titler.title(forUser: question, assistant: answerText),
                  let title = TitleSanitizer.sanitize(raw) else { return }
            try? history.setTitle(id: conversationID, title: title)
            guard let self else { return }
            if self.activeConversationID == conversationID {
                self.activeTitle = title
            }
            self.historyRevision += 1
        }
    }

    /// Fire-and-forget memory distillation over the transcript content the
    /// watermark hasn't covered. Called at conversation EXIT (new/switch,
    /// BEFORE the swap) and by the launch catch-up — never per turn, never on
    /// delete (deletion is discard intent). The titling blueprint throughout:
    /// id + count captured at spawn, defer cleanup, task exposed for tests.
    /// Watermark advances ONLY on success (a throw leaves the slice for the
    /// next trigger to retry).
    func scheduleDistillationIfNeeded() {
        guard let history, let distillation, autoCaptureEnabled() else { return }
        guard distillationTask == nil, !isResponding else { return }
        let conversationID = activeConversationID
        let snapshotCount = messages.count
        let watermark = (try? history.distilledWatermark(id: conversationID)) ?? 0
        guard watermark < snapshotCount else { return }
        let fresh = messages[min(watermark, snapshotCount)...].compactMap { message -> ChatTurn? in
            guard case .complete = message.status, !message.text.isEmpty else { return nil }
            return ChatTurn(role: message.role == .user ? .user : .assistant, text: message.text)
        }
        // At least one full new exchange — never distill a lone dangling turn.
        guard fresh.contains(where: { $0.role == .user }),
              fresh.contains(where: { $0.role == .assistant }) else { return }
        let turns = Array(fresh.suffix(Self.maxDistillationTurns))
        distillationTask = Task { [weak self] in
            defer { self?.distillationTask = nil }
            guard (try? await distillation.distillAndStore(turns: turns)) != nil else { return }
            try? history.setDistilledWatermark(id: conversationID, count: snapshotCount)
        }
    }

    /// Fold a streamed chunk into the accumulated answer. A chunk that extends
    /// the current text is a cumulative snapshot (replace); anything else is a
    /// delta (append). Handles both provider contracts with one rule.
    nonisolated static func fold(_ current: String, _ chunk: String) -> String {
        chunk.hasPrefix(current) ? chunk : current + chunk
    }

    /// Injected sources + what the model retrieved itself, deduped by chunk
    /// (injection order first, so the UI's source rows stay stable).
    nonisolated static func mergeSources(_ injected: [ChunkHit], _ collected: [ChunkHit]) -> [ChunkHit] {
        var seen = Set<UUID>()
        return (injected + collected).filter { seen.insert($0.chunkID).inserted }
    }

    private func update(_ id: UUID, _ mutate: (inout ChatMessage) -> Void) {
        guard let index = messages.firstIndex(where: { $0.id == id }) else { return }
        mutate(&messages[index])
    }
}
