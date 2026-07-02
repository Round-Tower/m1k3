/**
 * 間 AI 3D Model Introspection System (TypeScript Port)
 *
 * Platform-agnostic metadata classes for generic GLB/GLTF model loading.
 * Supports automatic bounding box calculation, animation discovery,
 * material introspection, and camera auto-fitting.
 *
 * Ported from: app/composeApp/src/commonMain/.../avatar/ModelMetadata.kt
 */

/**
 * 3D position in world space
 */
export interface Position3D {
  x: number;
  y: number;
  z: number;
}

export const Position3D = {
  ZERO: { x: 0, y: 0, z: 0 } as Position3D,

  add(a: Position3D, b: Position3D): Position3D {
    return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z };
  },

  subtract(a: Position3D, b: Position3D): Position3D {
    return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
  },

  scale(p: Position3D, scalar: number): Position3D {
    return { x: p.x * scalar, y: p.y * scalar, z: p.z * scalar };
  },
};

/**
 * Axis-aligned bounding box (AABB)
 *
 * Encapsulates the spatial extent of a 3D model.
 * Used for camera auto-fitting and collision detection.
 */
export interface BoundingBox {
  min: Position3D;
  max: Position3D;
}

export const BoundingBox = {
  EMPTY: {
    min: { x: Infinity, y: Infinity, z: Infinity },
    max: { x: -Infinity, y: -Infinity, z: -Infinity },
  } as BoundingBox,

  UNIT: {
    min: { x: -0.5, y: -0.5, z: -0.5 },
    max: { x: 0.5, y: 0.5, z: 0.5 },
  } as BoundingBox,

  width(box: BoundingBox): number {
    return box.max.x - box.min.x;
  },

  height(box: BoundingBox): number {
    return box.max.y - box.min.y;
  },

  depth(box: BoundingBox): number {
    return box.max.z - box.min.z;
  },

  center(box: BoundingBox): Position3D {
    return {
      x: (box.min.x + box.max.x) / 2,
      y: (box.min.y + box.max.y) / 2,
      z: (box.min.z + box.max.z) / 2,
    };
  },

  maxDimension(box: BoundingBox): number {
    return Math.max(
      BoundingBox.width(box),
      BoundingBox.height(box),
      BoundingBox.depth(box)
    );
  },

  boundingSphereRadius(box: BoundingBox): number {
    const halfWidth = BoundingBox.width(box) / 2;
    const halfHeight = BoundingBox.height(box) / 2;
    const halfDepth = BoundingBox.depth(box) / 2;
    return Math.sqrt(
      halfWidth * halfWidth + halfHeight * halfHeight + halfDepth * halfDepth
    );
  },

  isValid(box: BoundingBox): boolean {
    return (
      box.min.x <= box.max.x &&
      box.min.y <= box.max.y &&
      box.min.z <= box.max.z
    );
  },
};

/**
 * Animation metadata extracted from GLB file
 */
export interface AnimationMetadata {
  /** Animation name (e.g., "Idle_A", "Walk", "Run") */
  name: string;
  /** Index in model's animation array */
  index: number;
  /** Duration in seconds */
  duration: number;
  /** Total number of frames */
  frameCount: number;
  /** Whether animation can loop smoothly */
  isLoopable: boolean;
}

export const AnimationMetadata = {
  /** Frames per second (calculated) */
  fps(anim: AnimationMetadata): number {
    return anim.duration > 0 ? anim.frameCount / anim.duration : 0;
  },

  /** Check if animation name matches pattern (case-insensitive) */
  matches(anim: AnimationMetadata, pattern: string): boolean {
    return anim.name.toLowerCase().includes(pattern.toLowerCase());
  },

  /** Check if animation is an idle variant */
  isIdle(anim: AnimationMetadata): boolean {
    return AnimationMetadata.matches(anim, "idle");
  },

  /** Check if animation is a movement type */
  isMovement(anim: AnimationMetadata): boolean {
    return (
      AnimationMetadata.matches(anim, "walk") ||
      AnimationMetadata.matches(anim, "run") ||
      AnimationMetadata.matches(anim, "fly") ||
      AnimationMetadata.matches(anim, "swim")
    );
  },
};

/**
 * Material information extracted from model
 */
export interface MaterialInfo {
  /** Material name */
  name: string;
  /** Index in material array */
  index: number;
  /** Whether material uses texture maps */
  hasTexture: boolean;
  /** Base color (hex string, e.g., "#FF5733") */
  baseColor?: string;
}

/**
 * Complete model metadata
 *
 * Comprehensive introspection data for a 3D model.
 * Calculated once and cached for performance.
 */
export interface ModelMetadata {
  /** Spatial extent of model */
  boundingBox: BoundingBox;
  /** List of available animations */
  animations: AnimationMetadata[];
  /** List of materials used */
  materials: MaterialInfo[];
  /** Whether model has bone structure (rigged) */
  hasSkeleton: boolean;
  /** Approximate polygon count */
  triangleCount: number;
  /** Original file path */
  modelPath: string;
}

export const ModelMetadataUtils = {
  /** Geometric center of model (from bounding box) */
  center(metadata: ModelMetadata): Position3D {
    return BoundingBox.center(metadata.boundingBox);
  },

  /** Total animation count */
  animationCount(metadata: ModelMetadata): number {
    return metadata.animations.length;
  },

  /** Get animation by name (case-insensitive) */
  getAnimationByName(
    metadata: ModelMetadata,
    name: string
  ): AnimationMetadata | undefined {
    return metadata.animations.find(
      (a) => a.name.toLowerCase() === name.toLowerCase()
    );
  },

  /** Get animations matching pattern */
  getAnimationsMatching(
    metadata: ModelMetadata,
    pattern: string
  ): AnimationMetadata[] {
    return metadata.animations.filter((a) =>
      AnimationMetadata.matches(a, pattern)
    );
  },

  /** Get first idle animation (fallback for unknown states) */
  getDefaultAnimation(metadata: ModelMetadata): AnimationMetadata | undefined {
    return (
      metadata.animations.find((a) => AnimationMetadata.isIdle(a)) ??
      metadata.animations[0]
    );
  },

  /** Check if model is suitable for avatar display */
  isAvatarReady(metadata: ModelMetadata): boolean {
    return metadata.hasSkeleton && metadata.animations.length > 0;
  },

  /** Create empty metadata (for loading state) */
  empty(modelPath: string): ModelMetadata {
    return {
      boundingBox: BoundingBox.EMPTY,
      animations: [],
      materials: [],
      hasSkeleton: false,
      triangleCount: 0,
      modelPath,
    };
  },
};

/**
 * Camera configuration calculated from model metadata
 */
export interface CameraConfig {
  /** Camera position in world space */
  position: Position3D;
  /** Point camera should look at (usually model center) */
  lookAt: Position3D;
  /** Distance from camera to lookAt point */
  distance: number;
  /** Field of view in degrees */
  fov: number;
}

export const CameraConfig = {
  DEFAULT: {
    position: { x: 0, y: 0, z: 5 },
    lookAt: Position3D.ZERO,
    distance: 5,
    fov: 45,
  } as CameraConfig,

  /** Near clipping plane (10% of distance) */
  nearPlane(config: CameraConfig): number {
    return config.distance * 0.1;
  },

  /** Far clipping plane (300% of distance) */
  farPlane(config: CameraConfig): number {
    return config.distance * 3.0;
  },
};
