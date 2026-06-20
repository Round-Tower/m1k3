//
//  OpenLinkMCPTool.swift
//  M1K3MCPKit
//
//  `open_link`: a visiting agent surfaces a web page into M1K3's on-screen review
//  panel — "here, look at this" — beside the conversation. Local-first: the page
//  opens in M1K3 on the user's Mac, nothing is pushed anywhere. The handler is an
//  app-injected closure (the VoiceToolHandlers pattern), so this package stays
//  free of any UI / WebKit link.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation
import MCP

/// The app-injected implementation behind `open_link`.
public struct OpenLinkToolHandlers: Sendable {
    /// Open a URL in the review panel. Returns a human confirmation, or throws if
    /// the string isn't a web link M1K3 can open.
    public var open: @Sendable (_ url: String) async throws -> String

    public init(open: @escaping @Sendable (_ url: String) async throws -> String) {
        self.open = open
    }
}

public func makeOpenLinkToolDefinitions(handlers: OpenLinkToolHandlers) -> [MCPToolDefinition] {
    [
        MCPToolDefinition(
            tool: Tool(
                name: "open_link",
                description: "Open a web link in M1K3's review panel on the user's screen, beside the "
                    + "conversation, so they can see the page. Local-first — the page opens in M1K3, "
                    + "nothing is sent anywhere. Argument: an http(s) URL.",
                inputSchema: [
                    "type": "object",
                    "properties": [
                        "url": ["type": "string", "description": "the http(s) URL to open"],
                    ],
                    "required": ["url"],
                ]
            ),
            handler: { args in
                let url = stringArg(args, "url")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !url.isEmpty else { throw MCPVoiceError("open_link requires a url") }
                return try await handlers.open(url)
            }
        ),
    ]
}
