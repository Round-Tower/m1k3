//
//  CompanionSpec.swift
//  M1K3Avatar
//
//  A 3D companion creature — the OPT-IN avatar skin for voice mode, sibling to
//  the procedural pixel face (which stays M1K3's default brand face everywhere).
//  Pure data: the creature's identity, its display scale, and which animation
//  dialect its converted USDZ clips speak. No RealityKit here — playback lives in
//  the app's CompanionAvatarView (verify-by-launch); this layer is `swift test`'d.
//
//  Assets are per-clip USDZs (one mesh per file, animation harvested by filename —
//  clip names die in USD, the FILENAME is the clip identity; see scratch/usdz-probe).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.7 (mapping is by-design;
//  per-companion scale is a by-eye nudge on top of the view's auto-fit), Prior: Unknown

/// The two animation vocabularies in M1K3's companion lineup. The Quaternius
/// "Quirky Series" pack shares a rich clip set (Idle_A/Idle_B/Walk/Run/Jump/Fear/…);
/// the Khronos Fox speaks a leaner three-clip dialect (Survey/Walk/Run). A dialect
/// maps every abstract `CompanionGait` onto a clip it actually ships, collapsing
/// where a clip is missing — so the mapper can never name a clip that isn't bundled.
public enum CompanionDialect: Equatable, Sendable {
    case quaternius
    case fox

    public func clipName(for gait: CompanionGait) -> String {
        switch self {
        case .quaternius:
            switch gait {
            case .rest: "Idle_A"
            case .alert: "Idle_B"
            case .move: "Walk"
            case .react: "Jump"
            case .distress: "Fear"
            }
        case .fox:
            switch gait {
            case .rest, .alert: "Survey" // no distinct alert clip — survey reads as attentive
            case .move: "Walk"
            case .react, .distress: "Run" // no jump/fear — run carries both energy and agitation
            }
        }
    }
}

/// One installable companion. `clips` is the exact set of per-clip USDZ filenames
/// bundled for it (without the `.usdz` extension); `idleClip` is the resting clip
/// loaded first (and the one whose file carries the mesh the others bind onto).
public struct CompanionSpec: Equatable, Sendable, Identifiable {
    public let id: String
    public let displayName: String
    public let dialect: CompanionDialect
    public let clips: [String]
    /// By-eye fine-tune multiplied on top of the view's bounding-box auto-fit.
    public let scale: Float

    public init(id: String, displayName: String, dialect: CompanionDialect, clips: [String], scale: Float = 1.0) {
        self.id = id
        self.displayName = displayName
        self.dialect = dialect
        self.clips = clips
        self.scale = scale
    }

    /// The resting clip — what the companion idles on and the mesh-bearing file.
    public var idleClip: String {
        dialect.clipName(for: .rest)
    }

    // MARK: - Catalogue

    /// v1 ship companion — the site's hero, the leanest assets (~390 KB), and the
    /// fox dialect's special case proven first.
    public static let fox = CompanionSpec(
        id: "Fox",
        displayName: "Fox",
        dialect: .fox,
        clips: ["Survey", "Walk", "Run"]
    )

    /// Companion #2 — the Quaternius dialect's reference companion. Clips are
    /// exactly the gait-mapped set (bundled == emittable, no dead assets).
    public static let gecko = CompanionSpec(
        id: "Gecko",
        displayName: "Gecko",
        dialect: .quaternius,
        clips: ["Idle_A", "Idle_B", "Walk", "Jump", "Fear"]
    )

    /// Every known companion. The voice-mode picker self-limits to the subset that
    /// actually ships assets (CompanionAssets.isInstalled), so adding a creature is
    /// a spec line here plus its USDZ folder — no picker wiring.
    public static let all: [CompanionSpec] = [.fox, .gecko]

    /// Resolve a persisted picker id to a spec. The empty string (and any unknown
    /// id) means "pixel face" — the default — and returns nil.
    public static func named(_ id: String) -> CompanionSpec? {
        all.first { $0.id == id }
    }
}
