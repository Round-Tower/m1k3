//
//  HelloView.swift
//  M1K3App
//
//  The ONE first-run screen. The old four-step wizard asked a stranger three
//  engine questions and (on a capable Mac) made them watch a multi-GB download
//  before M1K3 said a word. Now: live face, the privacy line, an optional name,
//  one tap — talking in seconds. Mini-first (Apple Foundation Models, instant,
//  zero download); every engine choice lives in Settings where it always also
//  lived. Defaults-with-disclosure: the caption says what was chosen and that
//  it's one click to change.
//
//  The only decision made here is FirstRunBrainPolicy's (pure, tested): AFM
//  available → Mini now; AFM warming → wait honestly, never download; AFM
//  blocked → the Lil fallback with an honest size and, when it's user-fixable,
//  the Apple Intelligence pointer. A re-run keeps a non-Mini brain untouched.
//
//  GAP-1 note: `selectBrain` no longer writes the first-run gate key — the
//  onComplete closure in M1K3App owns it — so this view's downloading/failed
//  states can't be swapped out from under themselves mid-fetch.
//
//  Signed: Kev + claude-fable-5, 2026-07-03, Confidence 0.85 (flow logic rides
//  tested policy; look/feel + the AFM-unavailable path are verify-by-launch).
//  Prior: none (new file; the wizard it replaces lives on as BrainPickerView).

import M1K3Avatar
import M1K3Inference
import SwiftUI

struct HelloView: View {
    @Environment(AppEnvironment.self) private var env
    let onComplete: () -> Void

    private enum Phase: Equatable {
        /// The one screen (copy adapts to AFM availability).
        case hello
        /// AFM assets still syncing — waiting, with an escape to the download.
        case waitingForMini
        /// Lil fallback downloading (`modelLoad` is the active selection's truth).
        case downloading
    }

