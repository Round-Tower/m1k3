//
//  ImageAttachment.swift
//  M1K3Inference
//
//  The dependency-free image carrier for the chat→provider seam (the vision
//  arc, Stage B). A file URL inside the app container — the chat layer copies
//  whatever the user attached (drag, paste, picker) into its own storage
//  first, so the URL it hands down stays valid for history replay and never
//  points at sandbox-scoped originals the app can't reopen later.
//
//  URL-only on purpose: the MLX layer feeds `UserInput.Image.url` directly,
//  history persistence stores a bookmarkable path, and no pixel data rides
//  the seam types. Codable so ChatTurn persistence can carry it verbatim.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (seam type
//  red-first in ImageAttachmentTests; consumption pinned in the MLX mapping
//  tests). Prior: Unknown
//

import Foundation

/// One image attached to a user turn.
public struct ImageAttachment: Sendable, Equatable, Hashable, Codable {
    /// File URL of the image, inside the app's own container.
    public let url: URL

    public init(url: URL) {
        self.url = url
    }
}
