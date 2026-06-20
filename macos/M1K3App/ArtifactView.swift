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
import os
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
            contentType: artifact.language.utType,
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

            Text(artifact.displayTitle)
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

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        // The preview renders UNTRUSTED, model-generated HTML. Disable JavaScript
        // outright: with no script there is no fetch/WebSocket/XHR egress, which is
        // the amplifier that makes every other gap exploitable. A code PREVIEW does
        // not need to execute scripts to render its HTML/CSS.
        config.defaultWebpagePreferences.allowsContentJavaScript = false
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator

        // Block ALL sub-resource loads (images, scripts, stylesheets, fetch/XHR).
        // The navigation delegate handles navigation-level requests synchronously;
        // the content rule list seals the sub-resource gap. The rule decisions live
        // in the tested ArtifactSandboxPolicy.
        let source = self.source
        WKContentRuleListStore.default().compileContentRuleList(
            forIdentifier: ArtifactSandboxPolicy.contentRuleListIdentifier,
            encodedContentRuleList: ArtifactSandboxPolicy.contentRuleListJSON
        ) { list, error in
            if let list {
                webView.configuration.userContentController.add(list)
                webView.loadHTMLString(source, baseURL: nil)
            } else {
                // Fail CLOSED: if the seal won't compile, do NOT load the content with
                // only the navigation delegate (which does not see sub-resource loads).
                // Show an honest placeholder instead of a silently un-sealed preview.
                Self.logger.error(
                    "artifact preview seal failed to compile; refusing to load unsealed content: \(String(describing: error), privacy: .public)"
                )
                webView.loadHTMLString(Self.sealFailureHTML, baseURL: nil)
            }
        }

        return webView
    }

    private static let logger = Logger(subsystem: "app.m1k3", category: "artifact-preview")

    private static let sealFailureHTML =
        "<html><body style=\"font-family:-apple-system;color:#888;padding:2rem\">"
            + "Preview unavailable — the content sandbox could not be initialised.</body></html>"

    func updateNSView(_: WKWebView, context _: Context) {
        // source is immutable (let); identity is managed by .id(artifact.createdAt) in the parent.
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void)
        {
            let allowed = ArtifactSandboxPolicy.allowsNavigation(scheme: action.request.url?.scheme)
            decisionHandler(allowed ? .allow : .cancel)
        }
    }
}

// MARK: - FileDocument for export

struct ArtifactDocument: FileDocument {
    static var readableContentTypes: [UTType] {
        [.html, .javaScript, .sourceCode, .plainText]
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
