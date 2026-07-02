/**
 * 間 AI Dynamic Model Loader
 *
 * Drag-and-drop .glb files + download from URLs.
 * Makes M1K3 truly model-agnostic - load ANY 3D model!
 *
 * @murphysig v1.0.0 - Universal model loading
 * @confidence 0.9 - Proven File API patterns
 * @context Removes hardcoded model limitation, enables user creativity
 */

import { GLBModelLoader, type LoadedModel } from "../renderer/GLBModelLoader";

/**
 * Dynamic model entry (user-loaded or downloaded)
 */
export interface DynamicModelEntry {
  /** Unique ID (generated or from filename) */
  id: string;
  /** Display name */
  name: string;
  /** Blob URL or data URL */
  url: string;
  /** Source (file, url, or generated) */
  source: "file" | "url" | "generated";
  /** Timestamp when added */
  timestamp: number;
  /** File size in bytes */
  size?: number;
  /** Preview thumbnail (optional) */
  thumbnail?: string;
}

/**
 * Dynamic Model Loader
 *
 * Loads models from:
 * - Drag-and-drop files
 * - Direct URLs
 * - File input
 * - (Future) Procedural generation
 */
export class DynamicModelLoader {
  private models = new Map<string, DynamicModelEntry>();
  private blobUrls = new Set<string>();

  /**
   * Load model from File object (drag-and-drop or file input)
   */
  async loadFromFile(file: File): Promise<DynamicModelEntry> {
    if (!this.isValidModelFile(file)) {
      throw new Error(`Invalid file type: ${file.type}. Must be .glb or .gltf`);
    }

    // Create blob URL
    const blobUrl = URL.createObjectURL(file);
    this.blobUrls.add(blobUrl);

    // Generate ID from filename
    const id = this.generateIdFromFilename(file.name);
    const name = file.name.replace(/\.(glb|gltf)$/i, "");

    // Pre-load to validate
    try {
      await GLBModelLoader.load(blobUrl);
    } catch (error) {
      URL.revokeObjectURL(blobUrl);
      this.blobUrls.delete(blobUrl);
      throw new Error(`Failed to load model: ${error.message}`);
    }

    const entry: DynamicModelEntry = {
      id,
      name,
      url: blobUrl,
      source: "file",
      timestamp: Date.now(),
      size: file.size,
    };

    this.models.set(id, entry);
    console.log(`[DynamicModelLoader] Loaded from file: ${name} (${this.formatFileSize(file.size)})`);

    return entry;
  }

  /**
   * Load model from URL (CORS-compatible)
   */
  async loadFromUrl(url: string, name?: string): Promise<DynamicModelEntry> {
    // Validate URL
    if (!this.isValidModelUrl(url)) {
      throw new Error(`Invalid model URL: must end with .glb or .gltf`);
    }

    // Fetch and convert to blob URL for consistency
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`Failed to fetch model: ${response.statusText}`);
    }

    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    this.blobUrls.add(blobUrl);

    // Pre-load to validate
    try {
      await GLBModelLoader.load(blobUrl);
    } catch (error) {
      URL.revokeObjectURL(blobUrl);
      this.blobUrls.delete(blobUrl);
      throw new Error(`Failed to load model: ${error.message}`);
    }

    // Extract name from URL if not provided
    const fileName = name || url.split("/").pop()?.replace(/\.(glb|gltf)$/i, "") || "downloaded-model";
    const id = this.generateIdFromFilename(fileName);

    const entry: DynamicModelEntry = {
      id,
      name: fileName,
      url: blobUrl,
      source: "url",
      timestamp: Date.now(),
      size: blob.size,
    };

    this.models.set(id, entry);
    console.log(`[DynamicModelLoader] Loaded from URL: ${fileName}`);

    return entry;
  }

  /**
   * Get all loaded models
   */
  getAllModels(): DynamicModelEntry[] {
    return Array.from(this.models.values()).sort((a, b) => b.timestamp - a.timestamp);
  }

  /**
   * Get model by ID
   */
  getModel(id: string): DynamicModelEntry | undefined {
    return this.models.get(id);
  }

  /**
   * Remove model
   */
  removeModel(id: string): boolean {
    const entry = this.models.get(id);
    if (!entry) return false;

    // Revoke blob URL
    if (this.blobUrls.has(entry.url)) {
      URL.revokeObjectURL(entry.url);
      this.blobUrls.delete(entry.url);
    }

    this.models.delete(id);
    console.log(`[DynamicModelLoader] Removed model: ${entry.name}`);
    return true;
  }

  /**
   * Clear all models
   */
  clearAll(): void {
    // Revoke all blob URLs
    for (const url of this.blobUrls) {
      URL.revokeObjectURL(url);
    }

    this.blobUrls.clear();
    this.models.clear();
    console.log(`[DynamicModelLoader] Cleared all models`);
  }

  /**
   * Cleanup on dispose
   */
  dispose(): void {
    this.clearAll();
  }

  // ========================================================================
  // Validation
  // ========================================================================

  private isValidModelFile(file: File): boolean {
    const validTypes = ["model/gltf-binary", "model/gltf+json", "application/octet-stream"];
    const validExtensions = [".glb", ".gltf"];

    // Check MIME type
    if (file.type && validTypes.includes(file.type)) {
      return true;
    }

    // Fallback: check extension
    return validExtensions.some((ext) => file.name.toLowerCase().endsWith(ext));
  }

  private isValidModelUrl(url: string): boolean {
    const lowerUrl = url.toLowerCase();
    return lowerUrl.endsWith(".glb") || lowerUrl.endsWith(".gltf");
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private generateIdFromFilename(filename: string): string {
    // Remove extension and sanitize
    let id = filename
      .replace(/\.(glb|gltf)$/i, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");

    // Ensure uniqueness
    let uniqueId = id;
    let counter = 1;
    while (this.models.has(uniqueId)) {
      uniqueId = `${id}-${counter}`;
      counter++;
    }

    return uniqueId;
  }

  private formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}

/**
 * Singleton instance
 */
export const dynamicModelLoader = new DynamicModelLoader();
