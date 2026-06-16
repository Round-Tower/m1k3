//
//  SMAppServiceLoginItem.swift
//  M1K3Launch
//
//  The real backend: SMAppService.mainApp (macOS 13+). Registering the MAIN app
//  (not a separate helper bundle) means M1K3 itself relaunches at login — which
//  is exactly what we want for an always-resident menu-bar companion. Thin by
//  design: it maps Apple's status enum onto ours and forwards register/unregister.
//  All policy lives in LaunchAtLogin; this is verify-by-launch (needs a signed,
//  installed bundle — SMAppService is inert under `swift test`).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.75, Prior: Unknown

#if canImport(ServiceManagement)
    import Foundation
    import ServiceManagement

    public struct SMAppServiceLoginItem: LoginItemManaging {
        public init() {}

        /// SMAppService is a non-Sendable NSObject, so we never store it;
        /// `.mainApp` is a cheap accessor and registering the main app is the whole
        /// intent — no helper bundle to configure, nothing to inject.
        private var service: SMAppService {
            .mainApp
        }

        public var status: LoginItemStatus {
            switch service.status {
            case .notRegistered: .notRegistered
            case .enabled: .enabled
            case .requiresApproval: .requiresApproval
            case .notFound: .notFound
            @unknown default: .notFound
            }
        }

        public func register() throws {
            try service.register()
        }

        public func unregister() throws {
            try service.unregister()
        }
    }
#endif
