//
//  StartupVisibilityTests.swift
//  M1K3LaunchTests
//
//  Pins the menu-bar-only policy: which stance each preference maps to, and what
//  each stance implies for the Dock icon + launch window. The AppKit mapping is
//  verify-by-launch; this is the decision table.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85, Prior: Unknown

@testable import M1K3Launch
import Testing

struct StartupVisibilityTests {
    @Test("menuBarOnly:false is the default dock-and-window stance")
    func defaultStance() {
        let visibility = StartupVisibility(menuBarOnly: false)
        #expect(visibility == .dockAndWindow)
        #expect(!visibility.hidesDockIcon)
        #expect(!visibility.suppressesLaunchWindow)
    }

    @Test("menuBarOnly:true hides the Dock icon and suppresses the launch window")
    func menuBarOnlyStance() {
        let visibility = StartupVisibility(menuBarOnly: true)
        #expect(visibility == .menuBarOnly)
        #expect(visibility.hidesDockIcon)
        #expect(visibility.suppressesLaunchWindow)
    }
}
