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

    @Test("a locked tier is present (not dropped), not a download prompt, and names its floor")
    func lockedTierShownWithFloor() {
        let huge = row(.huge, in: rows(active: .mini, locked: [.huge]))
        #expect(huge.isLocked)
        #expect(!huge.needsDownload) // a locked row is never a download CTA
        #expect(huge.menuTitle.contains("32GB"))
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
}
