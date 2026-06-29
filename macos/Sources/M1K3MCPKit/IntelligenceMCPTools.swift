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
//  AsyncTimeout helper (written 2026-06-12 but never called) into the
//  ask_m1k3 handler. A think-phase question on a slow brain used to out-run
//  the MCP client's request deadline (-32001) while the server kept
//  generating, holding the single-flight lock — the wedge AsyncTimeout was
//  built to kill. Now a deadline races the generation: on expiry the helper
//  cancels the MLX loop (freeing the lock) and we surface a clean MCPVoiceError
//  instead of a raw transport timeout. Deadline is a parameter (default
//  `defaultAskTimeoutSeconds`) so Huge-brain sessions can raise it. Build/⌘R
//  verify pending — sandbox can't compile FoundationModels.
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

/// Server-side deadline for a single `ask_m1k3` generation. Set just under the
/// typical ~60s MCP client request deadline so a runaway think-phase surfaces a
/// clean message (and frees the single-flight lock) BEFORE the client reports a
/// raw `-32001`. Legitimate answers are "tens of seconds"; true wedges are
/// minutes. Raise it for Huge-brain sessions that genuinely need longer.
public let defaultAskTimeoutSeconds: Double = 50

public func makeIntelligenceToolDefinitions(
    handlers: IntelligenceToolHandlers,
    askTimeoutSeconds: Double = defaultAskTimeoutSeconds
) -> [MCPToolDefinition] {
    [
        MCPToolDefinition(
            tool: Tool(
                name: "ask_m1k3",
                description: "Ask M1K3's local brain a question. The answer is grounded in M1K3's "
                    + "private knowledge store with section-level citations, and may use web search if "
                    + "the user has it enabled in M1K3's settings. Fully local inference — can take tens "
                    + "of seconds on the larger brains. Check get_status for which brain is active "
                    + "and whether a conversation is in progress.",
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
                do {
                    return try await withTimeout(seconds: askTimeoutSeconds) {
                        try await handlers.ask(question)
                    }
                } catch let timeout as TimeoutError {
                    // The generation lost the race to the clock: withTimeout has
                    // already cancelled it (freeing the single-flight lock). Give
                    // the caller a clean, actionable message rather than a raw
                    // transport -32001.
                    throw MCPVoiceError(
                        "M1K3 didn't finish within \(Int(timeout.seconds))s — likely a long think phase. "
                            + "Try a shorter or more direct question, turn thinking off, or switch to a faster brain."
                    )
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
