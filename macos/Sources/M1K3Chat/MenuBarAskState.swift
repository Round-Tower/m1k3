//
//  MenuBarAskState.swift
//  M1K3Chat
//
//  The pure core of the menu-bar "Ask M1K3" surface: a four-state machine and a
//  driver that runs ONE headless turn and maps the outcome to a terminal state.
//  The driver never throws — errors become `.failed` — so the app-layer
//  controller only has to own the timeout, single-flight, and avatar side-effects
//  (which can't be unit-tested). This split is what makes the ask logic testable
//  with a fake responder, the same way HeadlessAsk itself is.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85, Prior: Unknown

import Foundation

/// What the bar's Ask box is showing.
public enum MenuBarAskState: Equatable, Sendable {
    case idle
    case asking(question: String)
    case answer(question: String, text: String)
    case failed(question: String, message: String)
}

public enum MenuBarAskDriver {
    /// Run one bar ask against a DEDICATED responder (never the chat one —
    /// `collectedSources()` is a draining read) and map to a terminal state.
    /// Total: any thrown error becomes `.failed`; an empty turn becomes an
    /// honest `.answer` (HeadlessAsk floors empties). The app layer wraps this in
    /// a deadline and maps a timeout to `timedOut(question:)`.
    public static func run(
        question: String,
        using responder: any RAGResponding,
        canary: CanaryGuard = .disabled,
        onCanaryTrip: @Sendable (Int) -> Void = { _ in }
    ) async -> MenuBarAskState {
        do {
            let text = try await HeadlessAsk.answer(
                question, using: responder, canary: canary, onCanaryTrip: onCanaryTrip
            )
            return .answer(question: question, text: text)
        } catch {
            return .failed(question: question, message: failureMessage(for: error))
        }
    }

    /// Friendly terminal state when the app-layer deadline fires — points the
    /// user at the fuller answer the chat window gives.
    public static func timedOut(question: String) -> MenuBarAskState {
        .failed(
            question: question,
            message: "That took too long, so I stopped. Try a more specific question — "
                + "or open it in chat for a fuller answer."
        )
    }

    static func failureMessage(for error: any Error) -> String {
        "Something went wrong — \(error.localizedDescription)"
    }
}
