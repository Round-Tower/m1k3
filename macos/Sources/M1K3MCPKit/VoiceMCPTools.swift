//
//  VoiceMCPTools.swift
//  M1K3MCPKit
//
//  Voice tools for the in-app MCP server: speak / stop_speaking /
//  get_voice_status / listen. Handlers are injected closures — the app wires
//  them to its speech provider, avatar, and transcription router on the
//  MainActor; this package stays free of M1K3Voice/M1K3Avatar links (emotion
//  crosses as a plain string, mirroring the app's toolsProvider pattern).
//
//  `listen` is the "pipe transcription to Claude" v1: a pull-model tool — the
//  client asks, M1K3 listens until silence or timeout, the transcript returns
//  as the result. No server-push plumbing needed.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.85 (contract
//  test-pinned with fakes; live handlers are app glue, verify-at-⌘R).
//  Prior: Unknown.
//

import Foundation
import MCP

/// Snapshot of the speech stack for `get_voice_status`.
public struct VoiceStatus: Sendable, Equatable {
    public let providerName: String
    public let tier: String
    public let isSpeaking: Bool

    public init(providerName: String, tier: String, isSpeaking: Bool) {
        self.providerName = providerName
        self.tier = tier
        self.isSpeaking = isSpeaking
    }
}

/// A tool error with a clean, client-facing message.
public struct MCPVoiceError: Error, CustomStringConvertible {
    public let description: String

    public init(_ description: String) {
        self.description = description
    }
}

/// The app-injected implementations behind the voice tools.
public struct VoiceToolHandlers: Sendable {
    public var speak: @Sendable (_ text: String, _ emotion: String?) async throws -> Void
    public var stopSpeaking: @Sendable () async -> Void
    public var status: @Sendable () async -> VoiceStatus
    public var listen: @Sendable (_ timeoutSeconds: Double) async throws -> String

    public init(
        speak: @escaping @Sendable (_ text: String, _ emotion: String?) async throws -> Void,
        stopSpeaking: @escaping @Sendable () async -> Void,
        status: @escaping @Sendable () async -> VoiceStatus,
        listen: @escaping @Sendable (_ timeoutSeconds: Double) async throws -> String
    ) {
        self.speak = speak
        self.stopSpeaking = stopSpeaking
        self.status = status
        self.listen = listen
    }
}

/// Bounds for the `listen` tool's timeout — long enough for a real sentence,
/// short enough that a forgotten call cannot hold the mic open for minutes.
public func clampListenTimeout(_ seconds: Double) -> Double {
    min(max(seconds, 5), 120)
}

public func makeVoiceToolDefinitions(handlers: VoiceToolHandlers) -> [MCPToolDefinition] {
    [
        MCPToolDefinition(
            tool: Tool(
                name: "speak",
                description: "Speak text aloud through M1K3's voice (and animate the avatar). Optional emotion: happy, sad, angry, surprised, love, thinking, excited, sleepy, neutral.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "text": ["type": "string", "description": "what to say"],
                        "emotion": ["type": "string", "description": "avatar emotion while speaking (optional)"],
                    ],
                    "required": ["text"],
                ]
            ),
            handler: { args in
                let text = stringArg(args, "text")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !text.isEmpty else { throw MCPVoiceError("speak requires non-empty text") }
                try await handlers.speak(text, stringArg(args, "emotion"))
                return "Speaking."
            }
        ),
        MCPToolDefinition(
            tool: Tool(
                name: "stop_speaking",
                description: "Stop any in-progress speech immediately.",
                inputSchema: ["type": "object", "properties": [:]]
            ),
            handler: { _ in
                await handlers.stopSpeaking()
                return "Stopped."
            }
        ),
        MCPToolDefinition(
            tool: Tool(
                name: "get_voice_status",
                description: "Current TTS provider, voice tier, and whether M1K3 is speaking.",
                inputSchema: ["type": "object", "properties": [:]]
            ),
            handler: { _ in
                let status = await handlers.status()
                // Stable key order — clients parse this; dictionaries don't sort.
                return """
                {"provider":"\(status.providerName)","tier":"\(status.tier)","speaking":\(status.isSpeaking)}
                """
            }
        ),
        MCPToolDefinition(
            tool: Tool(
                name: "listen",
                description: "Listen on M1K3's microphone and return the transcript once the speaker pauses (or the timeout passes). This is how you hear the user.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "timeout_s": ["type": "number", "description": "max seconds to listen (default 30, clamped 5-120)"],
                    ],
                ]
            ),
            handler: { args in
                let timeout = clampListenTimeout(doubleArg(args, "timeout_s") ?? 30)
                return try await handlers.listen(timeout)
            }
        ),
    ]
}
