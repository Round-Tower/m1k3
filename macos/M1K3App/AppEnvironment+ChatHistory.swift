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
import os

extension AppEnvironment {
    private static let routeLog = Logger(subsystem: "app.m1k3", category: "route")
    /// Reused across sends so a turn doesn't allocate a fresh probe each time; the
    /// live `isAvailable` read (SystemLanguageModel.availability) stays per-call so a
    /// mid-session Apple-Intelligence change is honoured.
    private static let afmAvailabilityProbe = AppleFoundationModelsProvider()

    /// Apply the EscalationLadder's brain choice when auto-routing is on (ADR 0001).
    /// A NO-OP when off, so default behaviour is unchanged and the feature is fully
    /// reversible. Reuses `selectBrain`, so the routed pick drives the same proven
    /// load/warmup path; only switches when the pick actually changes.
    func applyAutoRouteIfEnabled() {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: Self.autoRouteBrainKey) else { return }

        // TODO: (ADR 0001 Edge A): replace with a DEDICATED egress-consent key when the
        // network rungs are wired. The web-search toggle is a provisional proxy only —
        // third-party cloud escalation (chat → Claude/Gemini) is a far bigger privacy
        // step than a web search and needs its own per-request consent flow.
        let webAllowed = defaults.object(forKey: Self.webSearchEnabledKey) == nil
            || defaults.bool(forKey: Self.webSearchEnabledKey)
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: Self.afmAvailabilityProbe.isAvailable,
            networkAllowed: webAllowed,
            preferAppleOnDevice: defaults.bool(forKey: Self.preferAppleOnDeviceKey)
        )

        let tier: BrainTier
        switch route {
        case .appleOnDevice:
            tier = .mini
        case .mlxFloor, .privateCloud, .thirdParty:
            // No network-brain backends are wired yet, so those rungs resolve to the
            // local MLX floor: keep the current MLX brain, else default to Lil.
            tier = selectedBrain.mlxModelID != nil ? selectedBrain : .lil
        }
        guard tier != selectedBrain else { return }
        Self.routeLog.info("auto-route → \(tier.rawValue, privacy: .public)")
        selectBrain(tier)
    }

    /// Memory auto-capture consent (Settings "Learn from conversations").
    /// Default ON: everything is local, every memory visible and deletable
    /// in MemoriesView — the webSearchEnabledKey default-true read pattern.
    nonisolated static let memoryAutoCaptureKey = "memoryAutoCapture"

    nonisolated static func memoryAutoCaptureEnabled() -> Bool {
        let defaults = UserDefaults.standard
        return defaults.object(forKey: memoryAutoCaptureKey) == nil
            || defaults.bool(forKey: memoryAutoCaptureKey)
    }

    /// The distillation stack: AFM-first under NEUTRAL instructions (the
    /// distiller must not speak as M1K3 — judge-not-the-defendant), falling
    /// back to the shared runtime provider. The fallback path carries the
    /// persona (a second MLX instance would double model memory); the
    /// FACT:-only parser is the defence there.
    nonisolated static func makeMemoryDistillation(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        ingester: DocumentIngester,
        fallback: any InferenceProvider
    ) -> MemoryDistillationCoordinator {
        MemoryDistillationCoordinator(
            distiller: ProviderMemoryDistiller(
                primary: AppleFoundationModelsProvider(
                    instructions: { MemoryDistillationPrompt.instructions }
                ),
                fallback: fallback
            ),
            ingester: ingester,
            store: store,
            embedder: embedder
        )
    }

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
                    tools.insert(WikipediaTool(), at: 0)
                    tools.insert(FetchPageTool(), at: 0)
                    tools.insert(WebSearchTool(), at: 0)
                }
                return tools
            },
            sourceCollector: sourceCollector,
            thinkingModeProvider: {
                // A forced mode (MCP ask_m1k3 → .fast) bypasses Settings entirely.
                if let forcedThinkingMode { return forcedThinkingMode }
                let defaults = UserDefaults.standard
                let stored = defaults.string(forKey: Self.thinkingModeKey)
                    .flatMap(ThinkingMode.init(rawValue:)) ?? .auto
                // Voice mode swaps Settings for its own in-mode toggle
                // (default off → fast; read per turn, so flips apply next turn).
                return VoiceThinkingPolicy.effectiveMode(
                    stored: stored,
                    voiceModeActive: defaults.bool(forKey: Self.voiceModeActiveKey),
                    voiceThinkingEnabled: defaults.bool(forKey: Self.voiceModeThinkingKey)
                )
            }
        )
    }
}
