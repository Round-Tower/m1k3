//
//  M1K3iOSHarnessApp.swift
//  M1K3iOS — on-device derisk harness (NOT the shipping app)
//
//  A minimal SwiftUI iOS/visionOS shell that runs the REAL M1K3 engine on device
//  AND wears M1K3's real face: the procedural pixel-cube avatar (M1K3Avatar +
//  AvatarView, now cross-platform), the Silkscreen pixel brand, Liquid Glass, and
//  M1K3's asymmetric chat bubbles (flat M1K3 turns, tinted-glass user turns). It
//  exists to move the iOS port from "compiles" to "looks and runs like M1K3" — the
//  rung after the 2026-07-06 compile-green spike. Still deliberately minimal: no
//  voice (AVAudioSession is Phase 2), no RAG/MCP/menu-bar. The shipping iOS app is
//  the Phase-2 shared adaptive shell (docs/IOS_VISIONOS_PORT.md).
//
//  Signed: Kev + claude-fable-5, 2026-07-06, Confidence 0.7 (compile-verified for
//  iOS; on-device RUN + feel is the point — verify-by-launch on Kev's iPhone).
//  Prior: Unknown.
//

import M1K3Avatar
import M1K3Inference
import M1K3MLX
import SwiftUI

@main
struct M1K3iOSHarnessApp: App {
    init() {
        BundledFonts.register() // Silkscreen — the pixel wordmark face.
    }

    var body: some Scene {
        WindowGroup {
            HarnessView()
        }
    }
}

/// Which engine the harness drives. Mini is Apple Intelligence (instant, no
/// download, needs AI enabled on the device); Lil is the MLX Qwen3-4B (a ~2.5GB
/// on-device download on first use).
private enum HarnessBrain: String, CaseIterable, Identifiable {
    case mini = "Mini"
    case lil = "Lil"
    var id: String {
        rawValue
    }

    var subtitle: String {
        switch self {
        case .mini: "Apple Intelligence"
        case .lil: "MLX Qwen3-4B"
        }
    }
}

/// One conversation turn for the harness transcript.
private struct Turn: Identifiable {
    enum Speaker { case you, m1k3 }
    let id = UUID()
    let speaker: Speaker
    var text: String
}

struct HarnessView: View {
    @State private var avatar = AvatarController()
    @State private var brain: HarnessBrain = .mini
    @State private var draft = ""
    @State private var turns: [Turn] = []
    @State private var status = ""
    @State private var busy = false
    @State private var downloadProgress: Double?
    @State private var lilProvider: MLXGemmaProvider?

    var body: some View {
        ZStack {
            backdrop
            VStack(spacing: 0) {
                header
                transcript
                inputBar
            }
        }
        .preferredColorScheme(.dark)
        .onAppear { avatar.resetToIdle() }
    }

    // MARK: - Backdrop (deep, so the LED face glows)

