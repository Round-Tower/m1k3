/**
 * 間 AI Avatar State Types (TypeScript Port)
 *
 * Defines emotions, activities, and visual states for the avatar.
 *
 * Ported from: app/composeApp/src/commonMain/.../avatar/AvatarModels.kt
 */

/**
 * Avatar emotion types - 9 distinct emotional states
 */
export type AvatarEmotion =
  | "HAPPY"
  | "SAD"
  | "ANGRY"
  | "SURPRISED"
  | "LOVE"
  | "THINKING"
  | "SLEEPY"
  | "EXCITED"
  | "NEUTRAL";

/**
 * Emotion metadata with display info and colors
 */
export interface EmotionInfo {
  displayName: string;
  emoji: string;
  primaryColor: string; // Hex color
  description: string;
}

export const EMOTION_INFO: Record<AvatarEmotion, EmotionInfo> = {
  HAPPY: {
    displayName: "Happy",
    emoji: "😊",
    primaryColor: "#4CAF50", // Green
    description: "Joyful, content, satisfied",
  },
  SAD: {
    displayName: "Sad",
    emoji: "😢",
    primaryColor: "#2196F3", // Blue
    description: "Melancholy, disappointed, down",
  },
  ANGRY: {
    displayName: "Angry",
    emoji: "😠",
    primaryColor: "#F44336", // Red
    description: "Frustrated, irritated, upset",
  },
  SURPRISED: {
    displayName: "Surprised",
    emoji: "😲",
    primaryColor: "#FFEB3B", // Yellow
    description: "Astonished, shocked, amazed",
  },
  LOVE: {
    displayName: "Love",
    emoji: "😍",
    primaryColor: "#E91E63", // Pink
    description: "Affectionate, adoring, caring",
  },
  THINKING: {
    displayName: "Thinking",
    emoji: "🤔",
    primaryColor: "#9C27B0", // Purple
    description: "Pondering, processing, analyzing",
  },
  SLEEPY: {
    displayName: "Sleepy",
    emoji: "😴",
    primaryColor: "#607D8B", // Blue-gray
    description: "Tired, drowsy, resting",
  },
  EXCITED: {
    displayName: "Excited",
    emoji: "🤩",
    primaryColor: "#E25303", // M1K3 orange
    description: "Enthusiastic, energized, thrilled",
  },
  NEUTRAL: {
    displayName: "Neutral",
    emoji: "😐",
    primaryColor: "#9E9E9E", // Gray
    description: "Calm, balanced, composed",
  },
};

/**
 * Avatar activity state - what the AI is currently doing
 */
export type AvatarActivity =
  | "IDLE"
  | "LISTENING"
  | "THINKING"
  | "GENERATING"
  | "SPEAKING"
  | "ERROR";

/**
 * Activity metadata
 */
export interface ActivityInfo {
  displayName: string;
  description: string;
}

export const ACTIVITY_INFO: Record<AvatarActivity, ActivityInfo> = {
  IDLE: {
    displayName: "Idle",
    description: "Waiting for input",
  },
  LISTENING: {
    displayName: "Listening",
    description: "Processing user input",
  },
  THINKING: {
    displayName: "Thinking",
    description: "Analyzing and reasoning",
  },
  GENERATING: {
    displayName: "Generating",
    description: "Creating response",
  },
  SPEAKING: {
    displayName: "Speaking",
    description: "Delivering response",
  },
  ERROR: {
    displayName: "Error",
    description: "Encountered an issue",
  },
};

/**
 * Check if activity is active (not idle or error)
 */
export function isActivityActive(activity: AvatarActivity): boolean {
  return activity !== "IDLE" && activity !== "ERROR";
}

/**
 * Avatar visual state - complete state snapshot for rendering
 */
export interface AvatarState {
  emotion: AvatarEmotion;
  activity: AvatarActivity;
  /** 0.0 (subtle) to 1.0 (extreme) */
  intensity: number;
  /** 0.0 to 1.0 for transitions */
  animationProgress: number;
  /** Optional status message */
  message?: string;
}

/**
 * Default avatar state
 */
export const DEFAULT_AVATAR_STATE: AvatarState = {
  emotion: "NEUTRAL",
  activity: "IDLE",
  intensity: 0.5,
  animationProgress: 0,
};

/**
 * Create avatar state from activity
 */
export function stateFromActivity(activity: AvatarActivity): AvatarState {
  const emotion: AvatarEmotion =
    activity === "IDLE"
      ? "NEUTRAL"
      : activity === "LISTENING"
        ? "THINKING"
        : activity === "THINKING"
          ? "THINKING"
          : activity === "GENERATING"
            ? "EXCITED"
            : activity === "SPEAKING"
              ? "HAPPY"
              : activity === "ERROR"
                ? "ANGRY"
                : "NEUTRAL";

  return {
    emotion,
    activity,
    intensity: isActivityActive(activity) ? 0.7 : 0.5,
    animationProgress: 0,
  };
}

/**
 * Get display color for emotion with intensity adjustment
 */
export function getDisplayColor(state: AvatarState): string {
  const baseColor = EMOTION_INFO[state.emotion].primaryColor;
  // For simplicity, return base color (intensity blending would require color math)
  return baseColor;
}

/**
 * Check if avatar should be animating
 */
export function isAnimating(state: AvatarState): boolean {
  return isActivityActive(state.activity) || state.animationProgress > 0;
}

/**
 * Parse emotion from string (case-insensitive)
 */
export function parseEmotion(name: string): AvatarEmotion {
  const upper = name.toUpperCase();
  if (upper in EMOTION_INFO) {
    return upper as AvatarEmotion;
  }
  return "NEUTRAL";
}

/**
 * Parse activity from string (case-insensitive)
 */
export function parseActivity(name: string): AvatarActivity {
  const upper = name.toUpperCase();
  if (upper in ACTIVITY_INFO) {
    return upper as AvatarActivity;
  }
  return "IDLE";
}
