//
//  ClipMapper.swift
//  M1K3Avatar
//
//  AvatarState → companion animation clip. The pixel face reads the same
//  AvatarState through FaceExpression; this is its 3D sibling. Pure resolver:
//  state → a dialect-independent `CompanionGait` → the dialect's actual clip
//  name. The two-step keeps the "what mood" decision in one place and the "which
//  file" decision per-dialect, so a companion can never be asked for a clip it
//  doesn't ship (CompanionTests pins that invariant).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-11, Confidence 0.75 (mapping by-design,
//  crossfade timings are by-eye starting points), Prior: Unknown

/// The abstract motion a companion shows — independent of which creature it is.
/// Each `CompanionDialect` resolves a gait to one of its bundled clips.
public enum CompanionGait: CaseIterable, Sendable {
    case rest // calm idle
    case alert // attentive — listening / thinking
    case move // engaged — generating / speaking
    case react // a beat of delight (excited)
    case distress // error / upset
}

public enum ClipMapper {
    /// Resolve the gait for a state. Emotion takes priority for the two expressive
    /// beats (excited → react, distress → distress); otherwise activity drives it.
    public static func gait(for state: AvatarState) -> CompanionGait {
        if state.emotion == .excited { return .react }
        if state.activity == .error || state.emotion == .angry || state.emotion == .sad {
            return .distress
        }
        switch state.activity {
        case .idle: return .rest
        case .listening, .thinking: return .alert
        case .generating, .speaking: return .move
        case .error: return .distress // unreachable (caught above), kept total
        }
    }

    /// State → the clip filename to play for a given companion dialect.
    public static func clip(for state: AvatarState, dialect: CompanionDialect) -> String {
        dialect.clipName(for: gait(for: state))
    }

    /// Crossfade into a gait — reactions snap, settling is gentle. Seconds.
    public static func crossfadeDuration(to gait: CompanionGait) -> Double {
        switch gait {
        case .react, .distress: 0.15
        case .move, .alert: 0.25
        case .rest: 0.35
        }
    }
}
