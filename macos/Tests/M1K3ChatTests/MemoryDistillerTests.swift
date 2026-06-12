//
//  MemoryDistillerTests.swift
//  M1K3ChatTests
//
//  The distillation core, pure parts: prompt assembly (caps, role labels),
//  the FACT:-line parser (think-strip, NONE, caps, garbage tolerance), the
//  normalizer that feeds the dedupe hash, and the AFM-first/fallback
//  provider orchestration with fakes.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Inference
import Testing

// MARK: - Fakes

private struct FakeProvider: InferenceProvider, @unchecked Sendable {
    let name: String
    let isAvailable: Bool
    let result: Result<String, Error>

    func generate(prompt _: String) async throws -> String {
        try result.get()
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { c in
            if case let .success(text) = result { c.yield(text) }
            c.finish()
        }
    }
}

private struct Boom: Error {}

private func turns(_ pairs: [(String, String)]) -> [ChatTurn] {
    pairs.flatMap { [ChatTurn(role: .user, text: $0.0), ChatTurn(role: .assistant, text: $0.1)] }
}

// MARK: - Prompt

struct MemoryDistillationPromptTests {
    @Test("prompt carries role-labelled turns and the FACT: contract")
    func promptShape() {
        let prompt = MemoryDistillationPrompt.build(turns: turns([
            ("My sister is called Aoife", "Lovely name — noted."),
        ]))
        #expect(prompt.contains("USER: My sister is called Aoife"))
        #expect(prompt.contains("ASSISTANT: Lovely name — noted."))
        #expect(prompt.contains("FACT: "))
        #expect(prompt.contains("NONE"))
    }

    @Test("per-turn text is capped")
    func perTurnCap() {
        let long = String(repeating: "x", count: 2000)
        let prompt = MemoryDistillationPrompt.build(turns: [ChatTurn(role: .user, text: long)])
        #expect(!prompt.contains(String(repeating: "x", count: 500)))
    }

    @Test("a monster transcript is capped from the FRONT — newest turns survive")
    func totalCapKeepsNewest() {
        let many = (0 ..< 100).map { ("question \($0) " + String(repeating: "p", count: 300), "answer \($0)") }
        let prompt = MemoryDistillationPrompt.build(turns: turns(many))
        #expect(prompt.count < 8000)
        #expect(prompt.contains("question 99"))
        #expect(!prompt.contains("question 0 "))
    }
}

// MARK: - Parser

struct MemoryFactParserTests {
    @Test("FACT: lines parse, chatter is ignored")
    func parsesFactLines() {
        let raw = """
        Sure! Here is what I found:
        FACT: Kev's sister is called Aoife.
        Some rambling between lines.
        FACT: The user prefers metric units.
        Hope that helps!
        """
        #expect(MemoryFactParser.parse(raw) == [
            "Kev's sister is called Aoife.",
            "The user prefers metric units.",
        ])
    }

    @Test("NONE means no facts — cleanly")
    func noneIsEmpty() {
        #expect(MemoryFactParser.parse("NONE").isEmpty)
        #expect(MemoryFactParser.parse("  NONE\n").isEmpty)
    }

    @Test("a <think> block is stripped before parsing")
    func thinkStripped() {
        let raw = "<think>FACT: not a real fact, just thinking</think>FACT: Kev lives in Cork."
        #expect(MemoryFactParser.parse(raw) == ["Kev lives in Cork."])
    }

    @Test("caps: at most 8 facts, overlong and tiny facts dropped, duplicates collapsed")
    func capsAndFilters() {
        let tenFacts = (1 ... 10).map { "FACT: The user fact number \($0) is durable." }
        #expect(MemoryFactParser.parse(tenFacts.joined(separator: "\n")).count == 8)

        let overlong = "FACT: " + String(repeating: "very long fact ", count: 30)
        #expect(MemoryFactParser.parse(overlong).isEmpty)

        #expect(MemoryFactParser.parse("FACT: yes").isEmpty)

        let dupes = "FACT: Kev lives in Cork.\nFACT: Kev lives in Cork."
        #expect(MemoryFactParser.parse(dupes).count == 1)
    }

    @Test("pure garbage parses to empty, never throws")
    func garbageIsEmpty() {
        #expect(MemoryFactParser.parse("I am a helpful assistant and I think...").isEmpty)
        #expect(MemoryFactParser.parse("").isEmpty)
    }
}

// MARK: - Normalizer

struct MemoryFactNormalizerTests {
    @Test("case, whitespace and trailing punctuation collapse")
    func normalizes() {
        #expect(
            MemoryFactNormalizer.normalize("Kev's  sister is Aoife.")
                == MemoryFactNormalizer.normalize("kev's sister is aoife")
        )
        #expect(
            MemoryFactNormalizer.normalize("Prefers metric units!!!")
                == "prefers metric units"
        )
    }
}

// MARK: - Provider orchestration

struct ProviderMemoryDistillerTests {
    @Test("primary's facts win; fallback never consulted")
    func primaryWins() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .success("FACT: Kev lives in Cork.")),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .failure(Boom()))
        )
        let facts = try await distiller.distill(turns: turns([("I live in Cork", "Noted")]))
        #expect(facts == ["Kev lives in Cork."])
    }

    @Test("a clean NONE from the primary is a valid empty — no fall-through")
    func noneDoesNotFallThrough() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .success("NONE")),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("FACT: phantom fact here."))
        )
        let facts = try await distiller.distill(turns: turns([("hey", "hi")]))
        #expect(facts.isEmpty)
    }

    @Test("primary unavailable → fallback runs")
    func unavailablePrimaryFallsBack() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: false, result: .failure(Boom())),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("FACT: From the fallback model."))
        )
        let facts = try await distiller.distill(turns: turns([("a", "b")]))
        #expect(facts == ["From the fallback model."])
    }

    @Test("primary throws → fallback runs")
    func throwingPrimaryFallsBack() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .failure(Boom())),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("FACT: From the fallback model."))
        )
        let facts = try await distiller.distill(turns: turns([("a", "b")]))
        #expect(facts == ["From the fallback model."])
    }

    @Test("primary garbage (not NONE, no facts) → fallback runs")
    func garbagePrimaryFallsBack() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .success("As an AI I cannot…")),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("FACT: From the fallback model."))
        )
        let facts = try await distiller.distill(turns: turns([("a", "b")]))
        #expect(facts == ["From the fallback model."])
    }

    @Test("both throw → the error propagates (caller keeps the watermark and retries)")
    func bothThrowPropagates() async {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .failure(Boom())),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .failure(Boom()))
        )
        await #expect(throws: (any Error).self) {
            try await distiller.distill(turns: turns([("a", "b")]))
        }
    }

    @Test("both garbage → empty, NOT a throw (retrying garbage forever would loop)")
    func bothGarbageIsEmpty() async throws {
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .success("blah")),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("more blah"))
        )
        let facts = try await distiller.distill(turns: turns([("a", "b")]))
        #expect(facts.isEmpty)
    }

    @Test("cancellation rethrows immediately — the fallback is never consulted")
    func cancellationNeverFallsBack() async {
        // A cancelled distillation must not burn a fallback generation; the
        // caller's watermark stays put and the next exit retries.
        let distiller = ProviderMemoryDistiller(
            primary: FakeProvider(name: "afm", isAvailable: true, result: .failure(CancellationError())),
            fallback: FakeProvider(name: "mlx", isAvailable: true, result: .success("FACT: must never appear."))
        )
        await #expect(throws: CancellationError.self) {
            try await distiller.distill(turns: turns([("a", "b")]))
        }
    }
}
