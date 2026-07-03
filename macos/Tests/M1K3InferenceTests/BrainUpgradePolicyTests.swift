//
//  BrainUpgradePolicyTests.swift
//  M1K3InferenceTests
//
//  The whole background-upgrade brain lives here as a pure transition table, so
//  every race the coordinator could hit has a deterministic answer in `swift
//  test` before any wiring exists. The invariants with teeth:
//    · INVITATION-FIRST: no fetch ever starts without `userAccepted` — the
//      repo's signed promise is "downloads only when you ask"
//    · a staged-but-unconsented Lil (installed at launch) OFFERS a switch,
//      never auto-swaps a brain the user didn't ask for this session
//    · `dismissed` is terminal for nudging — Settings stays the manual path
//    · state is never persisted: `recomputed` rebuilds it from disk facts, so
//      a failure self-heals next launch
//    · SwapSafety: a hot-swap fires only with the app fully idle.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.9. Prior: none (new file).

@testable import M1K3Inference
import Testing

struct BrainUpgradePolicyTests {
    private func next(_ state: BrainUpgradeState, _ event: BrainUpgradeEvent) -> BrainUpgradeState {
        BrainUpgradePolicy.transition(state, on: event)
    }

    // MARK: - Recompute (launch + external brain change): disk facts are the truth

    @Test("non-Mini brain → done, the policy has nothing to sell")
    func recomputeNonMini() {
        #expect(next(.idle, .recomputed(lilInstalled: false, dismissed: false, currentBrain: .big)) == .done)
        #expect(next(.fetching(fraction: 0.4), .recomputed(lilInstalled: true, dismissed: false, currentBrain: .lil)) == .done)
    }

    @Test("dismissed persists across launches → never re-nudge")
    func recomputeDismissed() {
        #expect(next(.idle, .recomputed(lilInstalled: false, dismissed: true, currentBrain: .mini)) == .dismissed)
    }

