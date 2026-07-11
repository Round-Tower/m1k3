//
//  AgentToolDefinitionTests.swift
//  M1K3AgentTests
//
//  Pins the AgentTool → ToolDefinition projection (Phase 12a): the dialect-free
//  description a native tool-calling provider renders into its model's schema.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Agent
import M1K3Inference
import Testing

private struct SampleTool: AgentTool {
    let name = "search"
    let description = "searches knowledge"
    let parameters = [ToolParameter(name: "query", description: "the search query")]
    func execute(input _: [String: String]) async throws -> ToolResult {
        ToolResult(output: "")
    }
}

private struct OptionalParamTool: AgentTool {
    let name = "paged"
    let description = "fetches with an optional offset"
    let parameters = [
        ToolParameter(name: "title", description: "the title"),
        ToolParameter(name: "offset", description: "resume offset (optional)", isRequired: false),
    ]
    func execute(input _: [String: String]) async throws -> ToolResult {
        ToolResult(output: "")
    }
}

struct AgentToolDefinitionTests {
    @Test("projects name, description, and parameters into a ToolDefinition")
    func projection() {
        let definition = SampleTool().toolDefinition
        #expect(definition.name == "search")
        #expect(definition.description == "searches knowledge")
        #expect(definition.parameters.count == 1)
        #expect(definition.parameters.first?.name == "query")
        #expect(definition.parameters.first?.description == "the search query")
        #expect(definition.parameters.first?.isRequired == true)
        #expect(definition.parameters.first?.type == "string")
    }

    @Test("an optional ToolParameter projects isRequired: false into the wire type")
    func optionalParameterProjection() {
        let definition = OptionalParamTool().toolDefinition
        #expect(definition.parameters.map(\.name) == ["title", "offset"])
        #expect(definition.parameters.map(\.isRequired) == [true, false])
    }
}
