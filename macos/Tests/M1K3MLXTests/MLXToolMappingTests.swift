//
//  MLXToolMappingTests.swift
//  M1K3MLXTests
//
//  Fast tier (no Metal, no model): the PURE mappers that bridge the dialect-free
//  ToolCallingProvider seam (M1K3Inference) to mlx-swift-lm's native types —
//  AgentTool definitions → ToolSpec JSON schema, the [ToolMessage] transcript →
//  [Chat.Message] (with Gemma call-text echoed on the assistant turn + tool-role
//  results), and the library's parsed ToolCall → our ParsedToolCall. The actual
//  generation loop (continueToolTurn) is verify-by-launch — MLX needs the app
//  bundle's metallib — so these mappers carry the regression coverage.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Inference
@testable import M1K3MLX
import MLXLMCommon
import Testing
import Tokenizers

struct MLXToolSpecTests {
    @Test("a ToolDefinition becomes a function-typed JSON schema")
    func toolSpecShape() {
        let definition = ToolDefinition(
            name: "web_search",
            description: "Search the web.",
            parameters: [ToolParameterDefinition(name: "query", description: "the query")]
        )
        let spec = MLXToolMapping.toolSpec(from: definition)

        #expect(spec["type"] as? String == "function")
        let function = try? #require(spec["function"] as? [String: any Sendable])
        #expect(function?["name"] as? String == "web_search")
        #expect(function?["description"] as? String == "Search the web.")

        let parameters = function?["parameters"] as? [String: any Sendable]
        #expect(parameters?["type"] as? String == "object")
        #expect((parameters?["required"] as? [String]) == ["query"])
        let properties = parameters?["properties"] as? [String: any Sendable]
        let queryProp = properties?["query"] as? [String: any Sendable]
        #expect(queryProp?["type"] as? String == "string")
        #expect(queryProp?["description"] as? String == "the query")
    }
}

struct MLXChatMessageTests {
    @Test("a user message maps to the user role")
    func userRole() {
        let message = MLXToolMapping.chatMessage(from: .user("hello"))
        #expect(message.role == .user)
        #expect(message.content == "hello")
    }

    @Test("a tool result maps to the tool role")
    func toolRole() {
        let message = MLXToolMapping.chatMessage(from: .toolResult(name: "search", output: "found it"))
        #expect(message.role == .tool)
        #expect(message.content == "found it")
    }

    @Test("an assistant tool call echoes back as Gemma call-text")
    func assistantCallEcho() {
        let call = ParsedToolCall(name: "search", arguments: ["query": .string("seals")])
        let message = MLXToolMapping.chatMessage(from: .assistant(text: nil, toolCalls: [call]))
        #expect(message.role == .assistant)
        #expect(message.content.contains("call:search"))
        #expect(message.content.contains("<escape>seals<escape>"))
    }

    @Test("an assistant plain-text turn keeps its text")
    func assistantText() {
        let message = MLXToolMapping.chatMessage(from: .assistant(text: "done", toolCalls: []))
        #expect(message.role == .assistant)
        #expect(message.content == "done")
    }
}

struct MLXGemmaCallTextTests {
    @Test("string args are escape-wrapped; numeric args are raw")
    func callTextEncoding() {
        let call = ParsedToolCall(name: "lookup", arguments: ["q": .string("x y"), "n": .int(3)])
        let text = MLXToolMapping.gemmaCallText(call)
        #expect(text.contains("call:lookup"))
        #expect(text.contains("q:<escape>x y<escape>"))
        #expect(text.contains("n:3"))
    }

    @Test("the json dialect renders a <tool_call> block (Qwen/Llama)")
    func jsonCallText() {
        let call = ParsedToolCall(name: "search", arguments: ["query": .string("seals")])
        let text = MLXToolMapping.callText(call, format: .json)
        #expect(text.contains("<tool_call>"))
        #expect(text.contains("</tool_call>"))
        #expect(text.contains("\"name\":\"search\""))
        #expect(text.contains("seals"))
    }
}

struct MLXToolFormatResolutionTests {
    @Test("resolves the native dialect by model family")
    func familyResolution() {
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "mlx-community/gemma-3-1b")) == .gemma)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "mlx-community/Qwen3-1.7B")) == .json)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "meta/Llama-3.2-1B")) == .json)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "some/unknown-model")) == nil)
    }

    @Test("an explicit configuration format wins over the family heuristic")
    func explicitWins() {
        var config = ModelConfiguration(id: "some/unknown-model")
        config.toolCallFormat = .json
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: config) == .json)
    }

    @Test("supportsToolCalls reflects whether the family is recognised")
    func capabilityFlag() {
        #expect(MLXGemmaProvider(modelID: "mlx-community/gemma-3-1b-it-qat-4bit").supportsToolCalls)
        #expect(MLXGemmaProvider(modelID: "mlx-community/Qwen3-1.7B-4bit").supportsToolCalls)
        #expect(!MLXGemmaProvider(modelID: "some/unknown-model").supportsToolCalls)
    }
}

struct MLXParsedToolCallTests {
    @Test("a library ToolCall maps to our ParsedToolCall with typed args")
    func parsedToolCallMapping() {
        let libraryCall = MLXLMCommon.ToolCall(
            function: .init(name: "search", arguments: ["query": "seal", "limit": 5, "fuzzy": true])
        )
        let parsed = MLXToolMapping.parsedToolCall(from: libraryCall)

        #expect(parsed.name == "search")
        #expect(parsed.arguments["query"] == .string("seal"))
        #expect(parsed.arguments["limit"] == .int(5))
        #expect(parsed.arguments["fuzzy"] == .bool(true))
    }

    @Test("nested JSON values round-trip through the conversion")
    func nestedJSONValue() {
        let libraryCall = MLXLMCommon.ToolCall(
            function: .init(name: "f", arguments: ["tags": ["a", "b"]])
        )
        let parsed = MLXToolMapping.parsedToolCall(from: libraryCall)
        #expect(parsed.arguments["tags"] == .array([.string("a"), .string("b")]))
    }
}
