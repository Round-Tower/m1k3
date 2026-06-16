//
//  LoginItem.swift
//  M1K3Launch
//
//  The "launch M1K3 at login" seam. SMAppService (the real backend) needs a
//  registered .app bundle to do anything, so it can't run under `swift test`.
//  This protocol + a status enum decouple the policy (idempotent enable/disable,
//  error surfacing) from the runtime — the policy is unit-tested against a fake,
//  the concrete SMAppServiceLoginItem is the thin adapter (verify-by-launch).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.8, Prior: Unknown

import Foundation

/// A decoupled mirror of `SMAppService.Status` so the controller is testable
/// without the ServiceManagement runtime (which requires a registered bundle).
public enum LoginItemStatus: Sendable, Equatable {
    /// Not a login item.
    case notRegistered
    /// Registered and will launch at login.
    case enabled
    /// Registered but the user must approve it in System Settings › General ›
    /// Login Items (macOS surfaces a prompt the first time).
    case requiresApproval
    /// The service backing the login item couldn't be found.
    case notFound
}

/// Manages whether the app launches at login. One method per intent; the
/// idempotency/error policy lives in `LaunchAtLogin`, not here, so a fake can
/// drive the policy tests deterministically.
///
/// `@MainActor`: the SMAppService backend talks to the service-management daemon
/// and is main-thread-only. Annotating the protocol type-enforces what was prose
/// — the sole caller (`LaunchAtLogin`) is already `@MainActor`, so no call site
/// changes.
@MainActor
public protocol LoginItemManaging: Sendable {
    var status: LoginItemStatus { get }
    func register() throws
    func unregister() throws
}
