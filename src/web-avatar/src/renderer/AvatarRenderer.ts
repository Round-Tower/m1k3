/**
 * 間 AI Avatar Renderer
 *
 * Main THREE.js renderer for the 3D avatar system.
 * Handles scene setup, lighting, camera, and rendering loop.
 */

import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { GLBModelLoader, type LoadedModel } from "./GLBModelLoader";
import { AnimationController } from "../animation/AnimationController";
import type { AvatarState } from "../animation/AvatarState";
import { DEFAULT_AVATAR_STATE } from "../animation/AvatarState";
import { ModelRegistry } from "../registry/ModelRegistry";
import { ShaderEffectManager, type ShaderEffect } from "../effects/ShaderEffectManager";

/**
 * Avatar renderer options
 */
export interface AvatarRendererOptions {
  /** Container element or selector */
  container: HTMLElement | string;
  /** Initial model ID (default: "colobus") */
  modelId?: string;
  /** Base URL for model assets */
  assetsBasePath?: string;
  /** Enable orbit controls (default: true) */
  enableControls?: boolean;
  /** Background color (default: transparent) */
  backgroundColor?: string | null;
  /** Enable shadows (default: false) */
  enableShadows?: boolean;
  /** Pixel ratio (default: devicePixelRatio) */
  pixelRatio?: number;
  /** Antialias (default: true) */
  antialias?: boolean;
  /** Enable shader effects (default: true) */
  enableShaders?: boolean;
}

const DEFAULT_OPTIONS: Required<Omit<AvatarRendererOptions, "container">> = {
  modelId: "colobus",
  assetsBasePath: "assets/",
  enableControls: true,
  backgroundColor: null,
  enableShadows: false,
  pixelRatio: typeof window !== "undefined" ? window.devicePixelRatio : 1,
  antialias: true,
  enableShaders: true,
};

/**
 * Avatar Renderer
 *
 * Main class for rendering 3D avatars.
 */
export class AvatarRenderer {
  private container: HTMLElement;
  private options: Required<Omit<AvatarRendererOptions, "container">>;

  private renderer: THREE.WebGLRenderer;
  private scene: THREE.Scene;
  private camera: THREE.PerspectiveCamera;
  private controls: OrbitControls | null = null;
  private clock: THREE.Clock;

  private currentModel: LoadedModel | null = null;
  private animationController: AnimationController | null = null;
  private currentState: AvatarState = DEFAULT_AVATAR_STATE;

  private shaderManager: ShaderEffectManager | null = null;

  private animationFrameId: number | null = null;
  private isRunning = false;

  constructor(options: AvatarRendererOptions) {
    // Resolve container
    this.container =
      typeof options.container === "string"
        ? document.querySelector(options.container) as HTMLElement
        : options.container;

    if (!this.container) {
      throw new Error("Container element not found");
    }

    this.options = { ...DEFAULT_OPTIONS, ...options };

    // Initialize THREE.js
    this.scene = new THREE.Scene();
    this.clock = new THREE.Clock();

    // Setup renderer
    this.renderer = new THREE.WebGLRenderer({
      antialias: this.options.antialias,
      alpha: this.options.backgroundColor === null,
    });
    this.renderer.setPixelRatio(this.options.pixelRatio);
    this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;

    if (this.options.backgroundColor) {
      this.renderer.setClearColor(this.options.backgroundColor);
    }

    if (this.options.enableShadows) {
      this.renderer.shadowMap.enabled = true;
      this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    }

    this.container.appendChild(this.renderer.domElement);

    // Setup camera
    const aspect = this.container.clientWidth / this.container.clientHeight;
    this.camera = new THREE.PerspectiveCamera(45, aspect, 0.1, 1000);
    this.camera.position.set(0, 1, 5);

    // Setup controls
    if (this.options.enableControls) {
      this.controls = new OrbitControls(this.camera, this.renderer.domElement);
      this.controls.enableDamping = true;
      this.controls.dampingFactor = 0.05;
      this.controls.minDistance = 1;
      this.controls.maxDistance = 20;
    }

    // Setup lighting
    this.setupLighting();

    // Setup shader effects
    if (this.options.enableShaders) {
      this.shaderManager = new ShaderEffectManager(
        this.renderer,
        this.scene,
        this.camera
      );
    }

    // Handle resize
    window.addEventListener("resize", this.handleResize);
  }

