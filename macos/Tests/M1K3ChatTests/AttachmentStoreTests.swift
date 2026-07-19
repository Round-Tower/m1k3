//
//  AttachmentStoreTests.swift
//  M1K3ChatTests
//
//  The attachment intake: whatever the user picks (Photos export, Desktop
//  screenshot, download) is COPIED into the app's own attachments directory
//  before the send, so the ImageAttachment URL the transcript persists stays
//  readable across relaunches and never points at a sandbox-scoped original.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (red-first;
//  pure FileManager logic under a temp directory). Prior: Unknown
//

import Foundation
@testable import M1K3Chat
import M1K3Inference
import Testing

struct AttachmentStoreTests {
    private func makeTempDirectory() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("attachment-store-tests-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    @Test("stores a copy inside the attachments directory and returns its attachment")
    func storesCopy() throws {
        let temp = try makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: temp) }
        let original = temp.appendingPathComponent("photo.png")
        try Data([0x89, 0x50, 0x4E, 0x47]).write(to: original)

        let store = AttachmentStore(directory: temp.appendingPathComponent("attachments"))
        let attachment = try store.store(originalURL: original)

        #expect(attachment.url.path.contains("attachments"))
        #expect(FileManager.default.fileExists(atPath: attachment.url.path))
        // The copy is independent of the original — deleting the source
        // must not break the stored attachment (the whole point).
        try FileManager.default.removeItem(at: original)
        #expect(FileManager.default.fileExists(atPath: attachment.url.path))
    }

    @Test("preserves the file extension (the VLM processor sniffs by type)")
    func preservesExtension() throws {
        let temp = try makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: temp) }
        let original = temp.appendingPathComponent("shot.jpeg")
        try Data([0xFF, 0xD8]).write(to: original)

        let store = AttachmentStore(directory: temp.appendingPathComponent("attachments"))
        let attachment = try store.store(originalURL: original)
        #expect(attachment.url.pathExtension == "jpeg")
    }

    @Test("two stores of the same file yield distinct attachment files")
    func distinctCopies() throws {
        let temp = try makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: temp) }
        let original = temp.appendingPathComponent("photo.png")
        try Data([0x89]).write(to: original)

        let store = AttachmentStore(directory: temp.appendingPathComponent("attachments"))
        let first = try store.store(originalURL: original)
        let second = try store.store(originalURL: original)
        #expect(first.url != second.url)
    }

    @Test("a missing source throws rather than fabricating an attachment")
    func missingSourceThrows() throws {
        let temp = try makeTempDirectory()
        defer { try? FileManager.default.removeItem(at: temp) }
        let store = AttachmentStore(directory: temp.appendingPathComponent("attachments"))
        #expect(throws: (any Error).self) {
            _ = try store.store(originalURL: temp.appendingPathComponent("nope.png"))
        }
    }
}
