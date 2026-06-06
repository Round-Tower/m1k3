//
//  CallsView.swift
//  M1K3App
//
//  The calls drawer: import a transcript, browse the (encrypted-at-rest) call log,
//  read a call's summary + speaker-attributed transcript. Dumb glue over
//  AppEnvironment — all the work (parse → summarise → encrypt → index) lives in the
//  tested M1K3Calls package.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3Calls
import SwiftUI
import UniformTypeIdentifiers

struct CallsView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.dismiss) private var dismiss
    @State private var showImporter = false
    @State private var selected: CallSession?

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        .frame(width: 480, height: 540)
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [.plainText, .text],
            allowsMultipleSelection: false
        ) { result in
            if case let .success(urls) = result, let url = urls.first {
                Task { await env.importCallTranscript(url: url) }
            }
        }
        .sheet(item: $selected) { CallDetailView(call: $0) }
        .safeAreaInset(edge: .bottom) {
            if let status = env.lastCallStatus {
                HStack(spacing: 8) {
                    if env.isImportingCall { ProgressView().controlSize(.small) }
                    Text(status).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    Spacer()
                }
                .padding(12)
            }
        }
    }

    private var header: some View {
        HStack {
            Label("Calls", systemImage: "phone.bubble")
                .font(.headline)
            Text("\(env.callCount)") // observable — re-renders the list after import/delete
                .font(.caption).foregroundStyle(.secondary)
            Spacer()
            Button {
                showImporter = true
            } label: {
                Label("Import transcript", systemImage: "doc.badge.plus")
            }
            .disabled(env.isImportingCall)
            Button("Done") { dismiss() }
                .buttonStyle(.glassProminent)
        }
        .padding(16)
    }

    @ViewBuilder
    private var content: some View {
        let calls = env.calls()
        if calls.isEmpty {
            ContentUnavailableView {
                Label("No calls yet", systemImage: "phone.bubble")
            } description: {
                Text("Import a transcript (lines like “Speaker: …”). M1K3 summarises it, encrypts it at rest, and makes it searchable alongside your documents.")
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List {
                ForEach(calls) { call in
                    Button { selected = call } label: { CallRow(call: call) }
                        .buttonStyle(.plain)
                        .swipeActions {
                            Button("Delete", role: .destructive) { env.deleteCall(id: call.id) }
                        }
                }
            }
        }
    }
}

private struct CallRow: View {
    let call: CallSession

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(call.title).font(.body.weight(.medium))
                Spacer()
                Text(call.startedAt, format: .dateTime.day().month().hour().minute())
                    .font(.caption2).foregroundStyle(.secondary)
            }
            if let gist = call.fullSummary?.overview ?? call.quickSummary?.overview, !gist.isEmpty {
                Text(gist).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
            Text("\(call.segments.count) lines").font(.caption2).foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
        .contentShape(.rect)
    }
}

struct CallDetailView: View {
    let call: CallSession
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Label(call.title, systemImage: "phone.bubble").font(.headline)
                Spacer()
                Button("Done") { dismiss() }.buttonStyle(.glassProminent)
            }
            .padding(16)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let summary = call.fullSummary {
                        summarySection(summary)
                    } else if let quick = call.quickSummary {
                        sectionCard("Summary") { Text(quick.overview) }
                    }
                    transcriptSection
                }
                .padding(16)
            }
        }
        .frame(width: 520, height: 560)
    }

    private func summarySection(_ summary: CallSummary) -> some View {
        sectionCard("Summary") {
            VStack(alignment: .leading, spacing: 10) {
                if !summary.overview.isEmpty { Text(summary.overview) }
                if !summary.keyPoints.isEmpty {
                    bulletList("Key points", summary.keyPoints)
                }
                if !summary.actionItems.isEmpty {
                    bulletList("Action items", summary.actionItems)
                }
            }
        }
    }

    private var transcriptSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Transcript").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(call.segments) { segment in
                VStack(alignment: .leading, spacing: 2) {
                    if let speaker = segment.speaker {
                        Text(speaker).font(.caption.weight(.semibold)).foregroundStyle(.tint)
                    }
                    Text(segment.text).font(.callout)
                }
            }
        }
    }

    private func bulletList(_ title: String, _ items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            ForEach(items, id: \.self) { item in
                Label(item, systemImage: "circle.fill")
                    .labelStyle(BulletLabelStyle())
                    .font(.callout)
            }
        }
    }

    private func sectionCard(_ title: String, @ViewBuilder _ content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassEffect(.regular, in: .rect(cornerRadius: 14))
    }
}

private struct BulletLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            configuration.icon.font(.system(size: 5)).foregroundStyle(.tint)
            configuration.title
        }
    }
}
