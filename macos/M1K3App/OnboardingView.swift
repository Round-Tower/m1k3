//
//  OnboardingView.swift
//  M1K3App
//
//  First-run "choose your brain" — Mini / Lil / Big M1K3, the macOS take on the
//  KMP onboarding (app/.../ui/OnboardingScreen.kt). Pick a brain, then wake it:
//  Mini (Apple Foundation Models) is instant; Lil / Big download once, with a real
//  progress bar driven by the same ModelLoadState the Settings preload uses.
//
//  Verify-by-launch SwiftUI glue — the brain metadata + recommendation are tested
//  in BrainTierTests; the download itself only runs from the .app.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.75, Prior: Unknown

import M1K3Inference
import SwiftUI

struct OnboardingView: View {
    @Environment(AppEnvironment.self) private var env
    /// Called once a brain is chosen and (for Lil/Big) downloaded.
    let onComplete: () -> Void

    @State private var selected: BrainTier = .recommendedForThisMac
    @State private var isWaking = false

    private let recommended = BrainTier.recommendedForThisMac

    var body: some View {
        VStack(spacing: 24) {
            header
            if isWaking {
                awakening
            } else {
                picker
            }
        }
        .padding(32)
        .frame(minWidth: 580, minHeight: 640)
        .glassBackdrop()
        .onChange(of: env.modelLoad) { _, state in
            // Lil/Big finished downloading → into the app.
            if case .ready = state, isWaking { onComplete() }
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(spacing: 8) {
            Image(systemName: "brain.head.profile.fill")
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(.tint)
                .padding(.bottom, 4)
            Text("Meet M1K3")
                .font(.largeTitle.weight(.bold))
            Text("Your local intelligence machine. Choose a brain — it runs entirely on this Mac.")
                .font(.title3)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 460)
        }
    }

    // MARK: - Picker

    private var picker: some View {
        VStack(spacing: 16) {
            ForEach(BrainTier.allCases) { tier in
                BrainCard(
                    tier: tier,
                    isSelected: selected == tier,
                    isRecommended: tier == recommended
                ) { selected = tier }
            }

            Button(action: wake) {
                Text(wakeButtonTitle)
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .padding(.top, 4)

            Text("On-device only. Your conversations never leave this Mac.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var wakeButtonTitle: String {
        selected.requiresDownload
            ? "Download \(selected.displayName) →"
            : "Wake up \(selected.displayName) →"
    }

    private func wake() {
        env.selectBrain(selected)
        if selected.requiresDownload {
            isWaking = true // watch modelLoad; onComplete fires on .ready
        } else {
            onComplete() // Mini is instant
        }
    }

    // MARK: - Awakening (download)

    private var awakening: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: selected.glyph)
                .font(.system(size: 44, weight: .semibold))
                .foregroundStyle(.tint)
                .symbolEffect(.pulse)
            Text("Waking up \(selected.displayName)…")
                .font(.title2.weight(.semibold))

            switch env.modelLoad {
            case let .downloading(fraction):
                VStack(spacing: 6) {
                    ProgressView(value: fraction)
                        .frame(maxWidth: 320)
                    Text(env.modelLoad.label(modelName: selected.displayName))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            case .failed:
                VStack(spacing: 10) {
                    Label(env.modelLoad.label(modelName: selected.displayName), systemImage: "exclamationmark.triangle")
                        .symbolRenderingMode(.hierarchical)
                        .font(.callout)
                        .foregroundStyle(.orange)
                    Button("Try again") { wake() }
                        .buttonStyle(.glass)
                    Button("Use Mini (built-in) instead") {
                        selected = .mini
                        isWaking = false
                        env.selectBrain(.mini)
                        onComplete()
                    }
                    .buttonStyle(.plain)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            case .idle, .ready:
                ProgressView()
            }
            Text("Your data stays on your device, always.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }
}

// MARK: - Brain card

private struct BrainCard: View {
    let tier: BrainTier
    let isSelected: Bool
    let isRecommended: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: tier.glyph)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 40)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(tier.displayName).font(.headline)
                        if isRecommended { recommendedBadge }
                    }
                    Text(tier.tagline)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.tint)
                    Text(tier.detail)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(sizeLabel)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.tertiary)
                        .padding(.top, 2)
                }
                Spacer(minLength: 0)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                    .font(.title3)
                    .accessibilityHidden(true)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassEffect(
                isSelected ? .regular.tint(.accentColor.opacity(0.22)) : .regular,
                in: .rect(cornerRadius: 18)
            )
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(tier.displayName), \(tier.tagline). \(sizeLabel)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }

    private var recommendedBadge: some View {
        Text("Recommended")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .glassEffect(.regular.tint(.accentColor.opacity(0.3)), in: .capsule)
    }

    private var sizeLabel: String {
        guard let megabytes = tier.approxDownloadMB else {
            return "Built-in · no download"
        }
        if megabytes >= 1000 {
            return String(format: "~%.1f GB · one-time download", Double(megabytes) / 1000)
        }
        return "~\(megabytes) MB · one-time download"
    }
}
