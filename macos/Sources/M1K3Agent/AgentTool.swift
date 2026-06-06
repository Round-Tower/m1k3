//
//  AgentTool.swift
//  M1K3Agent
//
//  A capability the LocalAgent can invoke during its ReAct loop. M1K3's MVP
//  tools wrap the knowledge layer (search_knowledge, query_graph, get_document,
//  list_documents); they're injected, so the agent stays decoupled from the
//  store and testable against fakes.
//
//  Ported from the internal call-pipeline project's AgentTool / ToolParameter / ToolResult.
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9,
//  Prior: the internal call-pipeline project AgentTool + ToolResult (Kev)

import Foundation

/// A named input a tool accepts. The first parameter receives the agent's
/// positional argument (`ToolName(value)`); richer multi-arg parsing is a
/// future enhancement.
public struct ToolParameter: Sendable, Equatable {
    public let name: String
    public let description: String

    public init(name: String, description: String) {
        self.name = name
        self.description = description
    }
}

/// The text a tool returns to the agent as an observation. By convention an
/// output beginning with "Error:" is surfaced as a failed-but-recoverable step.
public struct ToolResult: Sendable, Equatable {
    public let output: String

    public init(output: String) {
        self.output = output
    }
}

public protocol AgentTool: Sendable {
    /// Identifier the agent uses in `ACTION: name(arg)`.
    var name: String { get }
    /// One-line description shown to the model so it knows when to call this.
    var description: String { get }
    /// Declared parameters. The first receives the positional argument.
    var parameters: [ToolParameter] { get }
    /// Execute against a name→value input map and return an observation.
    func execute(input: [String: String]) async throws -> ToolResult
}
