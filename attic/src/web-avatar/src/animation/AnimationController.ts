/**
 * 間 AI Animation Controller
 *
 * Manages animation playback for 3D avatar models using THREE.js.
 * Handles emotion-driven animation selection, blending, and idle variants.
 */

import * as THREE from "three";
import type { AnimationMetadata } from "../registry/ModelMetadata";
import type { AvatarState } from "./AvatarState";
import { AnimationIntrospector, ANIMATION_SPEED_SCALE, getAnimationSpeed } from "./AnimationIntrospector";

/**
 * Animation playback state
 */
export type AnimationPlaybackState =
  | "USER_DIRECTED"  // User explicitly selected animation
  | "AUTO_IDLE"      // Automatic idle state
  | "IDLE_VARIANT";  // Rotating through idle variants

/**
 * Animation controller options
 */
export interface AnimationControllerOptions {
  /** Enable idle variant rotation (default: true) */
  enableIdleVariants?: boolean;
  /** Seconds between idle variant changes (default: 12) */
  idleVariantInterval?: number;
  /** Blend duration in seconds (default: 0.3) */
  blendDuration?: number;
  /** Global speed scale (default: ANIMATION_SPEED_SCALE) */
  speedScale?: number;
}

const DEFAULT_OPTIONS: Required<AnimationControllerOptions> = {
  enableIdleVariants: true,
  idleVariantInterval: 8, // Base interval (will be randomized)
  blendDuration: 0.5, // Smoother transitions
  speedScale: ANIMATION_SPEED_SCALE,
};

// Randomize interval between 60-140% of base
function randomizeInterval(base: number): number {
  return base * (0.6 + Math.random() * 0.8);
}

/**
 * Animation Controller
 *
 * Manages animation playback for a 3D model.
 */
export class AnimationController {
  private mixer: THREE.AnimationMixer;
  private clips: THREE.AnimationClip[];
  private metadata: AnimationMetadata[];
  private options: Required<AnimationControllerOptions>;

  private currentAction: THREE.AnimationAction | null = null;
  private currentAnimationIndex: number = -1;
  private playbackState: AnimationPlaybackState = "AUTO_IDLE";
  private idleTimer: number = 0;
  private idleVariantIndex: number = 0;
  private nextIdleSwitch: number = 8; // Randomized target time
  private breathePhase: number = 0; // For subtle procedural motion
  private scene: THREE.Object3D; // Store reference for procedural motion

  constructor(
    scene: THREE.Object3D,
    clips: THREE.AnimationClip[],
    metadata: AnimationMetadata[],
    options?: AnimationControllerOptions
  ) {
    this.mixer = new THREE.AnimationMixer(scene);
    this.scene = scene;
    this.clips = clips;
    this.metadata = metadata;
    this.options = { ...DEFAULT_OPTIONS, ...options };
    this.nextIdleSwitch = randomizeInterval(this.options.idleVariantInterval);
  }

  /**
   * Update animation state from avatar state
   *
   * @param state Current avatar state (emotion + activity)
   * @param forceUpdate Force animation change even if same
   */
  updateFromState(state: AvatarState, forceUpdate = false): void {
    // Find best animation for current state
    const targetAnim = AnimationIntrospector.findAnimation(state, this.metadata);

    if (targetAnim.index !== this.currentAnimationIndex || forceUpdate) {
      this.playAnimation(targetAnim.index, targetAnim.isLoopable);
      this.playbackState = state.activity === "IDLE" ? "AUTO_IDLE" : "USER_DIRECTED";
    }

    // Update animation speed based on intensity
    if (this.currentAction) {
      const speed = getAnimationSpeed(state.intensity) * this.options.speedScale;
      this.currentAction.setEffectiveTimeScale(speed);
    }
  }

