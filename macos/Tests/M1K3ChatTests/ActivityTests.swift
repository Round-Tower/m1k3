//
//  ActivityTests.swift
//  M1K3ChatTests
//
//  The activity channel is the cover for the agent loop's streaming silence:
//  the responder reports what it's doing, ChatSession shows it on the
//  in-flight assistant message, and the label clears the moment real tokens
//  (or completion) arrive. Labeler copy is pure; the session behavior is
//  pinned with a gated fake responder.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Knowledge
import Testing

struct ActivityLabelerTests {
    @Test("known tools get tailored copy")
    func knownTools() {
        #expect(ActivityLabeler.label(for: .usingTool(name: "web_search", argument: "rain in Dublin"))
            == "Searching the web for “rain in Dublin”…")
        #expect(ActivityLabeler.label(for: .usingTool(name: "search_knowledge", argument: "seals"))
            == "Searching your knowledge…")
        #expect(ActivityLabeler.label(for: .usingTool(name: "datetime", argument: ""))
            == "Checking the date & time…")
        #expect(ActivityLabeler.label(for: .usingTool(name: "system_status", argument: ""))
            == "Checking system status…")
    }

    @Test("fetch_page shows which site is being read")
    func fetchPageLabel() {
        #expect(ActivityLabeler.label(for: .usingTool(
            name: "fetch_page", argument: "https://weather.com/boston/10-day"
        )) == "Reading weather.com…")
        #expect(ActivityLabeler.label(for: .usingTool(name: "fetch_page", argument: "junk"))
            == "Reading a web page…")
    }

    @Test("unknown tools and the loop phases have sensible copy")
    func phases() {
        #expect(ActivityLabeler.label(for: .retrieving) == "Looking through your knowledge…")
        #expect(ActivityLabeler.label(for: .thinking(iteration: 0)) == "Thinking…")
        #expect(ActivityLabeler.label(for: .usingTool(name: "query_graph", argument: "x"))
            == "Using query_graph…")
    }

    @Test("long web queries are truncated in the label")
    func truncatesLongQueries() {
        let longQuery = String(repeating: "very ", count: 30)
        let label = ActivityLabeler.label(for: .usingTool(name: "web_search", argument: longQuery))
        #expect(label.count < 80)
        #expect(label.contains("…"))
    }
}

// MARK: - ChatSession integration

/// Reports activity, then holds the stream open until the test releases it —
/// so the label is observable mid-flight.
private final class GatedActivityResponder: RAGResponding, @unchecked Sendable {
    private var release: (() -> Void)?

    func answerStreaming(
        _ question: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        try await answerStreaming(question, onActivity: { _ in })
    }

    func answerStreaming(
        _: String,
        onActivity: @escaping @Sendable (ResponderActivity) -> Void
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        onActivity(.usingTool(name: "web_search", argument: "weather"))
        let stream = AsyncStream<String> { continuation in
            release = {
                continuation.yield("Answer.")
                continuation.finish()
            }
        }
        return ([], stream)
    }

    func finish() {
        release?()
    }
}

@MainActor
struct ChatSessionActivityTests {
    @Test("the in-flight assistant message shows the activity label, cleared on completion")
    func labelLifecycle() async throws {
        let responder = GatedActivityResponder()
        let session = ChatSession(responder: responder)

        let sendTask = Task { await session.send("what's the weather?") }

        // Wait for the activity hop to land on the main actor.
        var label: String?
        for _ in 0 ..< 200 where label == nil {
            try await Task.sleep(for: .milliseconds(5))
            label = session.messages.last?.activityLabel
        }
        #expect(label == "Searching the web for “weather”…")

        responder.finish()
        await sendTask.value

        let assistant = session.messages.last
        #expect(assistant?.status == .complete)
        #expect(assistant?.text == "Answer.")
        #expect(assistant?.activityLabel == nil)
    }

    @Test("activityLabel is not persisted in the transcript")
    func labelNotPersisted() throws {
        var message = ChatMessage(role: .assistant, text: "hi", status: .complete)
        message.activityLabel = "Thinking…"
        let data = try JSONEncoder().encode([message])
        let decoded = try JSONDecoder().decode([ChatMessage].self, from: data)
        #expect(decoded.first?.activityLabel == nil)
        #expect(decoded.first?.text == "hi")
    }
}
