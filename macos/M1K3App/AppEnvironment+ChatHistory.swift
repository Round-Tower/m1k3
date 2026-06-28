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
import M1K3LanguageModel
import os
import Synchronization

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
        guard UserDefaults.standard.bool(forKey: Self.autoRouteBrainKey) else { return }
        let tier = resolvedAutoRouteTier()
        guard tier != selectedBrain else { return }
        Self.routeLog.info("auto-route → \(tier.rawValue, privacy: .public)")
        selectBrain(tier)
    }

    /// The brain auto-route resolves to for THIS Mac right now, given current
    /// Apple-Intelligence availability and consent settings (ADR 0001). Pure read —
    /// no side effects — so both `applyAutoRouteIfEnabled` and onboarding's
    /// "Let M1K3 choose" can ask "what would auto-route pick?" without committing.
    func resolvedAutoRouteTier() -> BrainTier {
        let defaults = UserDefaults.standard
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

        let routedTier: BrainTier
        switch route {
        case .appleOnDevice:
            routedTier = .mini
        case .mlxFloor, .privateCloud, .thirdParty:
            // No network-brain backends are wired yet, so those rungs resolve to the
            // local MLX floor: keep the current MLX brain, else default to Lil.
            routedTier = selectedBrain.mlxModelID != nil ? selectedBrain : .lil
        }
        // Prudent compute: ease the automatic pick down to what THIS Mac can run
        // comfortably (lower-only — never a silent upgrade-download). This is what
        // makes auto-route machine-aware: Big has no hard memory floor, so without
        // the cap it would otherwise ride along on a small Mac and thrash swap.
        // Manual selection stays sovereign (capped only on the AUTOMATIC path).
        return BrainTier.cappedForThisMac(routedTier)
    }

    /// Onboarding "Let M1K3 choose": turn auto-route ON and resolve the concrete
    /// starting brain so the first-run flow can drive the SAME download/wake UI a
    /// manual pick uses. Returns the resolved tier so the caller can show it.
    @discardableResult
    func enableAutoRouteForOnboarding() -> BrainTier {
        UserDefaults.standard.set(true, forKey: Self.autoRouteBrainKey)
        let tier = resolvedAutoRouteTier()
        selectBrain(tier)
        return tier
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
    /// `onOpenLink`, when supplied, adds the `open_link` agent tool so the model
    /// can surface a web page into the review panel mid-answer. Only the
    /// interactive chat responder passes it — the MCP/ask responder (forced
    /// `.fast`, headless) has no panel to drive.
    nonisolated static func makeAgentResponder(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        forcedThinkingMode: ThinkingMode? = nil,
        onOpenLink: (@Sendable (URL) -> Void)? = nil
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
                    ListDocumentsTool(store: store),
                    GetDocumentTool(store: store),
                ]
                let defaults = UserDefaults.standard
                let webAllowed = defaults.object(forKey: Self.webSearchEnabledKey) == nil
                    || defaults.bool(forKey: Self.webSearchEnabledKey)
                if webAllowed {
                    tools.insert(WikipediaTool(), at: 0)
                    tools.insert(FetchPageTool(), at: 0)
                    tools.insert(WebSearchTool(), at: 0)
                }
                if let onOpenLink {
                    tools.append(OpenLinkTool(onOpen: onOpenLink))
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
            },
            brainNameProvider: {
                // The persisted brain tier, read fresh each turn so a runtime
                // hot-swap names the new brain on the next answer. Empty → omitted.
                let raw = UserDefaults.standard.string(forKey: Self.selectedBrainKey) ?? ""
                return BrainTier(rawValue: raw)?.displayName ?? ""
            },
            maxIterationsProvider: {
                // Thermal "ease off" is opt-in; OFF → the plain base, so the
                // default path is byte-identical. ON → CoolHeadPolicy trims the
                // agent loop to the LIVE thermal level (read fresh each turn from
                // the shared state — never snapshotted; the responder is built once
                // and long-lived). The brain is NEVER swapped (CoolHeadPolicy
                // doctrine: ease effort, not identity).
                guard Self.coolHeadEaseEnabled() else { return Self.baseMaxIterations }
                return CoolHeadPolicy.maxIterations(
                    for: Self.coolHead.withLock(\.level),
                    base: Self.baseMaxIterations
                )
            }
        )
    }

    // MARK: - Cool Head (thermal effort easing — ADR: never swaps the brain)

    /// Opt-in: ease M1K3's EFFORT when the Mac is under thermal / low-power
    /// pressure (trim the agent loop). Default OFF → byte-identical. It deliberately
    /// never swaps the chosen brain — a mid-conversation reload would dump the KV +
    /// persona caches and rename M1K3's own identity for the sake of the room
    /// temperature (CoolHeadPolicy's signed reasoning).
    nonisolated static let coolHeadEaseKey = "compute.coolHeadEase"

    nonisolated static func coolHeadEaseEnabled() -> Bool {
        UserDefaults.standard.bool(forKey: coolHeadEaseKey) // default OFF
    }

    /// The agent-loop iteration base the thermal cap eases DOWN from. Matches
    /// AgentRAGResponder's default — one named home so the two can't drift.
    nonisolated static let baseMaxIterations = 3

    /// Turns of SUSTAINED relief required before effort recovers (hysteresis) —
    /// degrade now, recover slow, so a bouncing thermalState can't flap the level.
    nonisolated static let coolHeadMinRecoveryTurns = 3

    /// Process-wide thermal state with recovery hysteresis (CoolHeadPolicy.next).
    /// Static because there is exactly one AppEnvironment, and the `nonisolated
    /// static` responder factory must read the live level without capturing `self`.
    nonisolated static let coolHead = Mutex(CoolHeadState())

    /// Advance the thermal state for this turn — degrade immediately, recover only
    /// after a sustained relief streak (hysteresis, so a bouncing thermalState can't
    /// flap the effort level). A NO-OP when the opt-in is off. Called beside
    /// `applyAutoRouteIfEnabled` at the top of `send`, so the level is current for
    /// the SAME turn's `maxIterationsProvider` read.
    func applyCoolHeadIfEnabled() {
        guard Self.coolHeadEaseEnabled() else { return }
        let info = ProcessInfo.processInfo
        let target = CoolHeadPolicy.target(
            thermal: info.thermalState,
            lowPower: info.isLowPowerModeEnabled
        )
        Self.coolHead.withLock { state in
            state = CoolHeadPolicy.next(
                state, target: target, minRecoveryTurns: Self.coolHeadMinRecoveryTurns
            )
        }
    }
}
