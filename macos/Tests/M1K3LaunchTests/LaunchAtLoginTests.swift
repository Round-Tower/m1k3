//
//  LaunchAtLoginTests.swift
//  M1K3LaunchTests
//
//  Drives the launch-at-login policy against a fake login item. The real
//  SMAppService can't run here (no registered bundle under `swift test`), so the
//  reconcile/idempotency/error behaviour — the part that would otherwise throw
//  "already registered" or swallow a failure — is what these pin.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Launch
import Testing

/// In-memory stand-in for SMAppService: counts calls and flips its own status so
/// the controller's reconcile logic is observable. `@MainActor` to satisfy the
/// (now main-actor) `LoginItemManaging` protocol — which also makes it Sendable,
/// so no `@unchecked` needed.
@MainActor
private final class FakeLoginItem: LoginItemManaging {
    var status: LoginItemStatus
    var registerCount = 0
    var unregisterCount = 0
    var registerError: (any Error)?
    var unregisterError: (any Error)?

    init(status: LoginItemStatus = .notRegistered) {
        self.status = status
    }

    func register() throws {
        registerCount += 1
        if let registerError { throw registerError }
        status = .enabled
    }

    func unregister() throws {
        unregisterCount += 1
        if let unregisterError { throw unregisterError }
        status = .notRegistered
    }
}

private struct StubError: Error, LocalizedError {
    let errorDescription: String?
}

@MainActor
struct LaunchAtLoginTests {
    @Test("isEnabled mirrors the underlying status at init")
    func mirrorsStatus() {
        #expect(LaunchAtLogin(item: FakeLoginItem(status: .enabled)).isEnabled)
        #expect(!LaunchAtLogin(item: FakeLoginItem(status: .notRegistered)).isEnabled)
    }

    @Test("enabling from notRegistered registers and flips to enabled")
    func enableRegisters() {
        let fake = FakeLoginItem(status: .notRegistered)
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(true)

        #expect(fake.registerCount == 1)
        #expect(sut.isEnabled)
        #expect(sut.lastError == nil)
    }

    @Test("enabling when already enabled is a no-op (no double register)")
    func enableIdempotent() {
        let fake = FakeLoginItem(status: .enabled)
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(true)

        #expect(fake.registerCount == 0)
        #expect(sut.isEnabled)
    }

    @Test("disabling from enabled unregisters and flips off")
    func disableUnregisters() {
        let fake = FakeLoginItem(status: .enabled)
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(false)

        #expect(fake.unregisterCount == 1)
        #expect(!sut.isEnabled)
    }

    @Test("disabling when already off is a no-op")
    func disableIdempotent() {
        let fake = FakeLoginItem(status: .notRegistered)
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(false)

        #expect(fake.unregisterCount == 0)
        #expect(!sut.isEnabled)
    }

    @Test("a register failure surfaces a readable message and leaves status intact")
    func registerFailureSurfaced() {
        let fake = FakeLoginItem(status: .notRegistered)
        fake.registerError = StubError(errorDescription: "Operation not permitted")
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(true)

        #expect(sut.lastError == "Operation not permitted")
        #expect(!sut.isEnabled)
    }

    @Test("an unregister failure surfaces a readable message")
    func unregisterFailureSurfaced() {
        let fake = FakeLoginItem(status: .enabled)
        fake.unregisterError = StubError(errorDescription: "Access denied")
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(false)

        #expect(sut.lastError == "Access denied")
        // Unregister failed, so the service is still enabled — status reflects it.
        #expect(sut.isEnabled)
    }

    @Test("notFound is treated as not-enabled: enabling attempts a register")
    func notFoundEnableRegisters() {
        // .notFound means the backing service vanished; the safest reconcile is
        // to (re)register when the user asks for enabled rather than assume on.
        let fake = FakeLoginItem(status: .notFound)
        let sut = LaunchAtLogin(item: fake)
        #expect(!sut.isEnabled)

        sut.setEnabled(true)

        #expect(fake.registerCount == 1)
        #expect(sut.isEnabled)
    }

    @Test("notFound disable attempts an unregister and surfaces any failure")
    func notFoundDisableSurfacesError() {
        // .notFound != .notRegistered, so disabling tries to unregister; if the
        // vanished service errors, the user must see it, not a silent swallow.
        let fake = FakeLoginItem(status: .notFound)
        fake.unregisterError = StubError(errorDescription: "no such service")
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(false)

        #expect(fake.unregisterCount == 1)
        #expect(sut.lastError == "no such service")
    }

    @Test("requiresApproval is reflected for the System Settings prompt path")
    func requiresApprovalReflected() {
        let sut = LaunchAtLogin(item: FakeLoginItem(status: .requiresApproval))
        #expect(sut.requiresApproval)
        #expect(!sut.isEnabled)
    }

    @Test("enabling while requiresApproval is a no-op (already registered, awaiting user)")
    func enableWhileRequiresApprovalIsNoOp() {
        let fake = FakeLoginItem(status: .requiresApproval)
        let sut = LaunchAtLogin(item: fake)

        sut.setEnabled(true)

        #expect(fake.registerCount == 0)
        #expect(sut.lastError == nil)
        #expect(sut.requiresApproval)
    }

    @Test("refresh re-reads the live service status")
    func refreshReReads() {
        let fake = FakeLoginItem(status: .notRegistered)
        let sut = LaunchAtLogin(item: fake)
        #expect(!sut.isEnabled)

        fake.status = .enabled
        sut.refresh()

        #expect(sut.isEnabled)
    }

    @Test("a successful setEnabled clears a prior error")
    func clearsPriorError() {
        let fake = FakeLoginItem(status: .notRegistered)
        fake.registerError = StubError(errorDescription: "boom")
        let sut = LaunchAtLogin(item: fake)
        sut.setEnabled(true)
        #expect(sut.lastError == "boom")

        fake.registerError = nil
        sut.setEnabled(true)

        #expect(sut.lastError == nil)
        #expect(sut.isEnabled)
        // The first attempt threw BEFORE mutating status (still .notRegistered),
        // so the retry registers again — making the sequence explicit.
        #expect(fake.registerCount == 2)
    }
}