    @Test("Lil already on disk at launch → staged WITHOUT consent (offer, don't auto-swap)")
    func recomputeInstalled() {
        #expect(
            next(.idle, .recomputed(lilInstalled: true, dismissed: false, currentBrain: .mini))
                == .staged(consented: false)
        )
    }

    @Test("Mini, nothing installed, not dismissed → idle, waiting for the first answer")
    func recomputeFresh() {
        #expect(next(.done, .recomputed(lilInstalled: false, dismissed: false, currentBrain: .mini)) == .idle)
    }

    @Test("dismissed wins over installed — a dismissal is a dismissal even once weights exist")
    func recomputeDismissedWinsOverInstalled() {
        #expect(next(.idle, .recomputed(lilInstalled: true, dismissed: true, currentBrain: .mini)) == .dismissed)
    }

    // MARK: - Offering

    @Test("eligible answer completion from idle → offered")
    func offerAfterAnswer() {
        #expect(next(.idle, .answerCompleted(eligible: true)) == .offered)
    }

    @Test("ineligible answer completion stays idle")
    func noOfferWhenIneligible() {
        #expect(next(.idle, .answerCompleted(eligible: false)) == .idle)
    }

    @Test("answer completion is only an idle→offered edge — it never restarts a park or an active fetch")
    func answerCompletionOnlyFromIdle() {
        for state in [BrainUpgradeState.offered, .fetching(fraction: 0.2), .staged(consented: true), .dismissed, .done, .failed(attempts: 3, transient: false)] {
            #expect(next(state, .answerCompleted(eligible: true)) == state)
        }
    }

    // MARK: - Consent (invitation-first: no fetch without acceptance)

    @Test("accept from offered → fetching starts at zero")
    func acceptStartsFetch() {
        #expect(next(.offered, .userAccepted) == .fetching(fraction: 0))
    }

    @Test("accept from unconsented staged → consented staged (the one-tap switch)")
    func acceptConsentsStaged() {
        #expect(next(.staged(consented: false), .userAccepted) == .staged(consented: true))
    }

    @Test("dismiss from offered or unconsented staged → dismissed (terminal for nudging)")
    func dismissParks() {
        #expect(next(.offered, .userDismissed) == .dismissed)
        #expect(next(.staged(consented: false), .userDismissed) == .dismissed)
    }

    // MARK: - Fetch lifecycle

    @Test("progress only moves an in-flight fetch")
    func progressUpdatesFetch() {
        #expect(next(.fetching(fraction: 0.1), .fetchProgressed(fraction: 0.5)) == .fetching(fraction: 0.5))
        #expect(next(.idle, .fetchProgressed(fraction: 0.5)) == .idle)
    }

    @Test("fetch success → staged with consent carried (the user asked for this download)")
    func fetchSuccessStagesConsented() {
        #expect(next(.fetching(fraction: 0.97), .fetchSucceeded) == .staged(consented: true))
    }

    @Test("fetch failure records the attempt")
    func fetchFailureCounts() {
        #expect(next(.fetching(fraction: 0.3), .fetchFailed(attempts: 1, transient: true)) == .failed(attempts: 1, transient: true))
    }

    @Test("retry re-enters fetching from a failure")
    func retryRefetches() {
        #expect(next(.failed(attempts: 1, transient: true), .retryStarted) == .fetching(fraction: 0))
    }

    @Test("transient failures retry up to 3 attempts per launch; non-transient never")
    func retryBudget() {
        #expect(BrainUpgradePolicy.shouldRetry(attempts: 1, transient: true))
        #expect(BrainUpgradePolicy.shouldRetry(attempts: 2, transient: true))
        #expect(!BrainUpgradePolicy.shouldRetry(attempts: 3, transient: true))
        #expect(!BrainUpgradePolicy.shouldRetry(attempts: 1, transient: false))
    }

    // MARK: - Swap

    @Test("swap completion from consented staged → done")
    func swapCompletes() {
        #expect(next(.staged(consented: true), .swapCompleted) == .done)
    }

    @Test("wantsAutoSwap: only a CONSENTED staged state may hot-swap")
    func autoSwapNeedsConsent() {
        #expect(BrainUpgradePolicy.wantsAutoSwap(.staged(consented: true)))
        #expect(!BrainUpgradePolicy.wantsAutoSwap(.staged(consented: false)))
        #expect(!BrainUpgradePolicy.wantsAutoSwap(.offered))
        #expect(!BrainUpgradePolicy.wantsAutoSwap(.fetching(fraction: 1.0)))
    }

    // MARK: - SwapSafety: the full truth table, all-idle or nothing

    @Test("swap is safe only when every activity flag is quiet")
    func swapSafetyTruthTable() {
        #expect(SwapSafety.canSwap(isResponding: false, isVoiceModeActive: false, isListening: false, modelLoadActive: false))
        #expect(!SwapSafety.canSwap(isResponding: true, isVoiceModeActive: false, isListening: false, modelLoadActive: false))
        #expect(!SwapSafety.canSwap(isResponding: false, isVoiceModeActive: true, isListening: false, modelLoadActive: false))
        #expect(!SwapSafety.canSwap(isResponding: false, isVoiceModeActive: false, isListening: true, modelLoadActive: false))
        #expect(!SwapSafety.canSwap(isResponding: false, isVoiceModeActive: false, isListening: false, modelLoadActive: true))
    }

    // MARK: - Offer eligibility (the gate on even SHOWING the nudge)

    private func eligible(
        currentBrain: BrainTier = .mini,
        lilInstalled: Bool = false,
        completedAnswers: Int = 1,
        isResponding: Bool = false,
        freeDiskBytes: Int64 = 50_000_000_000,
        networkExpensive: Bool = false,
        networkConstrained: Bool = false
    ) -> Bool {
        OfferEligibility.isEligible(
            currentBrain: currentBrain,
            lilInstalled: lilInstalled,
            completedAnswers: completedAnswers,
            isResponding: isResponding,
            freeDiskBytes: freeDiskBytes,
            requiredBytes: OfferEligibility.lilDownloadBytes,
            networkExpensive: networkExpensive,
            networkConstrained: networkConstrained
        )
    }

    @Test("the happy path is eligible")
    func eligibleHappyPath() {
        #expect(eligible())
    }

    @Test("never before the first completed answer — the whoa lands un-stolen")
    func requiresFirstAnswer() {
        #expect(!eligible(completedAnswers: 0))
    }

    @Test("never mid-turn")
    func neverMidTurn() {
        #expect(!eligible(isResponding: true))
    }

    @Test("only upsells from Mini, and never re-sells installed weights")
    func onlyFromMini() {
        #expect(!eligible(currentBrain: .lil))
        #expect(!eligible(currentBrain: .big))
        #expect(!eligible(lilInstalled: true))
    }

    @Test("disk floor: ceil(bytes × 1.2) + 1GB headroom, boundary-exact")
    func diskFloorBoundary() {
        let required = OfferEligibility.lilDownloadBytes
        let floor = Int64((Double(required) * 1.2).rounded(.up)) + 1_000_000_000
        #expect(eligible(freeDiskBytes: floor))
        #expect(!eligible(freeDiskBytes: floor - 1))
    }

    @Test("metered or constrained networks are never offered a 2.3GB pull")
    func meteredNetworksExcluded() {
        #expect(!eligible(networkExpensive: true))
        #expect(!eligible(networkConstrained: true))
    }
}
