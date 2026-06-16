//
//  MCPHostController.swift
//  M1K3App
//
//  Hosts the M1K3 MCP server IN-PROCESS over loopback HTTP (default OFF —
//  privacy first), serving the live KnowledgeStore plus the voice tools.
//  All tool logic lives in tested packages (M1K3MCPKit); this controller is
//  the MainActor glue: handler closures over speech / avatar / transcription
//  and the Settings-facing lifecycle.
//
//  Client config (Settings shows this): one MCP client at a time (v1 —
//  stateless transport; a new client's initialize rebuilds the session).
//
//      claude mcp add --transport http m1k3 http://127.0.0.1:4242/mcp
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.8 (registry/wire
//  layers test-pinned in M1K3MCPKit; this glue is verify-at-⌘R against a real
//  claude session). Prior: Unknown.
//

import Foundation
import M1K3Avatar
import M1K3Chat // HeadlessAsk + RAGResponding (the ask_m1k3 core)
import M1K3MCPKit
import M1K3Memory // MemoryStore, Memory — the temporal memory graph the new tools expose
import M1K3Voice
import MCP // StatelessHTTPServerTransport (the SDK type the session factory builds)
import Observation
import os // Logger — the canary-tripwire alert channel

@MainActor
@Observable
final class MCPHostController {
    nonisolated static let enabledKey = "mcpServer.enabled"
    nonisolated static let portKey = "mcpServer.port"
    nonisolated static let defaultPort: UInt16 = 4242
    // Leak-tripwire honeypots. Stored in local config only — never in source —
    // so the repo never carries the bait:
    //   `defaults write app.m1k3 canaryTripwire "the passphrase"`
    // Pipe-separate for several (so a honeypot value must not itself contain
    // `|`). Unset → an inert guard. Plaintext in the sandboxed container is an
    // accepted v1 trade-off — the boundary guarded is the MCP OUTPUT surface,
    // not canary storage; migrate to the Keychain if the threat model widens to
    // a compromised container read. The key string lives once, in
    // `CanaryGuard.localConfigKey` — shared with the menu-bar Ask surface.

    /// Loud, PERSISTED alert channel (debug/info aren't kept by the log store).
    /// Logs the match COUNT only — never the canary value, which would re-leak it.
    private nonisolated static let securityLog = Logger(
        subsystem: "app.m1k3", category: "security"
    )

    /// Build the leak guard from local config. Static + nonisolated so the value
    /// (Sendable) can cross into the @Sendable timeout closure without self.
    private nonisolated static func canaryGuard() -> CanaryGuard {
        // Shared with the menu-bar Ask surface — one source of truth for the
        // tripwire parsing + the key (see CanaryGuard.fromLocalConfig).
        CanaryGuard.fromLocalConfig()
    }

    private unowned let env: AppEnvironment
    private var server: LocalMCPHTTPServer?
    /// One ask_m1k3 at a time — a second concurrent generation on the same
    /// MLX provider is undefined territory.
    private var askInFlight = false
    /// Ceiling on a single ask_m1k3 generation. Legit answers run tens of
    /// seconds even on the big brains; past this the generation is cancelled
    /// and the single-flight lock released, so one runaway question can't wedge
    /// the tool for the whole session (test-report F1 — observed ~5-min wedge).
    nonisolated static let askDeadlineSeconds: Double = 120

    private(set) var isRunning = false
    private(set) var statusText: String?

    var port: UInt16 {
        let stored = UserDefaults.standard.integer(forKey: Self.portKey)
        guard stored > 1023, stored <= 65535 else { return Self.defaultPort }
        return UInt16(stored)
    }

    /// Stored (not computed off UserDefaults) so @Observable actually tracks
    /// it — the Settings Toggle re-renders on this, not by coincidence of
    /// isRunning changing nearby.
    private(set) var isEnabled: Bool

    init(environment: AppEnvironment) {
        env = environment
        isEnabled = UserDefaults.standard.bool(forKey: Self.enabledKey)
    }

    func startIfEnabled() {
        guard isEnabled else { return }
        Task { await start() }
    }

    func setEnabled(_ enabled: Bool) {
        isEnabled = enabled
        UserDefaults.standard.set(enabled, forKey: Self.enabledKey)
        Task { enabled ? await start() : await stop() }
    }

    func start() async {
        guard server == nil else { return }
        let registry = MCPToolRegistry(
            makeKnowledgeToolDefinitions(store: env.store)
                + makeVoiceToolDefinitions(handlers: makeVoiceHandlers())
                + makeIntelligenceToolDefinitions(handlers: makeIntelligenceHandlers())
                + memoryToolDefinitions()
        )
        let host = LocalMCPHTTPServer(
            port: port,
            onAbnormalStop: { [weak self] reason in
                Task { @MainActor [weak self] in
                    self?.server = nil
                    self?.isRunning = false
                    self?.statusText = "Stopped: \(reason)"
                }
            }
        ) {
            let transport = StatelessHTTPServerTransport()
            let mcpServer = await makeM1K3Server(registry: registry)
            try await mcpServer.start(transport: transport)
            return (mcpServer, transport)
        }
        do {
            try await host.start()
            server = host
            isRunning = true
            statusText = "Running on 127.0.0.1:\(port)"
        } catch {
            server = nil
            isRunning = false
            statusText = "Couldn’t start: \(error.localizedDescription)"
        }
    }

    func stop() async {
        await server?.stop()
        server = nil
        isRunning = false
        statusText = nil
    }

    // MARK: - Voice tool handlers

    /// The bridge between the Sendable tool closures (called on the server
    /// actor) and the MainActor app surfaces. Guards keep MCP from talking
    /// over a live voice conversation or an in-flight chat turn.
    private func makeVoiceHandlers() -> VoiceToolHandlers {
        VoiceToolHandlers(
            speak: { [weak self] text, emotion, wait in
                guard let self else { throw MCPVoiceError("M1K3 is shutting down") }
                try await self.beginSpeak(text: text, emotion: emotion, wait: wait)
            },
            stopSpeaking: { [weak self] in
                await self?.env.stopSpeaking()
            },
            status: { [weak self] in
                await self?.voiceStatus() ?? VoiceStatus(providerName: "none", tier: "none", isSpeaking: false)
            },
            listen: { [weak self] timeout in
                guard let self else { throw MCPVoiceError("M1K3 is shutting down") }
                return try await self.listenForTranscript(timeout: timeout)
            }
        )
    }

    /// `wait: false` fires speech and returns immediately — env.speak awaits
    /// FULL playback (EffectfulSpeechProvider+Streaming awaitCompletion), and
    /// the SDK Server is serial, so a blocking speak would starve every status
    /// poll for the whole utterance (the report's F3-as-observed).
    private func beginSpeak(text: String, emotion: String?, wait: Bool) async throws {
        guard env.voiceLoop == nil, !env.chat.isResponding else {
            throw MCPVoiceError("M1K3 is in a conversation right now — try again shortly")
        }
        if let emotion {
            env.avatar.setEmotion(AvatarEmotion.from(emotion))
        }
        if wait {
            await env.speak(text)
        } else {
            let environment = env
            Task { @MainActor in await environment.speak(text) }
        }
    }

    private func voiceStatus() async -> VoiceStatus {
        // speaking covers the Kokoro synthesis gap too: the highlight's
        // utterance text is set at utterance start and cleared on end.
        let speaking = await env.speech.isSpeaking() || env.speechHighlight.utteranceText != nil
        return VoiceStatus(
            providerName: env.speech.active.name,
            tier: env.selectedVoiceTier.displayName,
            brain: env.selectedBrain.displayName,
            isSpeaking: speaking,
            inConversation: env.voiceLoop != nil || env.chat.isResponding,
            micInUse: env.voiceLoop != nil || env.isListening || env.isRecording,
            answering: askInFlight
        )
    }

    // MARK: - Intelligence tool handlers

    /// ask_m1k3 runs on a DEDICATED responder instance — collectedSources()
    /// is a draining read, so sharing the chat UI's responder would race a
    /// live turn. Same store/embedder/provider, fresh per server start.
    private func makeIntelligenceHandlers() -> IntelligenceToolHandlers {
        // Fast mode: ask_m1k3 grounds-and-cites for a visiting agent; the think
        // phase is what pushes synthesis past the deadline on these small local
        // brains (test-report follow-up). Deep reasoning over private data is the
        // async-job-model's job (PLAN 2026-06-12), not this blocking call's.
        let askResponder = AppEnvironment.makeAgentResponder(
            store: env.store, embedder: env.embedder, provider: env.provider,
            forcedThinkingMode: .fast
        )
        return IntelligenceToolHandlers(
            ask: { [weak self] question in
                guard let self else { throw MCPVoiceError("M1K3 is shutting down") }
                return try await self.askBrain(question, responder: askResponder)
            },
            remember: { [weak self] title, text in
                guard let self else { throw MCPVoiceError("M1K3 is shutting down") }
                return try await self.rememberText(title: title, text: text)
            }
        )
    }

    private func askBrain(_ question: String, responder: any RAGResponding) async throws -> String {
        guard env.voiceLoop == nil, !env.chat.isResponding else {
            throw MCPVoiceError("M1K3 is in a conversation right now — try again shortly")
        }
        guard !askInFlight else {
            throw MCPVoiceError("M1K3 is already answering an ask_m1k3 call")
        }
        askInFlight = true
        defer { askInFlight = false }
        env.avatar.setActivity(.thinking)
        defer { env.avatar.resetToIdle() }
        let tripwire = Self.canaryGuard()
        do {
            return try await withTimeout(seconds: Self.askDeadlineSeconds) {
                try await HeadlessAsk.answer(
                    question, using: responder, canary: tripwire,
                    onCanaryTrip: { count in
                        Self.securityLog.fault(
                            "canary tripwire fired in ask_m1k3 output: \(count, privacy: .public) honeypot(s) redacted"
                        )
                    }
                )
            }
        } catch is TimeoutError {
            // Deadline hit: the generation is cancelled and the lock is already
            // releasing (defer) — tell the caller honestly rather than hang.
            throw MCPVoiceError(
                "M1K3 took too long to answer (over \(Int(Self.askDeadlineSeconds))s) and stopped. "
                    + "Try a more specific question, or ask again."
            )
        }
    }

    private func rememberText(title: String, text: String) async throws -> String {
        // Same content identity as the distiller: remembering identical text
        // twice (agents retry!) collapses to one row instead of duplicating.
        let result = try await env.ingester.ingest(
            title: title,
            text: text,
            sourceRef: MemoryDistillationCoordinator.factSourceRef(text),
            kind: .memory,
            source: .user
        )
        env.refreshCounts()
        // A genuinely-new memory earns the save earcon; a dedup retry stays silent.
        if !result.wasDeduped {
            env.soundEffects.play(.save)
        }
        // DUAL-WRITE: also drop the fact into the temporal memory graph. Strictly
        // additive (the KnowledgeStore path above is unchanged) and best-effort —
        // a MemoryStore hiccup must never break the proven remember behaviour. The
        // full migration off KnowledgeStore is a later PR; today this seeds the
        // graph so recall_memory/related_memory have something to read.
        if !result.wasDeduped {
            await dualWriteToMemoryGraph(text: text)
        }
        let dedup = result.wasDeduped ? " (already remembered)" : ""
        return "Remembered “\(title)”\(dedup)."
    }

    /// Best-effort write of a remembered fact into the temporal memory graph.
    /// Embeds via the SAME embedder the recall tools query with, so the vectors
    /// share a space. Swallows every error — this is a non-fatal companion to the
    /// KnowledgeStore remember, never a gate on it.
    private func dualWriteToMemoryGraph(text: String) async {
        guard let memoryStore = env.memoryStore else { return }
        do {
            let vector = try await env.embedder.embed(text)
            // Title carried into the fact so a recall surfaces the same handle the
            // visitor used; source marks the provenance half of the consent story.
            let fact = Memory(kind: .note, text: text, source: "mcp:remember")
            try memoryStore.remember(fact, embedding: vector)
        } catch {
            Self.securityLog.error("memory-graph dual-write skipped: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Memory-graph tool handlers

    /// recall_memory / related_memory / memory_stats over the temporal memory
    /// graph. Each closure embeds the query with `env.embedder` (the same space
    /// the dual-write stores into) and reads the live MemoryStore. Inert when no
    /// MemoryStore opened (best-effort, like the dual-write) — recall returns
    /// empty, stats returns zero.
    private func memoryToolDefinitions() -> [MCPToolDefinition] {
        let embedder = env.embedder
        let memoryStore = env.memoryStore
        let handlers = MemoryToolHandlers(
            recall: { query in
                guard let memoryStore else { return [] }
                let vector = try await embedder.embed(query)
                return try memoryStore.recall(query: query, queryVector: vector)
            },
            related: { query in
                guard let memoryStore else { return nil }
                let vector = try await embedder.embed(query)
                guard let seed = try memoryStore.recall(query: query, queryVector: vector, limit: 1).first else {
                    return nil
                }
                return try (seed.memory, memoryStore.related(to: seed.memory.id))
            },
            stats: {
                guard let memoryStore else { return MemoryStatsSummary(liveCount: 0) }
                return try MemoryStatsSummary(liveCount: memoryStore.liveCount())
            }
        )
        return makeMemoryToolDefinitions(handlers: handlers)
    }

    /// Pull-model transcription for the `listen` tool: open the active STT
    /// stream, return once the speaker pauses (~2 s of no new words) or the
    /// timeout passes. Mirrors dictation's accumulator; never touches
    /// dictation's own state.
    private func listenForTranscript(timeout: Double) async throws -> String {
        guard env.voiceLoop == nil, !env.isListening, !env.isRecording else {
            throw MCPVoiceError("M1K3's microphone is already in use")
        }
        guard let provider = env.transcription.activeProvider else {
            throw MCPVoiceError("no speech recognizer is available")
        }
        // Don't open the mic over our own voice (the speak→listen race):
        // wait, bounded, for any in-progress utterance to finish.
        let quietDeadline = Date().addingTimeInterval(20)
        while Date() < quietDeadline,
              await env.speech.isSpeaking() || env.speechHighlight.utteranceText != nil
        {
            try? await Task.sleep(for: .milliseconds(150))
        }
        env.avatar.setActivity(.listening)
        defer { env.avatar.resetToIdle() }

        let stream = try provider.startListening()
        let session = ListenProgress()
        let consumer = Task { @MainActor in
            var accumulator = TranscriptAccumulator()
            for await segment in stream {
                accumulator.ingest(segment)
                session.update(text: accumulator.text)
            }
            return accumulator.text
        }
        // Watchdog: endpoint on silence-after-speech or the hard timeout.
        let watchdog = Task { @MainActor in
            let started = Date()
            while !session.finished {
                try? await Task.sleep(for: .milliseconds(200))
                let waited = Date().timeIntervalSince(started)
                let quiet = Date().timeIntervalSince(session.lastChange)
                if waited >= timeout || (!session.text.isEmpty && quiet >= 2.0) {
                    provider.stopListening() // stream drains → consumer returns
                    break
                }
            }
        }
        // Bound the drain itself: stopListening() SHOULD end the stream, but a
        // misbehaving provider must not hang the MCP call forever (and leak
        // the listening avatar state). AsyncStream iteration honours task
        // cancellation, so cancelling the consumer ends the for-await.
        let drainDeadline = Task { @MainActor in
            try? await Task.sleep(for: .seconds(timeout + 5))
            consumer.cancel()
        }
        let text = await consumer.value
        drainDeadline.cancel()
        session.finished = true
        watchdog.cancel()
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Mutable listen-session state shared between the consumer and watchdog
/// tasks — both run on the MainActor, so plain vars are race-free.
@MainActor
private final class ListenProgress {
    var text = ""
    var lastChange = Date()
    var finished = false

    func update(text newText: String) {
        guard newText != text else { return }
        text = newText
        lastChange = Date()
    }
}
