//
//  MessageView.swift
//  M1K3App
//
//  One turn in the transcript. User turns are tinted glass on the trailing edge;
//  assistant turns lead with the answer, a speak button, and a disclosure of the
//  grounding sources (the [ChunkHit]s ChatSession attached) so provenance is one
//  tap away — honest RAG, the M1K3 way.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import AppKit
import M1K3Chat
import M1K3Knowledge
import M1K3Preview
import SwiftUI

struct MessageView: View {
    let message: ChatMessage
    /// Called with the message text when the user taps speak.
    let onSpeak: (String) -> Void
    /// Called with a link found in the turn when the user taps its chip — opens it
    /// in the review panel rather than launching an external browser.
    var onOpenLink: (URL) -> Void = { _ in }
    /// The active brain's context window, so the stats footer can show how full it
    /// got. 0 = unknown (the footer then shows the raw token count).
    var contextWindow: Int = 0

    /// Testing aid: show per-turn generation stats under each answer (Settings).
    @AppStorage(AppEnvironment.showGenerationStatsKey) private var showGenerationStats = false

    /// The http(s) links mentioned in this turn, surfaced as one-click chips.
    /// Memoised in @State and recomputed only when the turn's text changes (via
    /// `.task(id:)`), off the synchronous render path — NSDataDetector shouldn't
    /// run on every body pass, which matters under the ~20Hz streaming throttle.
    @State private var links: [URL] = []

    @State private var showSources = false
    @State private var showReasoning = false
    @State private var didCopy = false

    var body: some View {
        rows
            // Detect off the MainActor: NSDataDetector is synchronous, and under the
            // ~20Hz stream throttle this re-runs on every partial — keep the regex
            // engine off the main thread so a long streamed answer can't drop frames.
            .task(id: message.text) {
                let text = message.text
                links = await Task.detached(priority: .utility) { LinkDetector.detect(in: text) }.value
            }
    }