    @State private var phase: Phase = .hello
    @State private var userName = ""
    @State private var afm: AFMAvailability = .available
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                switch phase {
                case .hello: helloCard
                case .waitingForMini: waitingCard
                case .downloading: downloadingCard
                }
            }
            .padding(32)
            .frame(maxWidth: .infinity)
            .animation(
                reduceMotion ? nil : .spring(response: 0.42, dampingFraction: 0.82),
                value: phase
            )
        }
        .frame(minWidth: 580, minHeight: 640)
        .background {
            // The live face is the room — same full-window reactive backdrop as
            // chat, mounted ONCE outside the phase switch (RealityView identity).
            // He steps back while a download/wait fills the screen with copy.
            AvatarChatBackground(env: env, isTyping: phase != .hello)
        }
        .glassBackdrop()
        .onAppear {
            afm = env.afmAvailability
            env.avatar.setEmotion(.happy)
        }
        .onDisappear { env.avatar.resetToIdle() }
        .onChange(of: env.modelLoad) { _, state in
            if case .ready = state, phase == .downloading { onComplete() }
        }
    }

    // MARK: - The one screen

    private var helloCard: some View {
        VStack(spacing: 18) {
            Text("M1K3")
                .font(.pixel(40))
                .kerning(2)
                .accessibilityAddTraits(.isHeader)

            Text("Your local AI companion. Everything stays on this Mac — no cloud, no account.")
                .font(.title3)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 440)

            TextField("What'll I call you? (optional)", text: $userName)
                .textFieldStyle(.roundedBorder)
                .font(.title3)
                .frame(maxWidth: 360)
                .accessibilityLabel("Your name, optional")
                .onSubmit(sayHello)

            Button(action: sayHello) {
                Text(needsFallbackDownload ? "Download & say hello →" : "Say hello →")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .frame(maxWidth: 360)
            .padding(.top, 4)

            Text(disclosureCaption)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 420)

            if case .blocked(userFixable: true) = afm, needsFallbackDownload {
                Button("Or turn on Apple Intelligence in System Settings") {
                    openSystemSettings()
                }
                .buttonStyle(.plain)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    /// True when the tested policy would answer this tap with the Lil download
    /// (AFM blocked, no heavier brain already chosen) — drives the honest CTA.
    private var needsFallbackDownload: Bool {
        if case .downloadFallback = FirstRunBrainPolicy.resolve(afm: afm, currentBrain: env.selectedBrain) {
            return true
        }
        return false
    }

    private var disclosureCaption: String {
        switch FirstRunBrainPolicy.resolve(afm: afm, currentBrain: env.selectedBrain) {
        case let .keepCurrent(tier):
            return "Runs on \(tier.displayName), your chosen brain · change anytime in Settings."
        case .useMini, .waitForMini:
            return "Runs on Mini, Apple's on-device model · change anytime in Settings."
        case .downloadFallback:
            return "Apple Intelligence isn't available here, so I'll fetch my own brain — "
                + "Lil M1K3, a one-time 2.3 GB download. Still fully on this Mac."
        }
    }

    // MARK: - Waiting (AFM assets syncing)

    private var waitingCard: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 80)
            ProgressView().controlSize(.large)
            Text("Mini is getting ready — Apple's on-device model is finishing a sync.")
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 400)
            Button("Use a downloaded brain instead (Lil, 2.3 GB)") {
                startFallbackDownload()
            }
            .buttonStyle(.plain)
            .font(.caption)
            .foregroundStyle(.secondary)
            Spacer(minLength: 80)
        }
        .task {
            // Re-poll while visible; completes the moment AFM turns available.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                if case .available = env.afmAvailability {
                    env.selectBrain(.mini)
                    onComplete()
                    return
                }
            }
        }
    }

    // MARK: - Downloading (the Lil fallback)

    private var downloadingCard: some View {
        VStack(spacing: 20) {
            Spacer(minLength: 60)
            switch env.modelLoad {
            case let .downloading(fraction):
                VStack(spacing: 6) {
                    ProgressView(value: fraction).frame(maxWidth: 320)
                    Text(env.modelLoad.label(modelName: BrainTier.lil.displayName))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            case .failed:
                VStack(spacing: 10) {
                    Label(env.modelLoad.label(modelName: BrainTier.lil.displayName),
                          systemImage: "exclamationmark.triangle")
                        .symbolRenderingMode(.hierarchical)
                        .font(.callout)
                        .foregroundStyle(.orange)
                    Button("Try again") { startFallbackDownload() }
                        .buttonStyle(.glass)
                    if case .blocked(userFixable: true) = afm {
                        Button("Or turn on Apple Intelligence in System Settings") {
                            openSystemSettings()
                        }
                        .buttonStyle(.plain)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }
            case .idle, .ready, .preparing:
                ProgressView().controlSize(.large)
            }
            Spacer(minLength: 60)
        }
    }

    // MARK: - Actions

    private func sayHello() {
        // Name first, brain second — the gate key flips in onComplete, and the
        // profile write must land before any window swap.
        env.saveFirstRunName(userName)
        env.avatar.setEmotion(.excited)
        switch FirstRunBrainPolicy.resolve(afm: afm, currentBrain: env.selectedBrain) {
        case let .keepCurrent(tier):
            env.selectBrain(tier)
            onComplete()
        case .useMini:
            env.selectBrain(.mini)
            onComplete()
        case .waitForMini:
            env.selectBrain(.mini)
            phase = .waitingForMini
        case let .downloadFallback(tier, _):
            startFallbackDownload(tier)
        }
    }

    private func startFallbackDownload(_ tier: BrainTier = .lil) {
        phase = .downloading
        // selectBrain drives the honest modelLoad bar; this IS the active
        // selection now, so using modelLoad here is correct (unlike the
        // background upgrade, which must never touch it).
        if env.selectBrain(tier) {
            // Already loaded (re-run edge) — no transition coming; finish now.
            onComplete()
        }
    }

    private func openSystemSettings() {
        // No verified deep link for the Apple Intelligence pane — open System
        // Settings itself rather than guess a pane id that could dead-end.
        if let url = URL(string: "x-apple.systempreferences:") {
            NSWorkspace.shared.open(url)
        }
    }
}