  /**
   * Setup scene lighting
   */
  private setupLighting(): void {
    // Ambient light for base illumination
    const ambient = new THREE.AmbientLight(0xffffff, 0.6);
    this.scene.add(ambient);

    // Main directional light (sun-like)
    const main = new THREE.DirectionalLight(0xffffff, 0.8);
    main.position.set(5, 10, 7);
    if (this.options.enableShadows) {
      main.castShadow = true;
      main.shadow.mapSize.width = 1024;
      main.shadow.mapSize.height = 1024;
    }
    this.scene.add(main);

    // Fill light from opposite side
    const fill = new THREE.DirectionalLight(0xffffff, 0.3);
    fill.position.set(-5, 5, -7);
    this.scene.add(fill);

    // Rim light for edge definition
    const rim = new THREE.DirectionalLight(0xffffff, 0.4);
    rim.position.set(0, 5, -10);
    this.scene.add(rim);
  }

  /**
   * Normalize model position and scale
   * Centers the model and scales it to a consistent size
   */
  private normalizeModel(scene: THREE.Object3D): void {
    // Calculate bounding box
    const box = new THREE.Box3().setFromObject(scene);
    const center = box.getCenter(new THREE.Vector3());
    const size = box.getSize(new THREE.Vector3());

    // Target height for all models (consistent scale)
    const targetHeight = 2.0;
    const currentHeight = size.y;
    const scale = targetHeight / currentHeight;

    // Apply scale
    scene.scale.setScalar(scale);

    // Recalculate bounding box after scaling
    box.setFromObject(scene);
    box.getCenter(center);
    box.getSize(size);

    // Center horizontally, place feet at y=0
    scene.position.x = -center.x;
    scene.position.z = -center.z;
    scene.position.y = -box.min.y; // Put bottom of model at y=0

    console.log(`[AvatarRenderer] Normalized model: scale=${scale.toFixed(2)}, height=${size.y.toFixed(2)}`);
  }

  /**
   * Load and display a model
   *
   * @param modelId Model ID from registry
   * @param onProgress Progress callback (0-1)
   */
  async loadModel(
    modelId: string,
    onProgress?: (progress: number) => void
  ): Promise<void> {
    // Get model config
    const config = ModelRegistry.getById(modelId);
    if (!config) {
      throw new Error(`Model "${modelId}" not found in registry`);
    }

    // Unload current model
    this.unloadModel();

    // Load new model
    const url = this.options.assetsBasePath + config.path;
    this.currentModel = await GLBModelLoader.load(url, onProgress);

    // Normalize model (center and scale)
    this.normalizeModel(this.currentModel.scene);

    // Add to scene
    this.scene.add(this.currentModel.scene);

    // Setup camera to frame normalized model
    // Model is now ~2 units tall, centered at origin, feet at y=0
    const cameraDistance = 4.0;
    const cameraHeight = 1.2;
    const lookAtHeight = 0.8; // Look slightly below center for better framing

    this.camera.position.set(
      cameraDistance * 0.7,  // Slight offset for 3/4 view
      cameraHeight,
      cameraDistance * 0.7
    );
    this.camera.fov = 45;
    this.camera.updateProjectionMatrix();

    if (this.controls) {
      this.controls.target.set(0, lookAtHeight, 0);
      this.controls.update();
    }

    // Setup animation controller
    if (this.currentModel.animations.length > 0) {
      this.animationController = new AnimationController(
        this.currentModel.scene,
        this.currentModel.animations,
        this.currentModel.metadata.animations
      );

      // Apply current state
      this.animationController.updateFromState(this.currentState);
    }
  }

