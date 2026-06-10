//
//  ChatFailureMessageTests.swift
//  M1K3ChatTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Chat
import Testing

private struct GenericError: Error, LocalizedError {
    var errorDescription: String? {
        "something broke"
    }
}

struct ChatFailureMessageTests {
    @Test("a transient network error becomes a calm, actionable message")
    func transient() {
        let message = ChatFailureMessage.userFacing(for: URLError(.timedOut))
        #expect(message.contains("still downloading"))
        #expect(message.contains("try again"))
    }

    @Test("a non-network error keeps the generic apology with its description")
    func generic() {
        let message = ChatFailureMessage.userFacing(for: GenericError())
        #expect(message.contains("Sorry"))
        #expect(message.contains("something broke"))
    }
}
