//
//  AppEnvironment+BrainUpgrade.swift
//  M1K3App
//
//  Coordinator for the invitation-first background brain upgrade: "That was
//  Mini, my quickest brain. Lil is sharper — I'll grab it in the background
//  while we keep talking." All decisions live in the PURE, tested layer
//  (BrainUpgradePolicy / OfferEligibility / SwapSafety, M1K3Inference); this
//  file only gathers live inputs and runs side effects.
//
//  The hard rules, enforced here:
//    · the fetch NEVER touches `modelLoad` or the live provider — it's a
//      disk-only BrainWeightsFetcher snapshot; the readiness gate must not
//      hide a working chat behind an invisible download
//    · no download without the user's tap on the nudge (the repo's signed
//      promise: "downloads only when you ask")
//    · the hot-swap fires only between turns with everything idle
//      (SwapSafety), through the ordinary `selectBrain(.lil)` path
//    · `selectBrain`/the picker route cancel the fetch synchronously first —
//      one writer to the Hub cache; the partial snapshot resumes elsewhere
//    · a fetch "success" is only believed after `isBrainDownloaded` confirms
//      weights on disk (the downloader falls back to a local partial on
//      offline/auth instead of throwing).
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.8 (side-effect glue
//  over 25 pinned transitions; download/swap behaviour is verify-by-launch —
//  named: nudge beat, Settings %, kill-mid-download resume, swap-at-idle toast).
//  Prior: none (new file).

import Foundation
import M1K3Inference
import M1K3MLX
import Network
import os

extension AppEnvironment {
    /// Persisted "Maybe later" — never re-nudge; Settings stays the manual path.
    nonisolated static let brainUpgradeDismissedKey = "brainUpgrade.dismissed"

    private static let upgradeLog = Logger(subsystem: "app.m1k3", category: "mlx-load")

    // MARK: - State recompute (launch + any explicit brain change)

    /// Rebuild the machine from disk facts. State is never persisted, so this
    /// is the self-healing entry: quit mid-download → partial resumes on the
    /// next accepted fetch; failure → clean slate next launch.
    func recomputeBrainUpgradeState() {
        brainUpgrade = BrainUpgradePolicy.transition(brainUpgrade, on: .recomputed(
            lilInstalled: isBrainDownloaded(.lil),
            dismissed: UserDefaults.standard.bool(forKey: Self.brainUpgradeDismissedKey),
            currentBrain: selectedBrain
        ))
    }

    // MARK: - The between-turns beat

    /// Called after each successful typed answer: maybe raise the offer, or
    /// complete a consented staged swap now that the app is idle.
    func evaluateBrainUpgradeAfterAnswer() {
        startNetworkMonitorIfNeeded()
        let path = brainUpgradeNetworkPath
        let eligible = OfferEligibility.isEligible(
            currentBrain: selectedBrain,
            lilInstalled: isBrainDownloaded(.lil),
            completedAnswers: 1, // this call IS an answer completion
            isResponding: chat.isResponding,
            freeDiskBytes: Self.freeDiskBytes(),
            requiredBytes: OfferEligibility.lilDownloadBytes,
            networkExpensive: path?.isExpensive ?? false,
            networkConstrained: path?.isConstrained ?? false
        )
        brainUpgrade = BrainUpgradePolicy.transition(brainUpgrade, on: .answerCompleted(eligible: eligible))
        attemptAutoSwapIfStaged()
    }

    // MARK: - User actions (the nudge card's two buttons)

    /// "Fetch Lil" — consent captured; the download starts now, invisibly.
    /// From an unconsented staged state this is the one-tap switch instead.
    func acceptBrainUpgrade() {
        let before = brainUpgrade
        brainUpgrade = BrainUpgradePolicy.transition(before, on: .userAccepted)
        switch brainUpgrade {
        case .fetching:
            runBrainUpgradeFetch(attempt: 1)
        case .staged(consented: true):
            attemptAutoSwapIfStaged()
        default:
            break
        }
    }

    /// "Maybe later" — terminal for nudging (persisted). Deliberate cost:
    /// a dismissed user stays on Mini until they visit Settings/the toolbar.
    func dismissBrainUpgrade() {
        UserDefaults.standard.set(true, forKey: Self.brainUpgradeDismissedKey)
        brainUpgrade = BrainUpgradePolicy.transition(brainUpgrade, on: .userDismissed)
    }

    // MARK: - Fetch

    private func runBrainUpgradeFetch(attempt: Int) {
        guard let modelID = BrainTier.lil.mlxModelID else { return }
        Self.upgradeLog.notice("background upgrade fetch start (attempt \(attempt)): \(modelID, privacy: .public)")
        // Strong-bind immediately in each closure (weak captures are mutable,
        // so a NESTED @Sendable closure referencing one trips Swift 6 strict
        // concurrency — the "captured var self" error).
        let progress: @Sendable (Double) -> Void = { [weak self] fraction in
            guard let self else { return }
            Task { @MainActor in
                self.brainUpgrade = BrainUpgradePolicy.transition(
                    self.brainUpgrade, on: .fetchProgressed(fraction: fraction)
                )
            }
        }
        // Inherits @MainActor (unstructured Task on an actor-isolated method):
        // the awaits suspend rather than block, and every state write below is
        // main-actor by construction. Holding self for the download's lifetime
        // is fine — AppEnvironment lives as long as the app.
        brainUpgradeFetchTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await BrainWeightsFetcher().fetch(modelID: modelID, progress: progress)
                guard !Task.isCancelled else { return }
                // Believe disk, not the downloader's return (it falls back
                // to a local partial on offline/auth instead of throwing).
                if self.isBrainDownloaded(.lil) {
                    Self.upgradeLog.notice("background upgrade staged: \(modelID, privacy: .public)")
                    self.brainUpgrade = BrainUpgradePolicy.transition(self.brainUpgrade, on: .fetchSucceeded)
                    self.attemptAutoSwapIfStaged()
                } else {
                    self.handleFetchFailure(attempt: attempt, transient: true,
                                            reason: "download incomplete (offline?)")
                }
            } catch is CancellationError {
                // Cancelled by selectBrain/the picker — state already recomputed.
            } catch {
                guard !Task.isCancelled else { return }
                let transient = (error as NSError).domain == NSURLErrorDomain
                self.handleFetchFailure(
                    attempt: attempt, transient: transient,
                    reason: error.localizedDescription
                )
            }
        }
    }

    private func handleFetchFailure(attempt: Int, transient: Bool, reason: String) {
        Self.upgradeLog.error("background upgrade fetch failed (attempt \(attempt)): \(reason, privacy: .public)")
        brainUpgrade = BrainUpgradePolicy.transition(
            brainUpgrade, on: .fetchFailed(attempts: attempt, transient: transient)
        )
        // Silent to the chat (the whoa is not interrupted by our retries);
        // Settings' brain section shows the failed row. Backoff then retry.
        guard BrainUpgradePolicy.shouldRetry(attempts: attempt, transient: transient) else { return }
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(Double(attempt) * 15))
            guard let self, case .failed = brainUpgrade else { return }
            brainUpgrade = BrainUpgradePolicy.transition(brainUpgrade, on: .retryStarted)
            runBrainUpgradeFetch(attempt: attempt + 1)
        }
    }

    /// Synchronous cancel — called FIRST by selectBrain / routeToOnboarding-
    /// BrainPicker so there's exactly one writer to the Hub cache dir. The
    /// partial snapshot stays on disk and resumes wherever it's next wanted.
    func cancelBrainUpgradeFetch() {
        brainUpgradeFetchTask?.cancel()
        brainUpgradeFetchTask = nil
    }

    // MARK: - Swap

    /// The payoff: consent was captured at the pitch, so once staged the swap
    /// fires automatically — but ONLY with the app fully idle (SwapSafety),
    /// between turns, through the ordinary selectBrain path (short from-disk
    /// load; the toast says what happened).
    func attemptAutoSwapIfStaged() {
        guard BrainUpgradePolicy.wantsAutoSwap(brainUpgrade) else { return }
        guard SwapSafety.canSwap(
            isResponding: chat.isResponding,
            isVoiceModeActive: isVoiceModeActive,
            isListening: isListening,
            modelLoadActive: modelLoad.isActive
        ) else { return } // stay staged; the next answer completion retries
        Self.upgradeLog.notice("background upgrade: swapping to lil at idle")
        selectBrain(.lil) // its hook recomputes brainUpgrade → .done
        showBrainUpgradeNotice("Lil's awake — switched you over.")
    }

    private func showBrainUpgradeNotice(_ text: String) {
        brainUpgradeNotice = text
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(6))
            if self?.brainUpgradeNotice == text { self?.brainUpgradeNotice = nil }
        }
    }

    // MARK: - Inputs

    private func startNetworkMonitorIfNeeded() {
        guard brainUpgradePathMonitor == nil else { return }
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            Task { @MainActor in self.brainUpgradeNetworkPath = path }
        }
        monitor.start(queue: DispatchQueue(label: "app.m1k3.brain-upgrade-path"))
        brainUpgradePathMonitor = monitor
    }

    private nonisolated static func freeDiskBytes() -> Int64 {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSHomeDirectory())
        let values = try? url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return values?.volumeAvailableCapacityForImportantUsage ?? 0
    }
}
