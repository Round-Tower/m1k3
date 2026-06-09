//
//  OnboardingCards.swift
//  M1K3App
//
//  The selectable cards for the first-run flow — Brain (Mini/Lil/Big), Voice input
//  (Apple Speech / WhisperKit), and Voice output (Built-in / M1K3 Voice). Extracted
//  from OnboardingView so that file stays within length limits; all three share the
//  same glass-card layout.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85, Prior: Unknown

import M1K3Inference
import M1K3Voice
import SwiftUI

// MARK: - Brain card

struct BrainCard: View {
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
                        Text(tier.displayName).font(.pixel(15))
                        if isRecommended { recommendedBadge }
                    }
                    .padding(.bottom, 2)
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
        guard let megabytes = tier.approxDownloadMB else { return "Built-in · no download" }
        if megabytes >= 1000 {
            return String(format: "~%.1f GB · one-time download", Double(megabytes) / 1000)
        }
        return "~\(megabytes) MB · one-time download"
    }
}

// MARK: - Voice-input card

struct VoiceCard: View {
    enum Engine {
        case apple, whisperKit

        var glyph: String {
            switch self {
            case .apple: "waveform"
            case .whisperKit: "waveform.badge.star"
            }
        }

        var displayName: String {
            switch self {
            case .apple: "Apple Speech"
            case .whisperKit: "WhisperKit"
            }
        }

        var tagline: String {
            switch self {
            case .apple: "Ready now · no download"
            case .whisperKit: "Higher accuracy · ~142 MB"
            }
        }

        var detail: String {
            switch self {
            case .apple:
                "Built into macOS. Works immediately, always on-device."
            case .whisperKit:
                "Open-source model. More accurate in noise and with accents. "
                    + "One download, then always offline."
            }
        }
    }

    let engine: Engine
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: engine.glyph)
                    .symbolRenderingMode(.hierarchical)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(.tint)
                    .frame(width: 40)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(engine.displayName).font(.pixel(15)).padding(.bottom, 2)
                    Text(engine.tagline)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.tint)
                    Text(engine.detail)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
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
        .accessibilityLabel("\(engine.displayName), \(engine.tagline)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

// MARK: - Voice-output card

struct SpeechVoiceCard: View {
    let tier: VoiceTier
    let isSelected: Bool
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
                    Text(tier.displayName).font(.pixel(15)).padding(.bottom, 2)
                    Text(tier.tagline)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.tint)
                    Text(tier.detail)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
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
        .accessibilityLabel("\(tier.displayName), \(tier.tagline)")
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
