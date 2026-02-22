/**
 * 間 AI Shader Effect Manager
 *
 * Post-processing pipeline for emotion-reactive visual effects.
 * Built on THREE.js EffectComposer for high-performance shader chains.
 *
 * @murphysig v1.0.0 - Initial shader system
 * @confidence 0.85 - Proven THREE.js patterns, minimal risk
 * @context Adds visual flair to avatars, emotion-driven shaders
 */

import * as THREE from "three";
import { EffectComposer } from "three/examples/jsm/postprocessing/EffectComposer.js";
import { RenderPass } from "three/examples/jsm/postprocessing/RenderPass.js";
import { ShaderPass } from "three/examples/jsm/postprocessing/ShaderPass.js";
import { UnrealBloomPass } from "three/examples/jsm/postprocessing/UnrealBloomPass.js";
import type { AvatarEmotion, AvatarActivity } from "../animation/AvatarState";

/**
 * Available shader effects
 */
export type ShaderEffect =
  | "none"
  | "pixelation"
  | "glitch"
  | "hologram"
  | "wireframe"
  | "bloom"
  | "chromatic"
  | "scanlines";

/**
 * Effect configuration
 */
export interface EffectConfig {
  /** Effect type */
  effect: ShaderEffect;
  /** Effect intensity 0.0-1.0 */
  intensity?: number;
  /** Auto-blend with emotion */
  emotionReactive?: boolean;
}

/**
 * Emotion → Shader mapping
 * Defines which shaders best represent each emotion
 */
const EMOTION_SHADERS: Record<AvatarEmotion, ShaderEffect> = {
  HAPPY: "bloom",
  SAD: "scanlines",
  ANGRY: "glitch",
  SURPRISED: "chromatic",
  LOVE: "bloom",
  THINKING: "hologram",
  SLEEPY: "scanlines",
  EXCITED: "pixelation",
  NEUTRAL: "none",
};

/**
 * Activity → Shader mapping
 */
const ACTIVITY_SHADERS: Record<AvatarActivity, ShaderEffect | null> = {
  IDLE: null,
  LISTENING: "hologram",
  THINKING: "hologram",
  GENERATING: "glitch",
  SPEAKING: "bloom",
  ERROR: "glitch",
};

/**
 * Shader Effect Manager
 *
 * Manages post-processing effects for the avatar renderer.
 * Automatically applies shaders based on emotion/activity state.
 */
export class ShaderEffectManager {
  private composer: EffectComposer;
  private renderer: THREE.WebGLRenderer;
  private scene: THREE.Scene;
  private camera: THREE.Camera;

  private renderPass: RenderPass;
  private activeEffects = new Map<ShaderEffect, ShaderPass>();

  private currentEffect: ShaderEffect = "none";
  private intensity = 0.7;
  private emotionReactive = true;

  constructor(
    renderer: THREE.WebGLRenderer,
    scene: THREE.Scene,
    camera: THREE.Camera
  ) {
    this.renderer = renderer;
    this.scene = scene;
    this.camera = camera;

    // Create composer
    this.composer = new EffectComposer(renderer);

    // Add base render pass (always active)
    this.renderPass = new RenderPass(scene, camera);
    this.composer.addPass(this.renderPass);

    console.log("[ShaderEffectManager] Initialized");
  }

  /**
   * Apply shader effect
   */
  setEffect(effect: ShaderEffect, intensity = 0.7): void {
    // Remove current effect
    if (this.currentEffect !== "none") {
      this.removeCurrentEffect();
    }

    this.currentEffect = effect;
    this.intensity = intensity;

    if (effect === "none") {
      return;
    }

    // Add new effect
    const pass = this.createEffectPass(effect, intensity);
    if (pass) {
      this.composer.addPass(pass);
      this.activeEffects.set(effect, pass);
      console.log(`[ShaderEffectManager] Applied ${effect} (${intensity})`);
    }
  }

  /**
   * Update effect for avatar state (emotion-reactive)
   */
  updateForState(emotion: AvatarEmotion, activity: AvatarActivity): void {
    if (!this.emotionReactive) return;

    // Activity takes precedence
    const activityShader = ACTIVITY_SHADERS[activity];
    if (activityShader) {
      this.setEffect(activityShader, this.intensity);
      return;
    }

    // Fallback to emotion
    const emotionShader = EMOTION_SHADERS[emotion];
    this.setEffect(emotionShader, this.intensity);
  }

  /**
   * Set emotion-reactive mode
   */
  setEmotionReactive(enabled: boolean): void {
    this.emotionReactive = enabled;
    console.log(`[ShaderEffectManager] Emotion-reactive: ${enabled}`);
  }

  /**
   * Render the scene with effects
   */
  render(): void {
    this.composer.render();
  }

  /**
   * Handle window resize
   */
  setSize(width: number, height: number): void {
    this.composer.setSize(width, height);
  }

  /**
   * Get current effect info
   */
  getStatus(): { effect: ShaderEffect; intensity: number; emotionReactive: boolean } {
    return {
      effect: this.currentEffect,
      intensity: this.intensity,
      emotionReactive: this.emotionReactive,
    };
  }

  /**
   * Cleanup
   */
  dispose(): void {
    this.composer.dispose();
    this.activeEffects.clear();
  }

  // ========================================================================
  // Private Methods
  // ========================================================================

  private removeCurrentEffect(): void {
    const pass = this.activeEffects.get(this.currentEffect);
    if (pass) {
      // EffectComposer doesn't have removePass, so we recreate
      this.composer.dispose();
      this.composer = new EffectComposer(this.renderer);
      this.composer.addPass(this.renderPass);
      this.activeEffects.delete(this.currentEffect);
    }
  }

