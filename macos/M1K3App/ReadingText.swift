//
//  ReadingText.swift
//  M1K3App
//
//  Renders a string in the active ReadingMode (default / serif / OpenDyslexic /
//  bionic). One place owns the font + spacing + bionic-emphasis decisions so the
//  chat bubbles and the Settings preview stay identical. Always selectable.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8, Prior: Unknown

import M1K3Chat
import SwiftUI

struct ReadingText: View {
    let text: String
    /// Force a specific mode (Settings previews); otherwise follow the saved choice.
    private let forcedMode: ReadingMode?

    @AppStorage(ReadingMode.storageKey) private var savedModeRaw = ReadingMode.standard.rawValue

    init(_ text: String, mode: ReadingMode? = nil) {
        self.text = text
        forcedMode = mode
    }

    private var mode: ReadingMode {
        forcedMode ?? ReadingMode(rawValue: savedModeRaw) ?? .standard
    }

    var body: some View {
        styled
            .textSelection(.enabled)
            .lineSpacing(mode == .dyslexia ? 6 : 2)
            .tracking(mode == .dyslexia ? 0.5 : 0)
    }

    @ViewBuilder
    private var styled: some View {
        switch mode {
        case .standard:
            Text(text).font(.body)
        case .serif:
            Text(text).font(.system(.body, design: .serif))
        case .dyslexia:
            Text(text).font(.dyslexic(15))
        case .bionic:
            Text(bionic(text)).font(.body)
        }
    }

    /// Build an AttributedString that bolds the leading characters of each word.
    private func bionic(_ source: String) -> AttributedString {
        var result = AttributedString()
        for run in BionicTextFormatter.runs(source) {
            if !run.bold.isEmpty {
                var bold = AttributedString(String(run.bold))
                bold.font = .body.bold()
                result += bold
            }
            if !run.rest.isEmpty {
                var rest = AttributedString(String(run.rest))
                rest.font = .body
                result += rest
            }
        }
        return result
    }
}
