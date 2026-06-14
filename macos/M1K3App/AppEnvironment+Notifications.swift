//
//  AppEnvironment+Notifications.swift
//  M1K3App
//
//  The "your long think is done" local notification: when a turn runs long and
//  you've tabbed away, M1K3 pings you that an answer is ready. Opt-in (default
//  OFF) — the system permission prompt fires only when you flip the Settings
//  toggle, never on launch. Backgrounded-only and threshold-gated by the pure
//  TurnNotificationPolicy (unit-tested in M1K3Chat); this file is the effect.
//
//  Privacy: the notification body is GENERIC — never the answer text. M1K3 is
//  on-device-only, and surfacing a reply on the lock screen / Notification Centre
//  would leak exactly the private content the product exists to keep local.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.8 (policy TDD'd; the
//  UNUserNotificationCenter effect + the permission flow are verify-by-launch).
//  Prior: Unknown

import AppKit
import Foundation
import M1K3Chat
import os
import UserNotifications

private let notifyLog = Logger(subsystem: "dev.murphysig.M1K3", category: "notify")

/// Thin wrapper over UNUserNotificationCenter for the long-think ping. The
/// decision of WHETHER to fire is the pure TurnNotificationPolicy; this is the
/// platform effect, so it's verify-by-launch.
enum TurnNotifier {
    /// Request authorization — called only when the user opts in, so the system
    /// prompt appears on an explicit toggle, never at launch. Returns whether it
    /// was granted (the toggle reflects the real grant).
    static func requestAuthorization() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound])
        } catch {
            notifyLog.error("authorization request failed: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }

    /// Post the generic "answer ready" notification. No content preview by design
    /// (privacy). The center silently drops it if authorization was never granted.
    static func notifyTurnFinished() async {
        let content = UNMutableNotificationContent()
        content.title = "M1K3 has your answer"
        content.body = "Your reply is ready."
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: UUID().uuidString, content: content, trigger: nil
        )
        do {
            try await UNUserNotificationCenter.current().add(request)
        } catch {
            notifyLog.error("could not post notification: \(error.localizedDescription, privacy: .public)")
        }
    }
}

extension AppEnvironment {
    /// UserDefaults flag for the long-think notification (default OFF).
    static var notifyOnLongTurnKey: String {
        "notifications.longTurn"
    }

    var notifyOnLongTurnEnabled: Bool {
        UserDefaults.standard.bool(forKey: Self.notifyOnLongTurnKey)
    }

    /// Flip the opt-in. Turning it ON requests authorization first and only
    /// persists ON if the user granted it — so a denied prompt leaves the toggle
    /// honestly OFF rather than silently inert.
    func setLongTurnNotifications(_ enabled: Bool) async {
        guard enabled else {
            UserDefaults.standard.set(false, forKey: Self.notifyOnLongTurnKey)
            return
        }
        let granted = await TurnNotifier.requestAuthorization()
        UserDefaults.standard.set(granted, forKey: Self.notifyOnLongTurnKey)
    }

    /// Called at the end of a successful turn: ping only if opted in, backgrounded,
    /// and the turn ran long enough (the pure policy decides).
    func maybeNotifyTurnFinished(duration: Duration) async {
        guard TurnNotificationPolicy.shouldNotify(
            turnDuration: duration,
            appActive: NSApplication.shared.isActive,
            enabled: notifyOnLongTurnEnabled
        ) else { return }
        await TurnNotifier.notifyTurnFinished()
    }
}
