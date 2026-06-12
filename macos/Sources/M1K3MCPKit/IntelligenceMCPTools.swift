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

public func makeIntelligenceToolDefinitions(handlers: IntelligenceToolHandlers) -> [MCPToolDefinition] {
    [
        MCPToolDefinition(
            tool: Tool(
                name: "ask_m1k3",
                description: "Ask M1K3's local brain a question. The answer is grounded in M1K3's "
                    + "private knowledge store with section-level citations, and may use web search if "
                    + "the user has it enabled in M1K3's settings. Fully local inference — can take tens "
                    + "of seconds on the larger brains. Check get_voice_status for which brain is active "
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
                return try await handlers.ask(question)
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
