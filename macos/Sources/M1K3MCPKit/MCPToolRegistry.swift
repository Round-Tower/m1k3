//
//  MCPToolRegistry.swift
//  M1K3MCPKit
//
//  The injectable tool registry every M1K3 MCP transport serves from: the
//  stdio executable registers knowledge tools, the in-app HTTP server adds
//  voice tools on top. Handlers return plain text; errors become isError
//  results, never protocol failures.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure dispatch,
//  test-pinned). Prior: Unknown.
//

import Foundation
import MCP
import os

/// One tool: its MCP declaration plus the handler that runs it.
public struct MCPToolDefinition: Sendable {
    public let tool: Tool
    public let handler: @Sendable ([String: Value]?) async throws -> String

    public init(tool: Tool, handler: @escaping @Sendable ([String: Value]?) async throws -> String) {
        self.tool = tool
        self.handler = handler
    }
}

/// Name-keyed dispatch over a fixed set of tool definitions.
public struct MCPToolRegistry: Sendable {
    private static let log = Logger(subsystem: "app.m1k3", category: "mcp")
    private let definitions: [MCPToolDefinition]

    public init(_ definitions: [MCPToolDefinition]) {
        self.definitions = definitions
    }

    /// Declarations in registration order (ListTools).
    public var tools: [Tool] {
        definitions.map(\.tool)
    }

    /// Run a tool by name. Unknown names and handler throws both surface as
    /// `isError` results — observations for the client, never crashes.
    public func call(name: String, arguments: [String: Value]?) async -> CallTool.Result {
        guard let definition = definitions.first(where: { $0.tool.name == name }) else {
            return CallTool.Result(
                content: [.text(text: "Unknown tool: \(name)", annotations: nil, _meta: nil)],
                isError: true
            )
        }
        do {
            let text = try await definition.handler(arguments)
            return CallTool.Result(content: [.text(text: text, annotations: nil, _meta: nil)])
        } catch {
            // The client gets isError, but Kev debugging from the app's own logs
            // saw nothing for a thrown tool — record the tool NAME (never args: PII).
            Self.log.error("tool \"\(name, privacy: .public)\" threw: \(error.localizedDescription, privacy: .public)")
            return CallTool.Result(
                content: [.text(text: "Error: \(error)", annotations: nil, _meta: nil)],
                isError: true
            )
        }
    }
}

// MARK: - Argument helpers

public func stringArg(_ args: [String: Value]?, _ key: String) -> String? {
    if case let .string(value)? = args?[key] { return value }
    return nil
}

public func intArg(_ args: [String: Value]?, _ key: String) -> Int? {
    if case let .int(value)? = args?[key] { return value }
    return nil
}

public func doubleArg(_ args: [String: Value]?, _ key: String) -> Double? {
    switch args?[key] {
    case let .double(value): return value
    case let .int(value): return Double(value) // JSON clients send 30 for 30.0
    default: return nil
    }
}

public func boolArg(_ args: [String: Value]?, _ key: String) -> Bool? {
    if case let .bool(value)? = args?[key] { return value }
    return nil
}
