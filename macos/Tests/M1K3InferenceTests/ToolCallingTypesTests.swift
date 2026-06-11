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

    @Test("encodes as plain JSON, not the synthesized enum container")
    func encodesAsPlainJSON() throws {
        // SE-0295 synthesis would wrap every case in a discriminant
        // ({"string":{"_0":"x"}}) — a model echo built from that is garbage.
        let value = JSONValue.object([
            "query": .string("seals"),
            "limit": .int(3),
            "depth": .double(1.5),
            "strict": .bool(true),
            "tags": .array([.string("a")]),
            "none": .null,
        ])
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let json = try #require(String(bytes: encoder.encode(value), encoding: .utf8))
        #expect(json == #"{"depth":1.5,"limit":3,"none":null,"query":"seals","strict":true,"tags":["a"]}"#)
    }

    @Test("decodes plain JSON back into the matching cases")
    func decodesPlainJSON() throws {
        let json = #"{"query":"seals","limit":3,"strict":true,"tags":["a",2.5],"none":null}"#
        let value = try JSONDecoder().decode(JSONValue.self, from: Data(json.utf8))
        #expect(value == .object([
            "query": .string("seals"),
            "limit": .int(3),
            "strict": .bool(true),
            "tags": .array([.string("a"), .double(2.5)]),
            "none": .null,
        ]))
    }

    @Test("JSON 1/0 decode as ints, never coerced to bools")
    func integerZeroOneStayInts() throws {
        // Some model families emit integer-valued booleans; the Bool-first
        // decode order must not swallow them (JSONDecoder doesn't coerce,
        // pinned here so a future decoder swap can't regress it).
        let json = #"{"flag":1,"off":0,"real":true}"#
        let value = try JSONDecoder().decode(JSONValue.self, from: Data(json.utf8))
        #expect(value == .object(["flag": .int(1), "off": .int(0), "real": .bool(true)]))
    }

    @Test("encode → decode round-trips every case")
    func codableRoundTrip() throws {
        let original = JSONValue.object([
            "nested": .object(["k": .array([.int(1), .bool(false), .null])]),
            "text": .string("x"),
        ])
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(JSONValue.self, from: data)
        #expect(decoded == original)
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
