//
//  QuickLookPreview.swift
//  M1K3App
//
//  A thin SwiftUI wrapper over AppKit's QLPreviewView — the same engine Finder's
//  Space-bar preview uses. Free, faithful rendering of PDFs, images, source code
//  (syntax-highlighted), Markdown, and most document types, with zero render code
//  of our own. Verify-by-run: there's no logic to unit-test here, just the bridge.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.8, Prior: Unknown

import Quartz
import SwiftUI

/// Renders a local file with QuickLook. The file must be sandbox-readable — i.e.
/// it arrived via drop or the open panel (which grant access); a bare typed path
/// to an un-granted file won't render.
struct QuickLookPreview: NSViewRepresentable {
    let url: URL

    func makeNSView(context _: Context) -> QLPreviewView {
        let view = QLPreviewView(frame: .zero, style: .normal) ?? QLPreviewView()
        view.autostarts = true
        view.previewItem = url as NSURL
        return view
    }

    func updateNSView(_ view: QLPreviewView, context _: Context) {
        // Only re-point when the file actually changed, so QuickLook doesn't
        // reload (and flicker) on every SwiftUI update pass.
        if (view.previewItem as? URL) != url {
            view.previewItem = url as NSURL
        }
    }
}
