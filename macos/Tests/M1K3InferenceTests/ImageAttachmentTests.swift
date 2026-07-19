//
//  ImageAttachmentTests.swift
//  M1K3InferenceTests
//
//  The image-input seam (Stage B of the vision arc): `ImageAttachment` is the
//  dependency-free carrier a chat turn uses to hand images down to a
//  vision-capable provider; `ToolMessage.user` grows an images payload with a
//  source-compatible bare-text overload so every existing call site compiles
//  unchanged.
//
//  Signed: Kev + claude-fable-5, 2026-07-19, Confidence 0.85 (red-first for
//  the seam types; the provider-side consumption is pinned in M1K3MLXTests).
//  Prior: Unknown
//

import Foundation
@testable import M1K3Inference
import Testing

struct ImageAttachmentTests {
    @Test("ImageAttachment is a value: same URL, equal attachment")
    func attachmentEquality() {
        let url = URL(fileURLWithPath: "/tmp/photo.png")
        #expect(ImageAttachment(url: url) == ImageAttachment(url: url))
        #expect(ImageAttachment(url: url)
            != ImageAttachment(url: URL(fileURLWithPath: "/tmp/other.png")))
    }

    @Test("ImageAttachment round-trips through Codable — history persistence carrier")
    func attachmentCodable() throws {
        let attachment = ImageAttachment(url: URL(fileURLWithPath: "/tmp/photo.png"))
        let data = try JSONEncoder().encode(attachment)
        let decoded = try JSONDecoder().decode(ImageAttachment.self, from: data)
        #expect(decoded == attachment)
    }

    @Test("ToolMessage.user carries images; the bare-text overload is empty-images")
    func userMessageImages() {
        let attachment = ImageAttachment(url: URL(fileURLWithPath: "/tmp/photo.png"))
        let withImage = ToolMessage.user("what is this?", images: [attachment])
        // The bare overload keeps every existing call site source-compatible
        // AND semantically identical to an explicit empty images array.
        #expect(ToolMessage.user("hi") == ToolMessage.user("hi", images: []))
        #expect(withImage != ToolMessage.user("what is this?"))
        if case let .user(text, images) = withImage {
            #expect(text == "what is this?")
            #expect(images == [attachment])
        } else {
            Issue.record("expected .user case")
        }
    }
}