    @ViewBuilder
    private var rows: some View {
        switch message.role {
        case .user:
            VStack(alignment: .trailing, spacing: 6) {
                HStack {
                    Spacer(minLength: 60)
                    bubble
                        .glassEffect(.regular.tint(.accentColor.opacity(0.2)), in: .rect(cornerRadius: 18))
                }
                linkChips
            }
        case .assistant:
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 8) {
                    assistantBody
                    if let reasoning = message.reasoning, !reasoning.isEmpty {
                        reasoningDisclosure(reasoning)
                            // Scale + fade the reasoning panel in (anchored at the
                            // top so it grows downward from the answer, not jumps).
                            .transition(.scale(scale: 0.96, anchor: .top).combined(with: .opacity))
                    }
                    if !citedSources.isEmpty {
                        sourcesDisclosure
                    }
                    linkChips
                    statsFooter
                }
                // Animate the panel's insertion/removal + the live think→answer
                // collapse. Paired with the ~20Hz stream throttle so it eases, not stutters.
                .animation(.easeOut(duration: 0.22), value: message.reasoning != nil)
                .animation(.easeOut(duration: 0.22), value: isThinkingLive)
                Spacer(minLength: 40)
            }
        }
    }

    private var bubble: some View {
        ReadingText(message.text)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
    }

    /// Per-turn generation stats (context tokens · output · tok/s) — a testing aid,
    /// shown only when the Settings toggle is on and the turn reported metrics
    /// (MLX tiers; Mini reports none). The context figure shows the fraction of the
    /// window used when known, else the raw prompt-token count.
    @ViewBuilder
    private var statsFooter: some View {
        if showGenerationStats, let metrics = message.metrics {
            let context: String = {
                if let fraction = metrics.contextFraction(window: contextWindow) {
                    return "\(metrics.promptTokens.formatted()) / \(contextWindow.formatted()) ctx "
                        + "(\(Int((fraction * 100).rounded()))%)"
                }
                return "\(metrics.promptTokens.formatted()) tok ctx"
            }()
            Text("\(context) · \(metrics.generationTokens.formatted()) out · "
                + "\(Int(metrics.tokensPerSecond.rounded())) tok/s")
                .font(.caption2.monospacedDigit())
                .foregroundStyle(.secondary)
                .textSelection(.enabled)
        }
    }

    private var assistantBody: some View {
        VStack(alignment: .leading, spacing: 8) {
            if !message.text.isEmpty {
                // NO .contentTransition(.opacity)/.animation(value: message.text) here:
                // contentTransition cross-fades the WHOLE Text on every value change,
                // so a growing streamed answer flashes ALL words on each token (worse
                // at the ~20Hz stream cadence). Plain append reads as live; a true
                // per-word fade-in would need per-word views (not worth the frame cost).
                ReadingText(message.text)
            } else if case .streaming = message.status {
                // While the agent works (thinking, searching the web…) show what
                // it's doing — the label doubles as the privacy surface for any
                // query that leaves the device.
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    if let activity = message.activityLabel {
                        Text(activity)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .contentTransition(.opacity)
                    }
                }
            } else if case .complete = message.status {
                // A completed-but-empty reply (the model returned nothing) reads
                // as a state, not a hollow glass bubble.
                Text("No response.")
                    .italic()
                    .foregroundStyle(.secondary)
            }

            if case let .failed(reason) = message.status {
                Label(reason, systemImage: "exclamationmark.triangle")
                    .symbolRenderingMode(.hierarchical)
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            if case .complete = message.status, !message.text.isEmpty {
                HStack(spacing: 6) {
                    Button {
                        onSpeak(message.text)
                    } label: {
                        Image(systemName: "speaker.wave.2")
                            .symbolRenderingMode(.hierarchical)
                            .font(.caption)
                    }
                    .buttonStyle(.glass)
                    .accessibilityLabel("Speak this answer")
                    .help("Speak this answer")

                    Button {
                        copyAnswer(message.text)
                    } label: {
                        Image(systemName: didCopy ? "checkmark" : "doc.on.doc")
                            .symbolRenderingMode(.hierarchical)
                            .font(.caption)
                            .contentTransition(.symbolEffect(.replace))
                    }
                    .buttonStyle(.glass)
                    .accessibilityLabel("Copy this answer")
                    .help(didCopy ? "Copied" : "Copy this answer")
                }
            }
        }
        // M1K3's turns sit flat on the backdrop — minimal, no glass card (the user's
        // own turns keep the tinted glass bubble, so the two speakers stay legible
        // apart). Kept a little inset so the controls aren't jammed against the edge.
        .padding(.vertical, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Copy the answer to the clipboard, with a brief checkmark confirmation. Copies
    /// the shown text (already polished) — what the user reads is what they share.
    private func copyAnswer(_ text: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        didCopy = true
        Task {
            try? await Task.sleep(for: .seconds(1.6))
            didCopy = false
        }
    }

    /// True while the model is thinking out loud: still streaming, reasoning
    /// arriving, no answer text yet.
    private var isThinkingLive: Bool {
        if case .streaming = message.status { return message.text.isEmpty }
        return false
    }

    /// The model's chain-of-thought, surfaced (collapsed by default) for
    /// transparency — the answer stays clean, the reasoning is one tap away.
    /// While the model is still thinking it auto-expands and streams live, so
    /// a long think phase reads as visible work, not a silent stall; it
    /// collapses again the moment the answer starts (unless the user opened it).
    private func reasoningDisclosure(_ reasoning: String) -> some View {
        DisclosureGroup(isExpanded: Binding(
            get: { showReasoning || isThinkingLive },
            set: { showReasoning = $0 }
        )) {
            ReadingText(reasoning)
                .foregroundStyle(.secondary)
                // Same gentle fade as the answer, so the chain-of-thought streams
                // in softly while the disclosure is open.
                .contentTransition(.opacity)
                .animation(.easeOut(duration: 0.18), value: reasoning)
                .padding(.top, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label(isThinkingLive ? "Thinking…" : "Model reasoning", systemImage: "ellipsis.bubble")
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .contentTransition(.opacity)
        }
        .padding(.horizontal, 6)
    }

    /// One-click chips for any links in the turn — tap to review in the side
    /// panel (stays on-device, no jump to an external browser).
    @ViewBuilder
    private var linkChips: some View {
        if !links.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(links, id: \.self) { url in
                    LinkChip(url: url) { onOpenLink(url) }
                }
            }
            .padding(.horizontal, 6)
        }
    }

    /// Only the sources the answer actually cited. Retrieval is promiscuous (top-K
    /// over the grounding floor), so an off-topic chunk can ride along; show what
    /// was REFERENCED, not everything read — matching HeadlessAsk's footer. The
    /// message keeps the full `.sources` (retrieved) for diagnostics; this is the
    /// honest display set.
    private var citedSources: [ChunkHit] {
        CitationFooter.referencedSources(from: message.sources, citedBy: message.citations)
    }

    private var sourcesDisclosure: some View {
        DisclosureGroup(isExpanded: $showSources) {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(citedSources) { hit in
                    SourceRow(hit: hit)
                }
            }
            .padding(.top, 4)
        } label: {
            Label("\(citedSources.count) source\(citedSources.count == 1 ? "" : "s")",
                  systemImage: "doc.text.magnifyingglass")
                .font(.caption.weight(.medium).monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 6)
    }
}

private struct SourceRow: View {
    let hit: ChunkHit

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption.weight(.semibold))
            Text(hit.content)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .glassEffect(.regular, in: .rect(cornerRadius: 10))
    }

    private var label: String {
        if let heading = hit.heading, !heading.isEmpty {
            "\(hit.itemTitle) · \(heading)"
        } else {
            hit.itemTitle
        }
    }
}

/// A tappable link found in a turn — opens in the review panel, not an external
/// browser, so the review stays on this Mac.
private struct LinkChip: View {
    let url: URL
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "safari").imageScale(.small)
                Text(label)
                    .lineLimit(1)
                    .truncationMode(.middle)
                Image(systemName: "arrow.up.forward.app")
                    .imageScale(.small)
                    .foregroundStyle(.secondary)
            }
            .font(.caption.weight(.medium))
        }
        .buttonStyle(.glass)
        .help("Review \(url.absoluteString) in the side panel")
        .accessibilityLabel("Review link \(label)")
    }

    /// Host without a leading "www.", falling back to the full string.
    private var label: String {
        guard let host = url.host else { return url.absoluteString }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }
}
