/**
 * 間 AI Web Avatar System
 *
 * Shared 3D avatar system using THREE.js.
 * Powers both Claude Desktop (MCP Apps) and standalone popover.
 *
 * @packageDocumentation
 */

// Registry
export { ModelRegistry, MODELS, DEFAULT_MODEL_ID } from "./registry/ModelRegistry";
export type { ModelConfig, ModelType } from "./registry/ModelRegistry";

export {
  Position3D,
  BoundingBox,
  AnimationMetadata,
  ModelMetadataUtils,
  CameraConfig,
} from "./registry/ModelMetadata";
export type {
  Position3D as Position3DType,
  BoundingBox as BoundingBoxType,
  AnimationMetadata as AnimationMetadataType,
  MaterialInfo,
  ModelMetadata,
  CameraConfig as CameraConfigType,
} from "./registry/ModelMetadata";

// Animation
export {
  EMOTION_INFO,
  ACTIVITY_INFO,
  DEFAULT_AVATAR_STATE,
  isActivityActive,
  stateFromActivity,
  getDisplayColor,
  isAnimating,
  parseEmotion,
  parseActivity,
} from "./animation/AvatarState";
export type {
  AvatarEmotion,
  AvatarActivity,
  AvatarState,
  EmotionInfo,
  ActivityInfo,
} from "./animation/AvatarState";

export {
  AnimationIntrospector,
  ANIMATION_SPEED_SCALE,
  getAnimationSpeed,
} from "./animation/AnimationIntrospector";

export { AnimationController } from "./animation/AnimationController";
export type {
  AnimationPlaybackState,
  AnimationControllerOptions,
} from "./animation/AnimationController";

// Renderer
export { GLBModelLoader } from "./renderer/GLBModelLoader";
export type { LoadedModel, LoadProgressCallback } from "./renderer/GLBModelLoader";

export { CameraAutoFit } from "./renderer/CameraAutoFit";
export type { CameraAutoFitOptions } from "./renderer/CameraAutoFit";

export { AvatarRenderer } from "./renderer/AvatarRenderer";
export type { AvatarRendererOptions } from "./renderer/AvatarRenderer";

// Communication
export { MCPAppBridge } from "./communication/MCPAppBridge";
export { WebSocketBridge } from "./communication/WebSocketBridge";

/**
 * Quick start helper - creates and initializes a renderer with default model
 *
 * @example
 * ```typescript
 * import { createAvatarRenderer } from "@m1k3/web-avatar";
 *
 * const renderer = await createAvatarRenderer({
 *   container: "#avatar-container",
 * });
 *
 * renderer.setState({ emotion: "HAPPY", activity: "SPEAKING" });
 * ```
 */
export async function createAvatarRenderer(
  options: import("./renderer/AvatarRenderer").AvatarRendererOptions
): Promise<import("./renderer/AvatarRenderer").AvatarRenderer> {
  const { AvatarRenderer } = await import("./renderer/AvatarRenderer");
  const renderer = new AvatarRenderer(options);
  await renderer.loadModel(options.modelId ?? "colobus");
  renderer.start();
  return renderer;
}

/**
 * Version info
 */
export const VERSION = "0.1.0";
