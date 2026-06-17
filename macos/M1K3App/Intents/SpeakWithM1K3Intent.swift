//
//  SpeakWithM1K3Intent.swift
//  M1K3App
//
//  "Speak with M1K3" for Siri & Shortcuts — have M1K3 say text aloud in its voice.
//  A text parameter (not "speak the last answer") so a Shortcut can chain
//  Ask → Speak, piping the Ask result straight into this. Sits on the same core as
//  the MCP `speak` tool (AppEnvironment.intelligenceSpeak).
//
//  App-glue (verify-by-launch). Signed: Kev + claude-opus-4-8, 2026-06-17,
//  Confidence 0.78, Prior: Unknown
//

import AppIntents
import M1K3Chat // IntentInput

struct SpeakWithM1K3Intent: AppIntent {
    static let title: LocalizedStringResource = "Speak with M1K3"
    static let description = IntentDescription(
        "Have M1K3 say something aloud in its voice.",
        categoryName: "Voice"
    )
    static let openAppWhenRun = false

    @Parameter(title: "Text", requestValueDialog: "What should M1K3 say?")
    var text: String

    /// When on, the intent waits for playback to finish before completing (useful
    /// when chaining further steps after the speech); off fires and returns.
    @Parameter(title: "Wait for completion", default: false)
    var waitForCompletion: Bool

    static var parameterSummary: some ParameterSummary {
        Summary("Speak \(\.$text) with M1K3") {
            \.$waitForCompletion
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        do {
            let spoken = try IntentInput.speakText(text)
            let env = try await M1K3IntentSupport.environment()
            try await env.intelligenceSpeak(text: spoken, emotion: nil, wait: waitForCompletion)
            return .result()
        } catch {
            throw M1K3IntentSupport.surface(error)
        }
    }
}
