//
//  M1K3Shortcuts.swift
//  M1K3App
//
//  Siri phrases for the App Intents — what makes "Ask M1K3 …" / "Tell M1K3 to
//  speak" / "Remember this with M1K3" work by voice and appear in Spotlight &
//  the Shortcuts gallery. Free-text parameters aren't embedded in the spoken
//  phrase (Siri prompts for the value via each intent's requestValueDialog).
//
//  App-glue (verify-by-launch). Signed: Kev + claude-opus-4-8, 2026-06-17,
//  Confidence 0.75, Prior: Unknown
//

import AppIntents

struct M1K3Shortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AskM1K3Intent(),
            phrases: [
                "Ask \(.applicationName)",
                "Ask \(.applicationName) a question",
            ],
            shortTitle: "Ask M1K3",
            systemImageName: "brain"
        )
        AppShortcut(
            intent: SpeakWithM1K3Intent(),
            phrases: [
                "Have \(.applicationName) speak",
                "Tell \(.applicationName) to speak",
            ],
            shortTitle: "Speak with M1K3",
            systemImageName: "waveform"
        )
        AppShortcut(
            intent: RememberWithM1K3Intent(),
            phrases: [
                "Remember this with \(.applicationName)",
                "Have \(.applicationName) remember something",
            ],
            shortTitle: "Remember with M1K3",
            systemImageName: "note.text"
        )
    }
}
