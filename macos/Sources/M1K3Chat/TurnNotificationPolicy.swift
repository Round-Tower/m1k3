//
//  TurnNotificationPolicy.swift
//  M1K3Chat
//
//  Decides whether a finished agent turn should raise a local notification — the
//  "your long think is done" ping for when you've tabbed away mid-answer. Pure so
//  it's unit-tested away from UNUserNotificationCenter (the app target wires the
//  effect + the permission). Three gates, all must hold: the user opted in, the
//  app is in the BACKGROUND (a foreground turn is already on-screen — a ping would
//  be noise), and the turn ran long enough that a heads-up is worth an interruption.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.85 (threshold is a
//  by-feel default, tune at ⌘R), Prior: Unknown

import Foundation

public enum TurnNotificationPolicy {
    /// A turn must run at least this long to be "worth notifying about". Below it
    /// the user hasn't really walked away — no ping for a quick reply.
    public static let longTurnThreshold: Duration = .seconds(8)

    public static func shouldNotify(
        turnDuration: Duration, appActive: Bool, enabled: Bool
    ) -> Bool {
        guard enabled, !appActive else { return false }
        return turnDuration >= longTurnThreshold
    }
}