    private var backdrop: some View {
        LinearGradient(
            colors: [Color(red: 0.05, green: 0.05, blue: 0.11), .black],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }

    /// Once a conversation is underway the hero avatar shrinks to a compact
    /// dock so the transcript gets the room (the Mac's hero→dock evolution).
    private var chatting: Bool {
        !turns.isEmpty
    }

    // MARK: - Header: avatar + wordmark

    private var header: some View {
        VStack(spacing: 6) {
            AvatarView(controller: avatar)
                .frame(height: chatting ? 84 : 200)
                .padding(.horizontal, chatting ? 130 : 40)
            if !chatting {
                Text("M1K3")
                    .font(.pixel(30))
                    .kerning(2)
                    .foregroundStyle(.white)
            }
            Picker("Brain", selection: $brain) {
                ForEach(HarnessBrain.allCases) { b in
                    Text(b.rawValue).tag(b)
                }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 240)
            Text(brain == .mini ? miniAvailabilityText : "MLX Qwen3-4B · on-device")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(.black.opacity(0.001)) // keep the header above the fading transcript
        .animation(.spring(duration: 0.45), value: chatting)
    }

    // MARK: - Transcript (M1K3's asymmetric bubbles)

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 14) {
                    if turns.isEmpty {
                        Text("Ask me anything.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .padding(.top, 24)
                    }
                    ForEach(turns) { turn in
                        turnRow(turn).id(turn.id)
                    }
                    if !status.isEmpty {
                        Text(status)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            // Fade the top edge so a long answer dissolves UNDER the header
            // instead of hard-cutting against the picker/caption.
            .mask(
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .black, location: 0.05),
                        .init(color: .black, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .onChange(of: turns.count) {
                if let last = turns.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
        }
    }

    @ViewBuilder
    private func turnRow(_ turn: Turn) -> some View {
        switch turn.speaker {
        case .you:
            HStack {
                Spacer(minLength: 60)
                Text(turn.text)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .glassEffect(.regular.tint(.accentColor.opacity(0.22)), in: .rect(cornerRadius: 18))
            }
        case .m1k3:
            // M1K3's turns are FLAT — no card — sitting on the backdrop (the house rule).
            Text(turn.text)
                .font(.body)
                .lineSpacing(4)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)
        }
    }

    // MARK: - Input bar

    private var inputBar: some View {
        HStack(spacing: 10) {
            TextField("Ask M1K3…", text: $draft, axis: .vertical)
                .lineLimit(1 ... 4)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .glassEffect(.regular, in: .rect(cornerRadius: 20))
            Button {
                Task { await ask() }
            } label: {
                if busy {
                    ProgressView()
                        .frame(width: 26, height: 26)
                } else {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 30))
                        .symbolRenderingMode(.hierarchical)
                }
            }
            .buttonStyle(.plain)
            .disabled(busy || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .overlay(alignment: .top) {
            if let downloadProgress {
                ProgressView(value: downloadProgress) {
                    Text("Downloading Lil… \(Int(downloadProgress * 100))%").font(.caption2)
                }
                .padding(.horizontal, 16)
                .offset(y: -18)
            }
        }
    }

    private var miniAvailabilityText: String {
        switch AppleFoundationModelsProvider().availabilityState {
        case .available: "Apple Intelligence · ready"
        case .notReady: "Apple Intelligence · still downloading"
        case let .blocked(userFixable):
            userFixable ? "Turn on Apple Intelligence in Settings" : "This device can't run Apple Intelligence — use Lil"
        }
    }

    // MARK: - Ask

    private func ask() async {
        let question = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !question.isEmpty else { return }
        draft = ""
        turns.append(Turn(speaker: .you, text: question))
        busy = true
        status = ""
        downloadProgress = nil
        avatar.setActivity(.thinking)
        defer {
            busy = false
            downloadProgress = nil
        }

        do {
            let started = Date()
            let text: String
            switch brain {
            case .mini:
                let provider = AppleFoundationModelsProvider()
                guard provider.isAvailable else {
                    status = miniAvailabilityText
                    avatar.setEmotion(.sad)
                    return
                }
                text = try await provider.generate(prompt: question)
            case .lil:
                MLXMemoryBudget.applyOnce()
                let provider = try await ensureLil()
                text = try await provider.generate(prompt: question)
            }
            avatar.setActivity(.speaking)
            turns.append(Turn(speaker: .m1k3, text: text))
            status = "\(brain.rawValue) · \(brain.subtitle) · \(String(format: "%.1f", Date().timeIntervalSince(started)))s"
            avatar.setEmotion(.happy)
        } catch {
            status = "Error: \(error.localizedDescription)"
            avatar.setActivity(.error)
        }
    }

    private func ensureLil() async throws -> MLXGemmaProvider {
        if let lilProvider { return lilProvider }
        let provider = MLXGemmaProvider(modelID: "mlx-community/Qwen3-4B-4bit")
        try await provider.prepare { fraction in
            Task { @MainActor in downloadProgress = fraction }
        }
        lilProvider = provider
        return provider
    }
}
