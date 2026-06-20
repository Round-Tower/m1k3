//
//  ArtifactView.swift
//  M1K3App
//
//  The renderer for code artifacts M1K3's brain generates. Two tabs — Preview
//  (live WKWebView) and Code (syntax-highlighted source) — plus an Export
//  button. The WebView uses a non-persistent data store (same privacy stance
//  as WebReviewView), and loads the HTML string directly rather than from a URL.

import AppKit
import M1K3Preview
import SwiftUI
import UniformTypeIdentifiers
import WebKit

struct ArtifactView: View {
    let artifact: CodeArtifact

    enum Tab: String, CaseIterable {
        case preview = "Preview"
        case code = "Code"
    }

    @State private var selectedTab: Tab = .preview
    @State private var showExporter = false

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().opacity(0.4)
            switch selectedTab {
            case .preview:
                ArtifactPreviewWebView(source: artifact.source)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .code:
                codeView
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .fileExporter(
            isPresented: $showExporter,
            document: ArtifactDocument(artifact: artifact),
            contentType: .plainText,
            defaultFilename: artifact.filename
        ) { _ in }
    }

    private var toolbar: some View {
        HStack(spacing: 12) {
            Picker("", selection: $selectedTab) {
                ForEach(Tab.allCases, id: \.self) { tab in
                    Text(tab.rawValue).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 200)

            Spacer()

            Text(artifact.title ?? "untitled")
                .font(.caption)
                .foregroundStyle(.secondary)

            Button {
                showExporter = true
            } label: {
                Image(systemName: "square.and.arrow.up")
            }
            .help("Export \(artifact.filename)")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var codeView: some View {
        ScrollView {
            Text(artifact.source)
                .font(.system(.body, design: .monospaced))
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
        }
        .background(.background.opacity(0.5))
    }
}

// MARK: - WKWebView for artifact preview

private struct ArtifactPreviewWebView: NSViewRepresentable {
    let source: String

    func makeNSView(context _: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.loadHTMLString(source, baseURL: nil)
        return webView
    }

    func updateNSView(_ webView: WKWebView, context _: Context) {
        webView.loadHTMLString(source, baseURL: nil)
    }
}

// MARK: - FileDocument for export

struct ArtifactDocument: FileDocument {
    static var readableContentTypes: [UTType] {
        [.plainText]
    }

    let artifact: CodeArtifact

    init(artifact: CodeArtifact) {
        self.artifact = artifact
    }

    init(configuration _: ReadConfiguration) throws {
        artifact = CodeArtifact(source: "", language: .html)
    }

    func fileWrapper(configuration _: WriteConfiguration) throws -> FileWrapper {
        guard let data = artifact.exportData else {
            throw CocoaError(.fileWriteUnknown)
        }
        return FileWrapper(regularFileWithContents: data)
    }
}
