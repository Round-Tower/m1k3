//
//  AgentTool+ToolDefinition.swift
//  M1K3Agent
//
//  Projects an `AgentTool` (the agent's execution-side abstraction) into a
//  dialect-free `ToolDefinition` (the inference-side wire type). This is the
//  one-way bridge that lets the native tool-calling loop hand a model its tool
//  list without the inference layer knowing anything about tool execution.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
import M1K3Inference

public extension AgentTool {
    /// The dialect-free description a `ToolCallingProvider` renders into its
    /// model's native schema. Every parameter is required string-typed today —
    /// matching the single-positional-argument `execute(input:)` contract.
    var toolDefinition: ToolDefinition {
        ToolDefinition(
            name: name,
            description: description,
            parameters: parameters.map {
                ToolParameterDefinition(name: $0.name, description: $0.description)
            }
        )
    }
}
