//
//  BrainUpgradePolicy.swift
//  M1K3Inference
//
//  The background brain-upgrade state machine — pure, so every race the app
//  coordinator can hit has a deterministic, tested answer. The coordinator
//  (AppEnvironment+BrainUpgrade) owns the side effects: it evaluates
//  `OfferEligibility` with live inputs, runs the actual download, checks
//  `SwapSafety` before calling `selectBrain(.lil)`, and persists exactly one
//  thing — the dismissal flag. State itself is NEVER persisted: `recomputed`
//  rebuilds it from disk facts at launch (and after any external brain change),
//  so a bad state self-heals next run.
//
//  Consent model (invitation-first — the repo's signed promise is "downloads
//  only when you ask"): no fetch starts without `userAccepted` on the offer,
//  and only a CONSENTED staged state may hot-swap. Lil found already on disk
//  at launch stages UNconsented: it offers a one-tap switch, it never swaps a
//  brain the user didn't ask for this session.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

import Foundation

/// Where the upgrade journey stands. `Equatable` so the transition table is
/// directly assertable; `fraction` rides along for the Settings/pill progress.
public enum BrainUpgradeState: Sendable, Equatable {
    /// Nothing offered yet — waiting for an eligible moment.
    case idle
    /// The nudge card is (or may be) showing.
    case offered
    /// User accepted; weights downloading in the background.
    case fetching(fraction: Double)
    /// Weights on disk. `consented` = the user asked for them this session
    /// (accepted the fetch, or tapped the one-tap switch offer).
    case staged(consented: Bool)
    /// Terminal for this launch: swapped, or there's nothing to upgrade.
    case done
    /// User said "Maybe later" — terminal for nudging; Settings stays the path.
    case dismissed
    /// A fetch attempt failed. `transient` decides whether retry is on the table.
    case failed(attempts: Int, transient: Bool)
}

/// Everything that can happen to the upgrade journey.
public enum BrainUpgradeEvent: Sendable, Equatable {
    /// Launch (or external brain change): rebuild state from disk facts.
    case recomputed(lilInstalled: Bool, dismissed: Bool, currentBrain: BrainTier)
    /// A chat answer finished; the coordinator already ran `OfferEligibility`.
    case answerCompleted(eligible: Bool)
    case userAccepted
    case userDismissed
    case fetchProgressed(fraction: Double)
    case fetchSucceeded
    case fetchFailed(attempts: Int, transient: Bool)
    case retryStarted
    case swapCompleted
}

public enum BrainUpgradePolicy {
    public static func transition(_ state: BrainUpgradeState, on event: BrainUpgradeEvent) -> BrainUpgradeState {
        switch event {
        case let .recomputed(lilInstalled, dismissed, currentBrain):
            // Disk facts outrank whatever the machine was doing. Order matters:
            // a non-Mini brain means there's nothing to sell; a dismissal is a
            // dismissal even once weights exist (a Settings-side download must
            // not resurrect the nudge).
            if currentBrain != .mini { return .done }
            if dismissed { return .dismissed }
            if lilInstalled { return .staged(consented: false) }
            return .idle

        case let .answerCompleted(eligible):
            // Strictly the idle→offered edge: an answer completion must never
            // restart a park, re-offer mid-fetch, or resurrect a failure.
            guard state == .idle, eligible else { return state }
            return .offered

        case .userAccepted:
            switch state {
            case .offered: return .fetching(fraction: 0)
            case .staged(consented: false): return .staged(consented: true)
            default: return state
            }

        case .userDismissed:
            switch state {
            case .offered, .staged(consented: false): return .dismissed
            default: return state
            }

        case let .fetchProgressed(fraction):
            guard case .fetching = state else { return state }
            return .fetching(fraction: fraction)

        case .fetchSucceeded:
            guard case .fetching = state else { return state }
            // The user asked for this download — consent carries into the swap.
            return .staged(consented: true)

        case let .fetchFailed(attempts, transient):
            guard case .fetching = state else { return state }
            return .failed(attempts: attempts, transient: transient)

        case .retryStarted:
            guard case .failed = state else { return state }
            return .fetching(fraction: 0)

        case .swapCompleted:
            guard case .staged(consented: true) = state else { return state }
            return .done
        }
    }

    /// Transient failures earn up to 3 attempts per launch; non-transient
    /// (404 / gated repo / disk full) never auto-retry — Settings shows why.
    public static func shouldRetry(attempts: Int, transient: Bool) -> Bool {
        transient && attempts < 3
    }

    /// Only a CONSENTED staged state may hot-swap — consent was captured at the
    /// pitch ("I'll grab it in the background"), the swap is the payoff.
    public static func wantsAutoSwap(_ state: BrainUpgradeState) -> Bool {
        state == .staged(consented: true)
    }
}

/// The hot-swap gate: `selectBrain` repoints the live provider, so it may only
/// fire with the app fully idle — never mid-answer, never with the voice loop
/// or a listen open, never while another model load is in flight.
public enum SwapSafety {
    public static func canSwap(
        isResponding: Bool,
        isVoiceModeActive: Bool,
        isListening: Bool,
        modelLoadActive: Bool
    ) -> Bool {
        !isResponding && !isVoiceModeActive && !isListening && !modelLoadActive
    }
}

/// The gate on even SHOWING the upgrade nudge. All-or-nothing: an ineligible
/// moment simply stays quiet (the machine remains `idle`, a later answer
/// re-evaluates), so a temporarily-full disk or a hotspot never burns the
/// one-shot offer.
public enum OfferEligibility {
    /// Lil's approximate download, in bytes (BrainTier.approxDownloadMB).
    public static var lilDownloadBytes: Int64 {
        Int64(BrainTier.lil.approxDownloadMB ?? 0) * 1_000_000
    }

    /// Disk floor: the download itself plus 20% working margin, plus a flat
    /// 1GB so the upgrade never becomes the thing that fills the disk.
    public static func requiredFreeDiskBytes(for downloadBytes: Int64) -> Int64 {
        Int64((Double(downloadBytes) * 1.2).rounded(.up)) + 1_000_000_000
    }

    public static func isEligible(
        currentBrain: BrainTier,
        lilInstalled: Bool,
        completedAnswers: Int,
        isResponding: Bool,
        freeDiskBytes: Int64,
        requiredBytes: Int64,
        networkExpensive: Bool,
        networkConstrained: Bool
    ) -> Bool {
        currentBrain == .mini
            && !lilInstalled
            && completedAnswers >= 1
            && !isResponding
            && freeDiskBytes >= requiredFreeDiskBytes(for: requiredBytes)
            && !networkExpensive
            && !networkConstrained
    }
}