  /**
   * Unload current model
   */
  unloadModel(): void {
    if (this.currentModel) {
      this.scene.remove(this.currentModel.scene);
      this.currentModel.scene.traverse((object) => {
        if (object instanceof THREE.Mesh) {
          object.geometry.dispose();
          if (Array.isArray(object.material)) {
            object.material.forEach((m) => m.dispose());
          } else {
            object.material.dispose();
          }
        }
      });
    }

    if (this.animationController) {
      this.animationController.dispose();
      this.animationController = null;
    }

    this.currentModel = null;
  }

  /**
   * Update avatar state (emotion, activity, intensity)
   */
  setState(state: Partial<AvatarState>): void {
    this.currentState = { ...this.currentState, ...state };

    if (this.animationController) {
      this.animationController.updateFromState(this.currentState);
    }

    // Update shader effects based on state
    if (this.shaderManager) {
      this.shaderManager.updateForState(
        this.currentState.emotion,
        this.currentState.activity
      );
    }
  }

  /**
   * Get current avatar state
   */
  getState(): AvatarState {
    return { ...this.currentState };
  }

  /**
   * Start render loop
   */
  start(): void {
    if (this.isRunning) return;
    this.isRunning = true;
    this.clock.start();
    this.animate();
  }

  /**
   * Stop render loop
   */
  stop(): void {
    this.isRunning = false;
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }

  /**
   * Animation loop
   */
  private animate = (): void => {
    if (!this.isRunning) return;

    this.animationFrameId = requestAnimationFrame(this.animate);

    const delta = this.clock.getDelta();

    // Update animation
    if (this.animationController) {
      this.animationController.update(delta);
    }

    // Update controls
    if (this.controls) {
      this.controls.update();
    }

    // Render (with or without shaders)
    if (this.shaderManager) {
      this.shaderManager.render();
    } else {
      this.renderer.render(this.scene, this.camera);
    }
  };

  /**
   * Handle window resize
   */
  private handleResize = (): void => {
    const width = this.container.clientWidth;
    const height = this.container.clientHeight;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();

    this.renderer.setSize(width, height);

    if (this.shaderManager) {
      this.shaderManager.setSize(width, height);
    }
  };

  /**
   * Get current model metadata
   */
  getModelMetadata() {
    return this.currentModel?.metadata ?? null;
  }

  /**
   * Get animation controller
   */
  getAnimationController(): AnimationController | null {
    return this.animationController;
  }

  /**
   * Set shader effect manually (overrides emotion-reactive)
   */
  setShaderEffect(effect: ShaderEffect, intensity = 0.7): void {
    if (this.shaderManager) {
      this.shaderManager.setEmotionReactive(false);
      this.shaderManager.setEffect(effect, intensity);
    }
  }

  /**
   * Enable/disable emotion-reactive shaders
   */
  setShaderEmotionReactive(enabled: boolean): void {
    if (this.shaderManager) {
      this.shaderManager.setEmotionReactive(enabled);
      if (enabled) {
        // Re-apply shader for current state
        this.shaderManager.updateForState(
          this.currentState.emotion,
          this.currentState.activity
        );
      }
    }
  }

  /**
   * Get shader effect manager (for advanced control)
   */
  getShaderManager(): ShaderEffectManager | null {
    return this.shaderManager;
  }

  /**
   * Take screenshot
   */
  takeScreenshot(): string {
    this.renderer.render(this.scene, this.camera);
    return this.renderer.domElement.toDataURL("image/png");
  }

  /**
   * Dispose of all resources
   */
  dispose(): void {
    this.stop();
    this.unloadModel();

    window.removeEventListener("resize", this.handleResize);

    if (this.controls) {
      this.controls.dispose();
    }

    if (this.shaderManager) {
      this.shaderManager.dispose();
    }

    this.renderer.dispose();
    this.container.removeChild(this.renderer.domElement);
  }
}
