/**
 * 間 AI Animation Introspection System (TypeScript Port)
 *
 * Intelligently maps avatar emotions/activities to model animations
 * using fuzzy matching and semantic patterns.
 *
 * Ported from: app/composeApp/src/commonMain/.../avatar/AnimationIntrospector.kt
 */

import type { AnimationMetadata } from "../registry/ModelMetadata";
import { AnimationMetadata as AnimUtils } from "../registry/ModelMetadata";
import type { AvatarState, AvatarEmotion, AvatarActivity } from "./AvatarState";
import { EMOTION_INFO, ACTIVITY_INFO } from "./AvatarState";

/**
 * Animation pattern matching scores
 */
enum MatchScore {
  EXACT = 100, // Exact name match
  STRONG = 75, // Strong keyword match
  MODERATE = 50, // Moderate keyword match
  WEAK = 25, // Weak keyword match
  FALLBACK = 10, // Default fallback
}

/**
 * Emotion → animation keyword mapping
 * Maps AvatarEmotion to keywords for fuzzy matching.
 * Ordered by preference (first = strongest match).
 */
const EMOTION_KEYWORDS: Record<AvatarEmotion, string[]> = {
  HAPPY: ["bounce", "idle_b", "happy", "excited", "jump"],
  SAD: ["sit", "idle_a", "sad", "death", "walk"],
  ANGRY: ["attack", "angry", "hit", "spin"],
  SURPRISED: ["hit", "surprised", "bounce", "clicked"],
  LOVE: ["eat", "love", "sit", "idle_b"],
  THINKING: ["spin", "thinking", "idle_a", "walk"],
  SLEEPY: ["sit", "idle_a", "sleepy", "death"],
  EXCITED: ["bounce", "jump", "excited", "run", "fly"],
  NEUTRAL: ["idle_c", "idle", "neutral"],
};

/**
 * Activity → animation keyword mapping
 */
const ACTIVITY_KEYWORDS: Record<AvatarActivity, string[]> = {
  LISTENING: ["idle_a", "idle", "sit"],
  THINKING: ["spin", "thinking", "idle_a"],
  GENERATING: ["run", "generating", "walk", "fly"],
  SPEAKING: ["clicked", "speaking", "bounce", "idle_b"],
  ERROR: ["death", "error", "hit", "fear"],
  IDLE: ["idle"], // Use emotion-based mapping
};

/**
 * Animation Introspector
 *
 * Provides intelligent animation selection based on avatar state.
 */
