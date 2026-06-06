//
//  SummarizationTests.swift
//  M1K3CallsTests
//
//  Parser (free text → CallSummary) + the two-stage pipeline's error isolation.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Calls
import M1K3Inference
import Testing

// MARK: - Fakes

struct FakeInference: InferenceProvider {
    let name: String
    let isAvailable: Bool
    var response: Result<String, FakeError> = .success("")
    func generate(prompt _: String) async throws -> String {
        try response.get()
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { $0.finish() }
    }
}

enum FakeError: Error { case boom }

// MARK: - Parser

struct CallSummaryParserTests {
    @Test("parses overview, key points, and action items under headers")
    func structured() {
        let summary = CallSummaryParser().parse("""
        Overview: The customer reported a billing issue.
        Key points:
        - Double charged in March
        - Wants a refund
        Action items:
        - Issue the refund
        """)
        #expect(summary.overview == "The customer reported a billing issue.")
        #expect(summary.keyPoints == ["Double charged in March", "Wants a refund"])
        #expect(summary.actionItems == ["Issue the refund"])
    }

    @Test("a response with no headers becomes the overview")
    func noHeaders() {
        let summary = CallSummaryParser().parse("Just a couple of sentences, no structure at all.")
        #expect(summary.overview == "Just a couple of sentences, no structure at all.")
        #expect(summary.keyPoints.isEmpty)
        #expect(summary.actionItems.isEmpty)
    }

    @Test("headers are case-insensitive and numbered bullets are stripped")
    func caseAndNumbering() {
        let summary = CallSummaryParser().parse("""
        OVERVIEW: Quick chat.
        ACTION ITEMS:
        1. Call back tomorrow
        2) Send the form
        """)
        #expect(summary.overview == "Quick chat.")
        #expect(summary.actionItems == ["Call back tomorrow", "Send the form"])
    }
}

// MARK: - Pipeline error isolation

struct SummarizationPipelineTests {
    private func pipeline(quick: FakeInference, deep: FakeInference) -> SummarizationPipeline {
        SummarizationPipeline(quickProvider: quick, deepProvider: deep)
    }

    @Test("both tiers produce output on the happy path")
    func bothSucceed() async {
        let out = await pipeline(
            quick: FakeInference(name: "afm", isAvailable: true, response: .success("The gist.")),
            deep: FakeInference(name: "gemma", isAvailable: true, response: .success("Overview: Deep dive."))
        ).summarize(transcript: "A: hi\nB: hello")
        #expect(out.quick?.overview == "The gist.")
        #expect(out.full?.overview == "Deep dive.")
    }

    @Test("a failing quick tier does not block the deep tier")
    func quickFailsDeepSurvives() async {
        let out = await pipeline(
            quick: FakeInference(name: "afm", isAvailable: true, response: .failure(.boom)),
            deep: FakeInference(name: "gemma", isAvailable: true, response: .success("Overview: Still here."))
        ).summarize(transcript: "x")
        #expect(out.quick == nil)
        #expect(out.full?.overview == "Still here.")
    }

    @Test("an unavailable deep tier still yields the quick summary")
    func deepUnavailableQuickSurvives() async {
        let out = await pipeline(
            quick: FakeInference(name: "afm", isAvailable: true, response: .success("Gist only.")),
            deep: FakeInference(name: "gemma", isAvailable: false)
        ).summarize(transcript: "x")
        #expect(out.quick?.overview == "Gist only.")
        #expect(out.full == nil)
    }
}