  private createEffectPass(
    effect: ShaderEffect,
    intensity: number
  ): ShaderPass | UnrealBloomPass | null {
    switch (effect) {
      case "pixelation":
        return this.createPixelationPass(intensity);
      case "glitch":
        return this.createGlitchPass(intensity);
      case "hologram":
        return this.createHologramPass(intensity);
      case "bloom":
        return this.createBloomPass(intensity);
      case "chromatic":
        return this.createChromaticPass(intensity);
      case "scanlines":
        return this.createScanlinesPass(intensity);
      default:
        return null;
    }
  }

  private createPixelationPass(intensity: number): ShaderPass {
    const pixelSize = Math.floor(3 + intensity * 7); // 3-10 pixels
    const pass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        resolution: {
          value: new THREE.Vector2(
            this.renderer.domElement.width,
            this.renderer.domElement.height
          ),
        },
        pixelSize: { value: pixelSize },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse;
        uniform vec2 resolution;
        uniform float pixelSize;
        varying vec2 vUv;

        void main() {
          vec2 dxy = pixelSize / resolution;
          vec2 coord = dxy * floor(vUv / dxy);
          gl_FragColor = texture2D(tDiffuse, coord);
        }
      `,
    });
    return pass;
  }

  private createGlitchPass(intensity: number): ShaderPass {
    const pass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        time: { value: 0 },
        intensity: { value: intensity },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse;
        uniform float time;
        uniform float intensity;
        varying vec2 vUv;

        float random(vec2 co) {
          return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
          vec2 uv = vUv;

          // RGB split
          float offset = intensity * 0.01 * sin(time * 10.0);
          vec4 cr = texture2D(tDiffuse, uv + vec2(offset, 0.0));
          vec4 cg = texture2D(tDiffuse, uv);
          vec4 cb = texture2D(tDiffuse, uv - vec2(offset, 0.0));

          // Scanline distortion
          float scanline = sin(uv.y * 100.0 + time * 5.0) * intensity * 0.1;
          uv.x += scanline;

          // Random block glitches
          float glitch = random(vec2(floor(uv.y * 10.0), floor(time * 5.0)));
          if (glitch > 0.95) {
            uv.x += (random(vec2(time)) - 0.5) * intensity * 0.1;
          }

          vec4 color = vec4(cr.r, cg.g, cb.b, 1.0);
          gl_FragColor = color;
        }
      `,
    });

    // Animate time uniform
    const animate = () => {
      pass.uniforms.time.value = performance.now() * 0.001;
      requestAnimationFrame(animate);
    };
    animate();

    return pass;
  }

  private createHologramPass(intensity: number): ShaderPass {
    const pass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        time: { value: 0 },
        intensity: { value: intensity },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse;
        uniform float time;
        uniform float intensity;
        varying vec2 vUv;

        void main() {
          vec2 uv = vUv;

          // Horizontal scanlines
          float scanline = sin(uv.y * 300.0 + time * 2.0) * 0.05;

          // Hologram flicker
          float flicker = sin(time * 10.0) * 0.05 + 0.95;

          // Cyan tint
          vec4 color = texture2D(tDiffuse, uv);
          color.rgb = mix(color.rgb, vec3(0.0, 1.0, 1.0), intensity * 0.3);
          color.rgb += scanline;
          color.rgb *= flicker;
          color.a = mix(1.0, 0.8, intensity);

          gl_FragColor = color;
        }
      `,
    });

    // Animate time uniform
    const animate = () => {
      pass.uniforms.time.value = performance.now() * 0.001;
      requestAnimationFrame(animate);
    };
    animate();

    return pass;
  }

  private createBloomPass(intensity: number): UnrealBloomPass {
    const pass = new UnrealBloomPass(
      new THREE.Vector2(window.innerWidth, window.innerHeight),
      intensity * 2.0, // strength
      0.4, // radius
      0.85 // threshold
    );
    return pass;
  }

  private createChromaticPass(intensity: number): ShaderPass {
    const pass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        intensity: { value: intensity },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse;
        uniform float intensity;
        varying vec2 vUv;

        void main() {
          vec2 uv = vUv;
          vec2 center = vec2(0.5, 0.5);
          vec2 offset = (uv - center) * intensity * 0.02;

          float r = texture2D(tDiffuse, uv + offset).r;
          float g = texture2D(tDiffuse, uv).g;
          float b = texture2D(tDiffuse, uv - offset).b;

          gl_FragColor = vec4(r, g, b, 1.0);
        }
      `,
    });
    return pass;
  }

  private createScanlinesPass(intensity: number): ShaderPass {
    const pass = new ShaderPass({
      uniforms: {
        tDiffuse: { value: null },
        time: { value: 0 },
        intensity: { value: intensity },
      },
      vertexShader: `
        varying vec2 vUv;
        void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse;
        uniform float time;
        uniform float intensity;
        varying vec2 vUv;

        void main() {
          vec2 uv = vUv;

          // CRT scanlines
          float scanline = sin(uv.y * 200.0) * intensity * 0.1;

          // Vignette effect
          vec2 center = vec2(0.5, 0.5);
          float dist = distance(uv, center);
          float vignette = 1.0 - (dist * intensity * 0.5);

          vec4 color = texture2D(tDiffuse, uv);
          color.rgb -= scanline;
          color.rgb *= vignette;

          gl_FragColor = color;
        }
      `,
    });

    // Animate time uniform
    const animate = () => {
      pass.uniforms.time.value = performance.now() * 0.001;
      requestAnimationFrame(animate);
    };
    animate();

    return pass;
  }
}