  /**
   * Play animation by index
   *
   * @param index Animation index
   * @param loop Whether to loop (default: true)
   * @param speed Playback speed multiplier (default: 1.0 * speedScale)
   */
  playAnimation(index: number, loop = true, speed?: number): void {
    if (index < 0 || index >= this.clips.length) {
      console.warn(`Animation index ${index} out of bounds`);
      return;
    }

    const clip = this.clips[index];
    const newAction = this.mixer.clipAction(clip);

    // Configure new action
    newAction.setLoop(loop ? THREE.LoopRepeat : THREE.LoopOnce, Infinity);
    newAction.clampWhenFinished = !loop;
    newAction.setEffectiveTimeScale(
      (speed ?? 1.0) * this.options.speedScale
    );

    // Crossfade from current animation
    if (this.currentAction && this.currentAction !== newAction) {
      newAction.reset();
      newAction.play();
      this.currentAction.crossFadeTo(newAction, this.options.blendDuration, true);
    } else {
      newAction.reset();
      newAction.play();
    }

    this.currentAction = newAction;
    this.currentAnimationIndex = index;
  }

  /**
   * Play animation by name
   *
   * @param name Animation name (case-insensitive)
   * @param loop Whether to loop
   */
  playAnimationByName(name: string, loop = true): void {
    const anim = AnimationIntrospector.findByName(name, this.metadata);
    if (anim) {
      this.playAnimation(anim.index, loop);
    } else {
      console.warn(`Animation "${name}" not found`);
    }
  }

  /**
   * Update animation mixer (call every frame)
   *
   * @param deltaTime Time since last update in seconds
   */
  update(deltaTime: number): void {
    this.mixer.update(deltaTime);

    // Subtle breathing motion (always active, very subtle)
    this.breathePhase += deltaTime * 0.8; // Slow breathing ~0.13 Hz
    const breatheScale = 1.0 + Math.sin(this.breathePhase) * 0.008; // +/- 0.8%
    const breatheSway = Math.sin(this.breathePhase * 0.7) * 0.003; // Subtle sway
    this.scene.scale.setScalar(breatheScale);
    this.scene.rotation.z = breatheSway;

    // Handle idle variant rotation with randomized timing
    if (
      this.options.enableIdleVariants &&
      (this.playbackState === "AUTO_IDLE" || this.playbackState === "IDLE_VARIANT")
    ) {
      this.idleTimer += deltaTime;
      if (this.idleTimer >= this.nextIdleSwitch) {
        this.idleTimer = 0;
        this.nextIdleSwitch = randomizeInterval(this.options.idleVariantInterval);
        this.rotateIdleVariant();
      }
    }
  }

  /**
   * Rotate to next idle variant animation
   */
  private rotateIdleVariant(): void {
    const idleAnims = AnimationIntrospector.getIdleAnimations(this.metadata);
    if (idleAnims.length <= 1) return;

    this.idleVariantIndex = (this.idleVariantIndex + 1) % idleAnims.length;
    const nextIdle = idleAnims[this.idleVariantIndex];

    this.playAnimation(nextIdle.index, true);
    this.playbackState = "IDLE_VARIANT";
  }

  /**
   * Stop all animations
   */
  stop(): void {
    this.mixer.stopAllAction();
    this.currentAction = null;
    this.currentAnimationIndex = -1;
  }

  /**
   * Pause current animation
   */
  pause(): void {
    if (this.currentAction) {
      this.currentAction.paused = true;
    }
  }

  /**
   * Resume current animation
   */
  resume(): void {
    if (this.currentAction) {
      this.currentAction.paused = false;
    }
  }

  /**
   * Get current animation metadata
   */
  getCurrentAnimation(): AnimationMetadata | null {
    if (this.currentAnimationIndex >= 0) {
      return this.metadata[this.currentAnimationIndex];
    }
    return null;
  }

  /**
   * Get all available animations
   */
  getAvailableAnimations(): AnimationMetadata[] {
    return this.metadata;
  }

  /**
   * Get playback state
   */
  getPlaybackState(): AnimationPlaybackState {
    return this.playbackState;
  }

  /**
   * Set playback speed
   */
  setSpeed(speed: number): void {
    if (this.currentAction) {
      this.currentAction.setEffectiveTimeScale(speed * this.options.speedScale);
    }
  }

  /**
   * Dispose of resources
   */
  dispose(): void {
    this.mixer.stopAllAction();
    this.mixer.uncacheRoot(this.mixer.getRoot());
  }
}
