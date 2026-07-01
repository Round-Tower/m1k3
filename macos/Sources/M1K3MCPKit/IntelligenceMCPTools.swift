//
//  IntelligenceMCPTools.swift
//  M1K3MCPKit
//
//  The resident-and-visitor tools: `ask_m1k3` reaches the local brain (a
//  grounded, cited answer from the full RAG/agent pipeline) and `remember`
//  writes into the permanent knowledge store — the durable-memory primitive
//  ("index this for M1K3") that makes the store more useful to a visiting
//  agent than its own session notes: queryable, section-attributed,
//  surviving every context window.
//
//  Handlers are app-injected closures (the VoiceToolHandlers pattern) — this
//  package stays free of inference/store links.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (contract
//  test-pinned with fakes; live behaviour rides tested seams — HeadlessAsk
//  in M1K3Chat, DocumentIngester in M1K3Knowledge). Prior: the MCP test
//  report 2026-06-11 (F5) + the resident/visitor reflection.
//
//  Review: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.8. Wired the
//  AsyncTimeout helper into ask_m1k3 so a runaway think-phase surfaced a clean
//  message instead of a raw -32001 at the MCP client's ~60s request deadline.
//
//  Review: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85. Replaced that
//  request-path timeout with a HYBRID submit-and-poll: a long turn (web search +
//  a verbose thinker) genuinely can't finish inside the client's deadline, so
//  ask_m1k3 now submits the generation to an AskJobStore, waits a short GRACE
//  window (the common fast case still returns the answer inline, unchanged), and
//  otherwise returns a job id the client fetches later via the new get_answer
//  tool. The generation runs detached with its own cap (inside handlers.ask), so
//  it finishes and is fetchable even after this request returns. Build/⌘R verify
//  pending — the live wire (job survival across stateless requests) is verify-by-launch.
//

import Foundation
import MCP

/// The app-injected implementations behind the intelligence tools.
public struct IntelligenceToolHandlers: Sendable {
    /// Ask the active brain. Returns the final answer text (citations intact,
    /// reasoning stripped, sources appended).
    public var ask: @Sendable (_ question: String) async throws -> String
    /// Index text into the knowledge store. Returns a human confirmation
    /// ("Indexed "Title" — 4 chunks.").
    public var remember: @Sendable (_ title: String, _ text: String) async throws -> String

    public init(
        ask: @escaping @Sendable (_ question: String) async throws -> String,
        remember: @escaping @Sendable (_ title: String, _ text: String) async throws -> String
    ) {
        self.ask = ask
        self.remember = remember
    }
}

/// How long `ask_m1k3` waits inline before handing back a job id to poll. Kept
/// well under the MCP client's ~60s request deadline: the common fast answer
/// still returns within this window (contract unchanged), while a genuinely long
/// turn hands back a job id instead of dying on the wire. The generation keeps
/// running detached and is fetched via `get_answer`.
public let defaultAskGraceSeconds: Double = 8

public func makeIntelligenceToolDefinitions(
    handlers: IntelligenceToolHandlers,
    jobStore: AskJobStore = AskJobStore(),
    graceSeconds: Double = defaultAskGraceSeconds
) -> [MCPToolDefinition] {
    [
        MCPToolDefinition(
            tool: Tool(
                name: "ask_m1k3",
                description: "Ask M1K3's local brain a question. The answer is grounded in M1K3's "
                    + "private knowledge store with section-level citations, and may use web search if "
                    + "the user has it enabled in M1K3's settings. Fully local inference. Fast answers "
                    + "return inline; a long turn (web search on a larger brain) instead returns a job id "
                    + "— call get_answer with it in a few seconds to fetch the result. Check get_status "
                    + "for which brain is active.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "question": ["type": "string", "description": "the question to ask"],
                    ],
                    "required": ["question"],
                ]
            ),
            handler: { args in
                let question = stringArg(args, "question")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !question.isEmpty else { throw MCPVoiceError("ask_m1k3 requires a non-empty question") }

                let id = await jobStore.submit()
                // Detached generation: outlives this request so a turn that beats
                // the client's ~60s deadline still finishes and is fetchable via
                // get_answer. Its own cap lives inside handlers.ask (app-side).
                Task {
                    do {
                        let answer = try await handlers.ask(question)
                        await jobStore.complete(id: id, result: answer)
                    } catch {
                        await jobStore.fail(id: id, message: describeAskError(error))
                    }
                }

                // Grace window: the common fast case returns the answer inline,
                // exactly as before. Poll the store cheaply until the deadline.
                let deadline = ContinuousClock.now.advanced(by: .seconds(graceSeconds))
                while ContinuousClock.now < deadline {
                    switch await jobStore.status(of: id) {
                    case let .done(answer):
                        return answer
                    case let .error(message):
                        throw MCPVoiceError(message)
                    case .running, .none:
                        try? await Task.sleep(for: .milliseconds(50))
                    }
                }
                return "M1K3 is still working on this one — it's taking longer than usual "
                    + "(a long think or a web search). Call get_answer with job_id \"\(id)\" in a "
                    + "few seconds to fetch the result."
            }
        ),
        MCPToolDefinition(
            tool: Tool(
                name: "get_answer",
                description: "Fetch the result of an ask_m1k3 call that returned a job id because it "
                    + "was taking a while. Returns the grounded answer once ready, or asks you to poll "
                    + "again if M1K3 is still working. Only needed when ask_m1k3 handed back a job id.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "job_id": ["type": "string", "description": "the job id ask_m1k3 returned"],
                    ],
                    "required": ["job_id"],
                ]
            ),
            handler: { args in
                let id = stringArg(args, "job_id")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !id.isEmpty else { throw MCPVoiceError("get_answer requires a job_id") }
                switch await jobStore.status(of: id) {
                case .none:
                    throw MCPVoiceError(
                        "No such job \"\(id)\" — it may have expired. Ask again with ask_m1k3."
                    )
                case .running:
                    return "M1K3 is still working on job \"\(id)\" — poll get_answer again in a few seconds."
                case let .done(answer):
                    return answer
                case let .error(message):
                    throw MCPVoiceError(message)
                }
            }
        ),
        MCPToolDefinition(
            tool: Tool(
                name: "remember",
                description: "Store text in M1K3's memory — it becomes part of what M1K3 knows, "
                    + "searchable in every future conversation (the same store search_knowledge "
                    + "reads). Use it for durable facts, notes, summaries, decisions. "
                    + "Survives every session.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "title": ["type": "string", "description": "a short title for the entry"],
                        "text": ["type": "string", "description": "the content to index"],
                    ],
                    "required": ["title", "text"],
                ]
            ),
            handler: { args in
                let title = stringArg(args, "title")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let text = stringArg(args, "text")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !title.isEmpty, !text.isEmpty else {
                    throw MCPVoiceError("remember requires both a title and text")
                }
                return try await handlers.remember(title, text)
            }
        ),
    ]
}

/// Preserve a clean, client-facing message when stashing a failed job — an
/// MCPVoiceError's own text (e.g. the "in a conversation right now" busy line)
/// rather than a wrapped debug description.
private func describeAskError(_ error: Error) -> String {
    if let voiceError = error as? MCPVoiceError { return voiceError.description }
    return error.localizedDescription
}
