/**
 * 間 AI Camera Auto-Fit (TypeScript Port)
 *
 * Automatically positions camera to frame the model optimally.
 *
 * Ported from: app/composeApp/src/commonMain/.../avatar/CameraAutoFit.kt
 */

import type { ModelMetadata, CameraConfig, Position3D } from "../registry/ModelMetadata";
import { BoundingBox } from "../registry/ModelMetadata";

/**
 * Camera auto-fit options
 */
export interface CameraAutoFitOptions {
  /** Padding multiplier around the model (default: 1.5) */
  padding?: number;
  /** Field of view in degrees (default: 45) */
  fov?: number;
  /** Horizontal angle in degrees (default: 0 = front view) */
  horizontalAngle?: number;
  /** Vertical angle in degrees (default: 10 = slightly above) */
  verticalAngle?: number;
}

const DEFAULT_OPTIONS: Required<CameraAutoFitOptions> = {
  padding: 1.5,
  fov: 45,
  horizontalAngle: 0,
  verticalAngle: 10,
};

/**
 * Camera Auto-Fit
 *
 * Calculates optimal camera position to frame a 3D model.
 */
export const CameraAutoFit = {
  /**
   * Calculate optimal camera configuration for a model
   *
   * @param metadata Model metadata with bounding box
   * @param options Optional camera settings
   * @returns Camera configuration
   */
  calculate(
    metadata: ModelMetadata,
    options?: CameraAutoFitOptions
  ): CameraConfig {
    const opts = { ...DEFAULT_OPTIONS, ...options };

    // Get model center and size
    const center = BoundingBox.center(metadata.boundingBox);
    const radius = BoundingBox.boundingSphereRadius(metadata.boundingBox);

    // Calculate camera distance based on FOV and model size
    const fovRad = (opts.fov * Math.PI) / 180;
    const distance = (radius * opts.padding) / Math.tan(fovRad / 2);

    // Calculate camera position with angles
    const horizontalRad = (opts.horizontalAngle * Math.PI) / 180;
    const verticalRad = (opts.verticalAngle * Math.PI) / 180;

    const position: Position3D = {
      x: center.x + distance * Math.sin(horizontalRad) * Math.cos(verticalRad),
      y: center.y + distance * Math.sin(verticalRad),
      z: center.z + distance * Math.cos(horizontalRad) * Math.cos(verticalRad),
    };

    return {
      position,
      lookAt: center,
      distance,
      fov: opts.fov,
    };
  },

  /**
   * Calculate camera config for front view
   */
  frontView(metadata: ModelMetadata, padding?: number): CameraConfig {
    return this.calculate(metadata, {
      padding,
      horizontalAngle: 0,
      verticalAngle: 5,
    });
  },

  /**
   * Calculate camera config for side view
   */
  sideView(metadata: ModelMetadata, padding?: number): CameraConfig {
    return this.calculate(metadata, {
      padding,
      horizontalAngle: 90,
      verticalAngle: 5,
    });
  },

  /**
   * Calculate camera config for 3/4 view (common for avatars)
   */
  threeQuarterView(metadata: ModelMetadata, padding?: number): CameraConfig {
    return this.calculate(metadata, {
      padding,
      horizontalAngle: 30,
      verticalAngle: 15,
    });
  },

  /**
   * Calculate camera config for top-down view
   */
  topView(metadata: ModelMetadata, padding?: number): CameraConfig {
    return this.calculate(metadata, {
      padding,
      horizontalAngle: 0,
      verticalAngle: 80,
    });
  },
};
