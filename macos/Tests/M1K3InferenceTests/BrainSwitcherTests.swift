//
//  BrainSwitcherTests.swift
//  M1K3InferenceTests
//
//  The pure rows + indicator label behind the chat toolbar's brain hot-swap.
//  The Menu wiring is verify-by-launch; this is the part with a right answer —
//  especially the "never lie during a download" invariant (selectBrain flips the
//  active tier BEFORE the weights finish, so the label must compose with load).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.85. Prior: this file.

@testable import M1K3Inference
import Testing

struct BrainSwitcherTests {
    private func rows(
        active: BrainTier,
        downloaded: Set<BrainTier> = [],
        locked: Set<BrainTier> = []
    ) -> [BrainSwitchRow] {
        BrainSwitcher.rows(
            active: active,
            isDownloaded: { downloaded.contains($0) },
            isLocked: { locked.contains($0) }
        )
    }

    private func row(_ tier: BrainTier, in rows: [BrainSwitchRow]) -> BrainSwitchRow {
        rows.first { $0.tier == tier }!
    }

    // MARK: - rows

    @Test("one row per tier, in BrainTier.allCases order")
    func ordering() {
        #expect(rows(active: .mini).map(\.tier) == BrainTier.allCases)
    }

    @Test("only the active tier is marked active")
    func activeMarking() {
        let result = rows(active: .lil, downloaded: [.lil])
        #expect(result.filter(\.isActive).map(\.tier) == [.lil])
    }

    @Test("a locked tier is present (not dropped) and never a download prompt")
    func lockedTierShownNotDropped() {
        // No live tier carries a memory floor since Huge retired (2026-07-02),
        // so the "· needs NNGB+" hint branch is dormant — it revives when
        // gemma-4-12B lands as Big with a floor. The lock mechanism itself is
        // injected, so it stays pinned here.
        let big = row(.big, in: rows(active: .mini, locked: [.big]))
        #expect(big.isLocked)
        #expect(!big.needsDownload) // a locked row is never a download CTA
        #expect(big.menuTitle == "Big") // floor-less locked row: plain name
    }

    @Test("Mini never needs a download and never locks")
    func miniAlwaysReady() {
        let mini = row(.mini, in: rows(active: .mini))
        #expect(!mini.needsDownload)
        #expect(!mini.isLocked)
        #expect(mini.menuTitle == "Mini")
    }

    @Test("a selectable, not-yet-downloaded tier needs a download — with a size hint")
    func needsDownloadWithSize() {
        let lil = row(.lil, in: rows(active: .mini))
        #expect(lil.needsDownload)
        #expect(lil.menuTitle.contains("Lil"))
        #expect(lil.menuTitle.contains("GB")) // ~2.9 GB
    }

    @Test("a downloaded tier is not a download prompt — just its name")
    func downloadedIsPlain() {
        let lil = row(.lil, in: rows(active: .lil, downloaded: [.lil]))
        #expect(!lil.needsDownload)
        #expect(lil.menuTitle == "Lil")
    }

    // MARK: - indicatorLabel (the no-lie-during-download invariant)

    @Test("indicator is the plain brain name only when truly settled (idle or ready)")
    func indicatorWhenSettled() {
        #expect(BrainSwitcher.indicatorLabel(active: .big, load: .ready) == "Big")
        #expect(BrainSwitcher.indicatorLabel(active: .big, load: .idle) == "Big")
    }

    @Test("indicator flags preparing (loading weights into memory) — not yet ready")
    func indicatorWhilePreparing() {
        #expect(BrainSwitcher.indicatorLabel(active: .lil, load: .preparing) == "Lil · loading")
    }

    @Test("indicator shows the percent while downloading — never a false 'ready'")
    func indicatorWhileDownloading() {
        #expect(BrainSwitcher.indicatorLabel(active: .big, load: .downloading(fraction: 0.34)) == "Big · 34%")
        #expect(BrainSwitcher.indicatorLabel(active: .big, load: .downloading(fraction: 0)) == "Big · 0%")
    }

    @Test("indicator flags a failed load")
    func indicatorWhenFailed() {
        #expect(BrainSwitcher.indicatorLabel(active: .lil, load: .failed(message: "boom")) == "Lil · failed")
    }

    /// The "no redundant multi-GB reload" invariant (109 review nit): re-selecting
    /// a warm brain is a no-op; anything less than warm-and-matching falls through.
    @Test("reselect is a no-op only for the same tier, ready, with the matching loaded model")
    func reselectNoOpWhenWarm() throws {
        let modelID = try #require(BrainTier.big.mlxModelID)
        #expect(BrainSwitcher.reselectIsNoOp(
            tier: .big, selected: .big, load: .ready, loadedModelID: modelID
        ))
    }

    @Test("a non-ready load state falls through (onboarding 'Try again' / first wake)")
    func reselectFallsThroughWhenNotReady() throws {
        let modelID = try #require(BrainTier.big.mlxModelID)
        for load in [ModelLoadState.idle, .preparing, .downloading(fraction: 0.5), .failed(message: "x")] {
            #expect(!BrainSwitcher.reselectIsNoOp(
                tier: .big, selected: .big, load: load, loadedModelID: modelID
            ), "\(load)")
        }
    }

    @Test("a different tier falls through — that's a real switch")
    func reselectFallsThroughForDifferentTier() throws {
        let modelID = try #require(BrainTier.big.mlxModelID)
        #expect(!BrainSwitcher.reselectIsNoOp(
            tier: .lil, selected: .big, load: .ready, loadedModelID: modelID
        ))
    }

    @Test("a loaded-model mismatch falls through — the warm provider isn't this tier's")
    func reselectFallsThroughOnModelMismatch() {
        #expect(!BrainSwitcher.reselectIsNoOp(
            tier: .big, selected: .big, load: .ready, loadedModelID: "some/other-model"
        ))
        #expect(!BrainSwitcher.reselectIsNoOp(
            tier: .big, selected: .big, load: .ready, loadedModelID: nil
        ))
    }

    @Test("Mini (no MLX model) is never guarded — re-selecting it is cheap")
    func reselectNeverGuardsMini() {
        #expect(!BrainSwitcher.reselectIsNoOp(
            tier: .mini, selected: .mini, load: .ready, loadedModelID: nil
        ))
    }
}
