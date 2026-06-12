//
//  AppEnvironment+ChatHistory.swift
//  M1K3App
//
//  Chat wiring kept out of AppEnvironment.swift because that file sits at the
//  1000-line file-length lint ceiling (the established split pattern: VoiceMode,
//  MCP, ChatHistory): the conversation-history store factory AND the tool-calling
//  chat-responder factory.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (store + migrator
//  behaviour test-pinned in M1K3Chat; this is wiring). Prior: Unknown.
//  Review: Kev + claude-opus-4-8, 2026-06-12 — relocated makeAgentResponder here
//  (it pushed AppEnvironment past 1000) + added forcedThinkingMode for MCP fast-ask.
//

import Foundation
import M1K3Agent
import M1K3AgentTools
import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import M1K3KnowledgeTools

extension AppEnvironment {
    /// Build the conversation store and run the one-shot legacy-transcript
    /// migration BEFORE ChatSession init reads it (resume-most-recent must see
    /// the import). nil on store failure → chat degrades to non-persistent,
    /// exactly like the old optional transcript.
    static func makeChatHistoryStore(in dir: URL) -> (any ChatHistoryPersisting)? {
        guard let store = try? GRDBChatHistoryStore(
            path: dir.appendingPathComponent("chat-history.sqlite").path
        ) else { return nil }
        try? TranscriptMigrator.migrateIfNeeded(
            legacyURL: dir.appendingPathComponent("transcript.json"),
            into: store
        )
        return store
    }

    /// The tool-calling chat responder: every turn runs the agent loop with
    /// retrieve-first grounding, plus web search / datetime / system status /
    /// a second knowledge lookup as tools. The tool list is built fresh each
    /// turn so the Settings web-search toggle applies immediately — disabled
    /// means the model never even sees the tool.
    /// `forcedThinkingMode` pins the reasoning budget regardless of Settings —
    /// the MCP `ask_m1k3` path passes `.fast`. ask_m1k3 is a grounding tool for
    /// a visiting agent ("find and cite my private corpus"), not a synthesis
    /// engine: on these small local brains the think phase is what blows past
    /// the deadline on anything but a lookup (test-report follow-up, 2026-06-12).
    /// nil keeps the Settings picker + voice-mode override (the chat/voice UI).
    nonisolated static func makeAgentResponder(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        forcedThinkingMode: ThinkingMode? = nil
    ) -> any RAGResponding {
        // Hits the model retrieves itself (search_knowledge) flow through the
        // collector into the turn's sources + the citation allow-list.
        let sourceCollector = ToolSourceCollector()
        return AgentRAGResponder(
            store: store,
            embedder: embedder,
            provider: provider,
            toolsProvider: {
                var tools: [any AgentTool] = [
                    DateTimeTool(),
                    SystemStatusTool(),
                    SearchKnowledgeTool(
                        store: store,
                        embedder: embedder,
                        onHits: { hits in sourceCollector.record(hits) }
                    ),
                ]
                let defaults = UserDefaults.standard
                let webAllowed = defaults.object(forKey: Self.webSearchEnabledKey) == nil
                    || defaults.bool(forKey: Self.webSearchEnabledKey)
                if webAllowed {
                    tools.insert(FetchPageTool(), at: 0)
                    tools.insert(WebSearchTool(), at: 0)
                }
                return tools
            },
            sourceCollector: sourceCollector,
            thinkingModeProvider: {
                // A forced mode (MCP ask_m1k3 → .fast) bypasses Settings entirely.
                if let forcedThinkingMode { return forcedThinkingMode }
                let stored = UserDefaults.standard.string(forKey: Self.thinkingModeKey)
                    .flatMap(ThinkingMode.init(rawValue:)) ?? .auto
                // Voice mode maps Auto → fast replies (latency IS the UX in a
                // spoken loop); an explicit Always is respected.
                if stored == .auto, UserDefaults.standard.bool(forKey: Self.voiceModeActiveKey) {
                    return .fast
                }
                return stored
            }
        )
    }
}
