//
//  ToolCallingTypesTests.swift
//  M1K3InferenceTests
//
//  Pins the value semantics of the tool-calling seam types (Phase 12a): how a
//  typed `JSONValue` flattens to text at the execution edge, and how a parsed
//  call exposes its arguments as the `[String: String]` an AgentTool consumes.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

struct JSONValueTests {
    @Test("scalars render to their natural string form")
    func scalarStrings() {
        #expect(JSONValue.string("hi").stringValue == "hi")
        #expect(JSONValue.int(5).stringValue == "5")
        #expect(JSONValue.double(2.5).stringValue == "2.5")
        #expect(JSONValue.bool(true).stringValue == "true")
        #expect(JSONValue.null.stringValue == "")
    }

    @Test("an array flattens to comma-separated scalars")
    func arrayString() {
        let value = JSONValue.array([.string("a"), .int(2), .bool(false)])
        #expect(value.stringValue == "a, 2, false")
    }

    @Test("an object flattens to sorted key=value pairs")
    func objectString() {
        let value = JSONValue.object(["b": .int(2), "a": .string("x")])
        #expect(value.stringValue == "a=x, b=2")
    }
}

struct ParsedToolCallTests {
    @Test("stringArguments flattens typed values for the AgentTool contract")
    func stringArguments() {
        let call = ParsedToolCall(
            name: "search",
            arguments: ["query": .string("seal failure"), "limit": .int(5), "fuzzy": .bool(true)]
        )
        let flattened = call.stringArguments
        #expect(flattened["query"] == "seal failure")
        #expect(flattened["limit"] == "5")
        #expect(flattened["fuzzy"] == "true")
    }
}
