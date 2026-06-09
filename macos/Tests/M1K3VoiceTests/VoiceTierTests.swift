import M1K3Voice
import Testing

struct VoiceTierTests {
    @Test("two tiers, built-in first")
    func cases() {
        #expect(VoiceTier.allCases == [.builtin, .m1k3Voice])
    }

    @Test("display names")
    func displayNames() {
        #expect(VoiceTier.builtin.displayName == "Built-in")
        #expect(VoiceTier.m1k3Voice.displayName == "M1K3 Voice")
    }

    @Test("only M1K3 Voice requires a download")
    func downloadRequirement() {
        #expect(VoiceTier.builtin.approxDownloadMB == nil)
        #expect(VoiceTier.builtin.requiresDownload == false)
        #expect(VoiceTier.m1k3Voice.approxDownloadMB == 354)
        #expect(VoiceTier.m1k3Voice.requiresDownload == true)
    }

    @Test("every tier has a non-empty tagline, detail, and glyph")
    func metadataPopulated() {
        for tier in VoiceTier.allCases {
            #expect(!tier.tagline.isEmpty)
            #expect(!tier.detail.isEmpty)
            #expect(!tier.glyph.isEmpty)
        }
    }

    @Test("identifiable by raw value")
    func identifiable() {
        #expect(VoiceTier.builtin.id == "builtin")
        #expect(VoiceTier.m1k3Voice.id == "m1k3Voice")
    }
}