export const AnimationIntrospector = {
  /**
   * Find best animation for avatar state
   *
   * Main API for animation selection. Uses intelligent matching:
   * 1. Activity takes precedence (if not IDLE)
   * 2. Emotion provides fallback
   * 3. Intensity affects speed (not animation choice)
   */
  findAnimation(
    state: AvatarState,
    availableAnimations: AnimationMetadata[]
  ): AnimationMetadata {
    if (availableAnimations.length === 0) {
      throw new Error("Model has no animations");
    }

    // Priority 1: Activity-based animation (if not IDLE)
    if (state.activity !== "IDLE") {
      const activityAnim = findBestMatch(
        ACTIVITY_KEYWORDS[state.activity] ?? [],
        availableAnimations
      );
      if (activityAnim) {
        return activityAnim;
      }
    }

    // Priority 2: Emotion-based animation
    const emotionAnim = findBestMatch(
      EMOTION_KEYWORDS[state.emotion] ?? [],
      availableAnimations
    );
    if (emotionAnim) {
      return emotionAnim;
    }

    // Priority 3: Fallback to first idle or first animation
    return (
      availableAnimations.find((a) => AnimUtils.isIdle(a)) ??
      availableAnimations[0]
    );
  },

  /**
   * Find animation by exact name (case-insensitive)
   */
  findByName(
    name: string,
    availableAnimations: AnimationMetadata[]
  ): AnimationMetadata | undefined {
    return availableAnimations.find(
      (a) => a.name.toLowerCase() === name.toLowerCase()
    );
  },

  /**
   * Find animations matching pattern
   */
  findMatching(
    pattern: string,
    availableAnimations: AnimationMetadata[]
  ): AnimationMetadata[] {
    return availableAnimations.filter((a) => AnimUtils.matches(a, pattern));
  },

  /**
   * Get all idle animations
   */
  getIdleAnimations(
    availableAnimations: AnimationMetadata[]
  ): AnimationMetadata[] {
    return availableAnimations.filter((a) => AnimUtils.isIdle(a));
  },

  /**
   * Get all movement animations
   */
  getMovementAnimations(
    availableAnimations: AnimationMetadata[]
  ): AnimationMetadata[] {
    return availableAnimations.filter((a) => AnimUtils.isMovement(a));
  },

  /**
   * Suggest animations for emotion
   * Returns all animations that could represent the emotion, ranked by score.
   */
  suggestForEmotion(
    emotion: AvatarEmotion,
    availableAnimations: AnimationMetadata[]
  ): Array<{ animation: AnimationMetadata; score: number }> {
    const keywords = EMOTION_KEYWORDS[emotion] ?? [];
    return availableAnimations
      .map((anim) => ({
        animation: anim,
        score: calculateMatchScore(anim.name, keywords),
      }))
      .filter((r) => r.score > 0)
      .sort((a, b) => b.score - a.score);
  },

  /**
   * Generate animation mapping report (for debugging)
   */
  generateMappingReport(availableAnimations: AnimationMetadata[]): string {
    const lines: string[] = [];
    lines.push("Animation Mapping Report");
    lines.push("=".repeat(50));
    lines.push("");
    lines.push(`Available Animations (${availableAnimations.length}):`);
    for (const anim of availableAnimations) {
      const loopStr = anim.isLoopable ? "loop" : "once";
      lines.push(`  • ${anim.name} (${anim.duration.toFixed(1)}s, ${loopStr})`);
    }
    lines.push("");
    lines.push("Emotion Mappings:");
    for (const [emotion, info] of Object.entries(EMOTION_INFO)) {
      const suggestions = this.suggestForEmotion(
        emotion as AvatarEmotion,
        availableAnimations
      );
      const best = suggestions[0];
      if (best) {
        lines.push(
          `  ${info.emoji} ${info.displayName} → ${best.animation.name} (score: ${best.score})`
        );
      } else {
        lines.push(`  ${info.emoji} ${info.displayName} → [no match]`);
      }
    }
    lines.push("");
    lines.push("Activity Mappings:");
    for (const [activity, info] of Object.entries(ACTIVITY_INFO)) {
      const testState: AvatarState = {
        emotion: "NEUTRAL",
        activity: activity as AvatarActivity,
        intensity: 0.5,
        animationProgress: 0,
      };
      const anim = this.findAnimation(testState, availableAnimations);
      lines.push(`  ${info.displayName} → ${anim.name}`);
    }

    return lines.join("\n");
  },
};

/**
 * Find best matching animation from keyword list
 */
function findBestMatch(
  keywords: string[],
  availableAnimations: AnimationMetadata[]
): AnimationMetadata | undefined {
  for (const keyword of keywords) {
    const match = availableAnimations.find((a) =>
      a.name.toLowerCase().includes(keyword.toLowerCase())
    );
    if (match) {
      return match;
    }
  }
  return undefined;
}

/**
 * Calculate match score for animation name
 */
function calculateMatchScore(animationName: string, keywords: string[]): number {
  const lowerName = animationName.toLowerCase();

  // Check for exact match
  if (keywords.some((k) => lowerName === k.toLowerCase())) {
    return MatchScore.EXACT;
  }

  // Check for strong keyword match (first 2 keywords)
  if (
    keywords.slice(0, 2).some((k) => lowerName.includes(k.toLowerCase()))
  ) {
    return MatchScore.STRONG;
  }

  // Check for moderate keyword match (next 3 keywords)
  if (
    keywords.slice(2, 5).some((k) => lowerName.includes(k.toLowerCase()))
  ) {
    return MatchScore.MODERATE;
  }

  // Check for weak keyword match (remaining keywords)
  if (keywords.slice(5).some((k) => lowerName.includes(k.toLowerCase()))) {
    return MatchScore.WEAK;
  }

  return 0;
}

/**
 * Global animation speed scale factor
 * All animations are multiplied by this value to slow them down.
 */
export const ANIMATION_SPEED_SCALE = 0.4;

/**
 * Get animation playback speed multiplier based on intensity
 *
 * - 0.0 → 0.5x speed (very slow)
 * - 0.5 → 1.0x speed (normal)
 * - 1.0 → 1.5x speed (very fast)
 */
export function getAnimationSpeed(intensity: number): number {
  return 0.5 + intensity; // Maps 0.0-1.0 to 0.5-1.5
}
