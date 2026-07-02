/**
 * 間 AI GLB Model Loader
 *
 * Loads GLB/GLTF 3D models with animations using THREE.js GLTFLoader.
 * Extracts metadata for the avatar system.
 */

import * as THREE from "three";
import { GLTFLoader, type GLTF } from "three/examples/jsm/loaders/GLTFLoader.js";
import type {
  ModelMetadata,
  AnimationMetadata,
  MaterialInfo,
  BoundingBox,
  Position3D,
} from "../registry/ModelMetadata";

/**
 * Loaded model with scene and metadata
 */
export interface LoadedModel {
  /** THREE.js scene containing the model */
  scene: THREE.Group;
  /** Animation clips from the model */
  animations: THREE.AnimationClip[];
  /** Extracted metadata */
  metadata: ModelMetadata;
  /** Original GLTF data */
  gltf: GLTF;
}

/**
 * Loading progress callback
 */
export type LoadProgressCallback = (progress: number) => void;

/**
 * GLB Model Loader
 *
 * Singleton loader with caching support.
 */
class GLBModelLoaderImpl {
  private loader: GLTFLoader;
  private cache = new Map<string, LoadedModel>();

  constructor() {
    this.loader = new GLTFLoader();
  }

  /**
   * Load a GLB model from URL
   *
   * @param url Model URL (can be relative or absolute)
   * @param onProgress Optional progress callback (0-1)
   * @returns Loaded model with metadata
   */
  async load(url: string, onProgress?: LoadProgressCallback): Promise<LoadedModel> {
    // Check cache
    const cached = this.cache.get(url);
    if (cached) {
      // Return cloned scene to allow multiple instances
      return {
        ...cached,
        scene: cached.scene.clone(),
      };
    }

    // Load model
    const gltf = await new Promise<GLTF>((resolve, reject) => {
      this.loader.load(
        url,
        resolve,
        (event) => {
          if (onProgress && event.total > 0) {
            onProgress(event.loaded / event.total);
          }
        },
        reject
      );
    });

    // Extract metadata
    const metadata = this.extractMetadata(gltf, url);

    const loaded: LoadedModel = {
      scene: gltf.scene,
      animations: gltf.animations,
      metadata,
      gltf,
    };

    // Cache for future loads
    this.cache.set(url, loaded);

    return loaded;
  }

  /**
   * Preload model into cache without returning
   */
  async preload(url: string): Promise<void> {
    await this.load(url);
  }

  /**
   * Clear model from cache
   */
  clearCache(url?: string): void {
    if (url) {
      this.cache.delete(url);
    } else {
      this.cache.clear();
    }
  }

  /**
   * Extract metadata from loaded GLTF
   */
  private extractMetadata(gltf: GLTF, modelPath: string): ModelMetadata {
    const scene = gltf.scene;

    // Calculate bounding box
    const box = new THREE.Box3().setFromObject(scene);
    const boundingBox: BoundingBox = {
      min: { x: box.min.x, y: box.min.y, z: box.min.z },
      max: { x: box.max.x, y: box.max.y, z: box.max.z },
    };

    // Extract animations
    const animations: AnimationMetadata[] = gltf.animations.map((clip, index) => ({
      name: clip.name,
      index,
      duration: clip.duration,
      frameCount: Math.round(clip.duration * 30), // Assume 30fps
      isLoopable: !clip.name.toLowerCase().includes("death") &&
                  !clip.name.toLowerCase().includes("hit"),
    }));

    // Extract materials
    const materials: MaterialInfo[] = [];
    const seenMaterials = new Set<string>();

    scene.traverse((object) => {
      if (object instanceof THREE.Mesh && object.material) {
        const mat = Array.isArray(object.material)
          ? object.material[0]
          : object.material;
        if (mat && !seenMaterials.has(mat.uuid)) {
          seenMaterials.add(mat.uuid);
          materials.push({
            name: mat.name || `Material_${materials.length}`,
            index: materials.length,
            hasTexture: !!(mat as THREE.MeshStandardMaterial).map,
            baseColor: this.getBaseColor(mat),
          });
        }
      }
    });

    // Check for skeleton
    let hasSkeleton = false;
    scene.traverse((object) => {
      if (object instanceof THREE.SkinnedMesh) {
        hasSkeleton = true;
      }
    });

    // Count triangles
    let triangleCount = 0;
    scene.traverse((object) => {
      if (object instanceof THREE.Mesh && object.geometry) {
        const geo = object.geometry;
        if (geo.index) {
          triangleCount += geo.index.count / 3;
        } else if (geo.attributes.position) {
          triangleCount += geo.attributes.position.count / 3;
        }
      }
    });

    return {
      boundingBox,
      animations,
      materials,
      hasSkeleton,
      triangleCount: Math.round(triangleCount),
      modelPath,
    };
  }

  /**
   * Get base color from material as hex string
   */
  private getBaseColor(material: THREE.Material): string | undefined {
    if (material instanceof THREE.MeshStandardMaterial && material.color) {
      return "#" + material.color.getHexString();
    }
    return undefined;
  }
}

/** Singleton instance */
export const GLBModelLoader = new GLBModelLoaderImpl();
