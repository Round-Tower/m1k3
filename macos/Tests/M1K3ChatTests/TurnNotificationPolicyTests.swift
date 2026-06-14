import Foundation
import M1K3Chat
import Testing

/// Pins the three-gate policy behind the "long think finished" notification:
/// opted-in AND backgrounded AND long-enough. The app wires the UNUserNotification
/// effect; this is the whole decision.
struct TurnNotificationPolicyTests {
    @Test("notifies for a long turn while backgrounded, when enabled")
    func notifiesWhenAllConditionsMet() {
        #expect(TurnNotificationPolicy.shouldNotify(
            turnDuration: .seconds(20), appActive: false, enabled: true
        ))
    }

    @Test("never notifies when the user hasn't opted in")
    func silentWhenDisabled() {
        #expect(!TurnNotificationPolicy.shouldNotify(
            turnDuration: .seconds(60), appActive: false, enabled: false
        ))
    }

    @Test("never notifies while the app is active — the turn is already on-screen")
    func silentWhenForeground() {
        #expect(!TurnNotificationPolicy.shouldNotify(
            turnDuration: .seconds(60), appActive: true, enabled: true
        ))
    }

    @Test("a quick turn doesn't earn an interruption")
    func silentForShortTurn() {
        #expect(!TurnNotificationPolicy.shouldNotify(
            turnDuration: .seconds(2), appActive: false, enabled: true
        ))
    }

    @Test("the threshold boundary is inclusive")
    func boundaryInclusive() {
        #expect(TurnNotificationPolicy.shouldNotify(
            turnDuration: TurnNotificationPolicy.longTurnThreshold, appActive: false, enabled: true
        ))
    }
}
