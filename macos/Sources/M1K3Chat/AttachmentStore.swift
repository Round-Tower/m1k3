//
//  AttachmentStore.swift
//  M1K3Chat
//
//  Copies a user-picked image into the app's own attachments directory before
//  a send, so the `ImageAttachment` URL the transcript persists survives
//  relaunches and never points at a sandbox-scoped original the app can't
//  reopen (a security-scoped picker URL is only readable inside the picker's
//  access window). UUID filenames keep repeated sends of the same file
//  distinct; the original's extension is preserved because type sniffing
//  downstream (the VLM processor / QuickLook thumbnails) reads it.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (TDD'd against a
//  temp directory: copy, independence-from-original, extension, distinctness,
//  missing-source throw). Prior: Unknown
//

import Foundation
import M1K3Inference

/// Intake for chat image attachments: copy in, hand back the attachment.
public struct AttachmentStore: Sendable {
    private let directory: URL

    /// `directory` is created on first store if absent (e.g.
    /// `Application Support/attachments` inside the container).
    public init(directory: URL) {
        self.directory = directory
    }

    /// Copy the file at `originalURL` into the attachments directory and
    /// return the attachment pointing at the COPY. Throws if the source
    /// doesn't exist or the copy fails — never fabricates an attachment.
    public func store(originalURL: URL) throws -> ImageAttachment {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let ext = originalURL.pathExtension
        let name = ext.isEmpty ? UUID().uuidString : "\(UUID().uuidString).\(ext)"
        let destination = directory.appendingPathComponent(name)
        try FileManager.default.copyItem(at: originalURL, to: destination)
        return ImageAttachment(url: destination)
    }
}
