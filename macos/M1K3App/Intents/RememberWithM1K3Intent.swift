//
//  RememberWithM1K3Intent.swift
//  M1K3App
//
//  "Remember with M1K3" for Siri & Shortcuts — save a fact to M1K3's private local
//  memory. Title is optional (derived from the text when omitted — voice-first).
//  Sits on the same core as the MCP `remember` tool (AppEnvironment.intelligenceRemember):
//  KnowledgeStore (.memory) ingest + the temporal memory-graph dual-write, tagged
//  `intent:remember`.
//
//  App-glue (verify-by-launch). Signed: Kev + claude-opus-4-8, 2026-06-17,
//  Confidence 0.78, Prior: Unknown
//

import AppIntents
import M1K3Chat // IntentInput

struct RememberWithM1K3Intent: AppIntent {
    static let title: LocalizedStringResource = "Remember with M1K3"
    static let description = IntentDescription(
        "Save a fact to M1K3’s private memory.",
        categoryName: "Memory"
    )
    static let openAppWhenRun = false

    @Parameter(title: "Memory", requestValueDialog: "What should M1K3 remember?")
    var text: String

    /// Optional handle for the memory; when omitted, M1K3 derives one from the text.
    @Parameter(title: "Title")
    var title: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Remember \(\.$text) with M1K3") {
            \.$title
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        do {
            let body = try IntentInput.rememberText(text)
            let memoryTitle = IntentInput.rememberTitle(from: body, explicit: title)
            let env = try await M1K3IntentSupport.environment()
            let confirmation = try await env.intelligenceRemember(
                title: memoryTitle, text: body, provenance: "intent:remember"
            )
            return .result(dialog: IntentDialog(stringLiteral: confirmation))
        } catch {
            throw M1K3IntentSupport.surface(error)
        }
    }
}
