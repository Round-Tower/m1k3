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

    @Test("a system message maps to the system role (the persona's seat)")
    func systemRole() {
        let message = MLXToolMapping.chatMessage(from: .system("You are M1K3."))
        #expect(message.role == .system)
        #expect(message.content == "You are M1K3.")
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
        // Plain JSON scalar, NOT the synthesized enum container
        // {"string":{"_0":"seals"}} — a .json-dialect model reads this echo.
        #expect(text.contains("\"arguments\":{\"query\":\"seals\"}"))
    }

    @Test("json call text is valid RFC-8259 JSON for mixed argument types")
    func jsonCallTextIsRealJSON() throws {
        let call = ParsedToolCall(name: "lookup", arguments: [
            "query": .string("seals"),
            "limit": .int(3),
            "strict": .bool(true),
            "tags": .array([.string("a"), .string("b")]),
            "filters": .object(["depth": .double(1.5), "none": .null]),
        ])
        let text = MLXToolMapping.callText(call, format: .json)
        let payload = text
            .replacingOccurrences(of: "<tool_call>", with: "")
            .replacingOccurrences(of: "</tool_call>", with: "")
        let object = try JSONSerialization.jsonObject(with: Data(payload.utf8)) as? [String: Any]
        let parsed = try #require(object)
        #expect(parsed["name"] as? String == "lookup")
        let arguments = try #require(parsed["arguments"] as? [String: Any])
        #expect(arguments["query"] as? String == "seals")
        #expect(arguments["limit"] as? Int == 3)
        #expect(arguments["strict"] as? Bool == true)
        #expect(arguments["tags"] as? [String] == ["a", "b"])
        let filters = try #require(arguments["filters"] as? [String: Any])
        #expect(filters["depth"] as? Double == 1.5)
        #expect(filters["none"] is NSNull)
    }

    @Test("the xmlFunction dialect renders <function=>/<parameter=> (Qwen3.5)")
    func xmlFunctionCallText() {
        let call = ParsedToolCall(name: "search", arguments: ["query": .string("seals"), "limit": .int(3)])
        let text = MLXToolMapping.callText(call, format: .xmlFunction)
        #expect(text.hasPrefix("<function=search>"))
        #expect(text.hasSuffix("</function>"))
        #expect(text.contains("<parameter=query>seals</parameter>"))
        #expect(text.contains("<parameter=limit>3</parameter>"))
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

    @Test("gemma-4 resolves BEFORE the generic gemma arm — no parser at 3.31.3, ReAct floor")
    func gemma4ResolvesNil() {
        let gemma4 = ModelConfiguration(id: "mlx-community/gemma-4-e4b-it-4bit")
        let gemma3n = ModelConfiguration(id: "mlx-community/gemma-3n-E4B-it-lm-4bit")
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: gemma4) == nil)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: gemma3n) == .gemma)
    }

    @Test("qwen3.5 resolves to xmlFunction BEFORE the generic qwen arm")
    func qwen35ResolvesXMLFunction() {
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "mlx-community/Qwen3.5-2B-4bit")) == .xmlFunction)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "mlx-community/Qwen3.5-9B-4bit")) == .xmlFunction)
        #expect(MLXGemmaProvider.resolveToolCallFormat(for: .init(id: "mlx-community/Qwen3-1.7B-4bit")) == .json)
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

struct MLXThinkTemplateTests {
    @Test("qwen3.5 templates pre-open <think> — the output needs a synthetic opener")
    func qwen35PreOpensThink() {
        #expect(MLXGemmaProvider.templatePreOpensThink(for: .init(id: "mlx-community/Qwen3.5-2B-4bit")))
        #expect(MLXGemmaProvider.templatePreOpensThink(for: .init(id: "mlx-community/Qwen3.5-9B-4bit")))
        #expect(MLXGemmaProvider.templatePreOpensThink(for: .init(id: "mlx-community/qwen3_5-instruct")))
    }

    @Test("qwen3 and non-reasoning families do NOT pre-open think")
    func othersDoNot() {
        #expect(!MLXGemmaProvider.templatePreOpensThink(for: .init(id: "mlx-community/Qwen3-1.7B-4bit")))
        #expect(!MLXGemmaProvider.templatePreOpensThink(for: .init(id: "mlx-community/gemma-4-e4b-it-4bit")))
        #expect(!MLXGemmaProvider.templatePreOpensThink(for: .init(id: "meta/Llama-3.2-1B")))
    }

    @Test("the synthetic opener is added once and never duplicated")
    func normalisePrefix() {
        #expect(MLXGemmaProvider.normaliseThinkPrefix("plan</think>answer", preOpened: true)
            == "<think>plan</think>answer")
        #expect(MLXGemmaProvider.normaliseThinkPrefix("<think>plan</think>answer", preOpened: true)
            == "<think>plan</think>answer")
        #expect(MLXGemmaProvider.normaliseThinkPrefix("plain answer", preOpened: false)
            == "plain answer")
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
