//
//  LaunchAtLogin.swift
//  M1K3Launch
//
//  The observable the Settings toggle binds to. The whole point is the policy:
//  reconcile the DESIRED state against the service's ACTUAL state so a stale
//  toggle can't double-register (SMAppService throws if you register twice), and
//  surface a failure as a readable message instead of an exception the UI can't
//  catch. `status` is a stored, observation-tracked mirror so SwiftUI re-renders
//  after a mutation; reads for the decision go straight to the live service.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.8, Prior: Unknown

import Foundation

@MainActor
@Observable
public final class LaunchAtLogin {
    private let item: any LoginItemManaging

    /// Last-known service status, mirrored for SwiftUI observation.
    public private(set) var status: LoginItemStatus

    /// Human-readable failure from the last `setEnabled`, or nil on success.
    public private(set) var lastError: String?

    public init(item: any LoginItemManaging) {
        self.item = item
        status = item.status
    }

    /// True when the app is a registered, enabled login item.
    public var isEnabled: Bool {
        status == .enabled
    }

    /// True when registered but awaiting the user's approval in System Settings.
    public var requiresApproval: Bool {
        status == .requiresApproval
    }

    /// Re-read the live service status (e.g. after returning from System Settings).
    public func refresh() {
        status = item.status
    }

    /// Reconcile the desired state. Idempotent: registering when already enabled
    /// (or unregistering when already off) is a no-op, so the toggle never throws
    /// the "already registered" error. Failures land in `lastError`; `status` is
    /// always re-read so the UI reflects whatever actually happened.
    public func setEnabled(_ enabled: Bool) {
        lastError = nil
        do {
            if enabled {
                // .requiresApproval already means registered-and-pending-the-user
                // in System Settings — re-registering throws "already registered",
                // so a user re-opening Settings before approving doesn't see a
                // spurious error. Both "on" states short-circuit.
                if item.status != .enabled, item.status != .requiresApproval {
                    try item.register()
                }
            } else {
                if item.status != .notRegistered { try item.unregister() }
            }
        } catch {
            lastError = error.localizedDescription
        }
        refresh()
    }
}
