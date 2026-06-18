//
//  AskM1K3Intent.swift
//  M1K3App
//
//  "Ask M1K3" for Siri & Shortcuts — one grounded, cited answer from the local
//  knowledge base, with NO chat window. Quiet by design (openAppWhenRun = false):
//  M1K3 answers in the background since it's normally already resident. Sits on the
//  exact same core as the MCP `ask_m1k3` tool (AppEnvironment.intelligenceAsk) —
//  single-flight, 120s deadline, shared canary tripwire.
//
//  App-glue (verify-by-launch). Signed: Kev + claude-opus-4-8, 2026-06-17,
//  Confidence 0.78, Prior: Unknown
//

import AppIntents
import M1K3Chat // IntentInput

struct AskM1K3Intent: AppIntent {
    static let title: LocalizedStringResource = "Ask M1K3"
    static let description = IntentDescription(
        "Ask M1K3 a question and get a grounded answer from your local knowledge.",
        categoryName: "Intelligence"
    )
    /// Quiet: answer in the background without bringing the app forward.
    static let openAppWhenRun = false

    @Parameter(title: "Question", requestValueDialog: "What would you like to ask M1K3?")
    var question: String

    static var parameterSummary: some ParameterSummary {
        Summary("Ask M1K3 \(\.$question)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> & ProvidesDialog {
        do {
            let cleaned = try IntentInput.askQuestion(question)
            let env = try await M1K3IntentSupport.environment()
            let answer = try await env.intelligenceAsk(cleaned)
            return .result(value: answer, dialog: IntentDialog(stringLiteral: answer))
        } catch {
            throw M1K3IntentSupport.surface(error)
        }
    }
}
