/**
 * 間 AI Model Drop Zone
 *
 * Drag-and-drop interface for loading .glb files.
 * Shows overlay when dragging, handles file drop.
 *
 * @murphysig v1.0.0 - User-friendly model loading
 * @confidence 0.95 - Standard drag-and-drop API
 */

import { dynamicModelLoader, type DynamicModelEntry } from "../loader/DynamicModelLoader";

export type ModelDropCallback = (entry: DynamicModelEntry) => void | Promise<void>;
export type ErrorCallback = (error: Error) => void;

/**
 * Model Drop Zone Options
 */
export interface ModelDropZoneOptions {
  /** Container element to attach to */
  container: HTMLElement;
  /** Callback when model is dropped */
  onModelDrop?: ModelDropCallback;
  /** Callback on error */
  onError?: ErrorCallback;
  /** Show visual overlay on drag */
  showOverlay?: boolean;
}

/**
 * Model Drop Zone
 *
 * Handles drag-and-drop for .glb/.gltf files.
 */
export class ModelDropZone {
  private container: HTMLElement;
  private overlay: HTMLElement | null = null;
  private onModelDrop?: ModelDropCallback;
  private onError?: ErrorCallback;
  private showOverlay: boolean;

  private dragCounter = 0; // Track nested drag events

  constructor(options: ModelDropZoneOptions) {
    this.container = options.container;
    this.onModelDrop = options.onModelDrop;
    this.onError = options.onError;
    this.showOverlay = options.showOverlay ?? true;

    this.init();
  }

  private init(): void {
    // Create overlay
    if (this.showOverlay) {
      this.overlay = this.createOverlay();
      this.container.appendChild(this.overlay);
    }

    // Attach event listeners
    this.container.addEventListener("dragenter", this.handleDragEnter);
    this.container.addEventListener("dragleave", this.handleDragLeave);
    this.container.addEventListener("dragover", this.handleDragOver);
    this.container.addEventListener("drop", this.handleDrop);

    console.log("[ModelDropZone] Initialized - drop .glb files to load!");
  }

  /**
   * Programmatically trigger file picker
   */
  async openFilePicker(): Promise<void> {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".glb,.gltf";
    input.multiple = false;

    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        await this.loadFile(file);
      }
    };

    input.click();
  }

  /**
   * Dispose of resources
   */
  dispose(): void {
    this.container.removeEventListener("dragenter", this.handleDragEnter);
    this.container.removeEventListener("dragleave", this.handleDragLeave);
    this.container.removeEventListener("dragover", this.handleDragOver);
    this.container.removeEventListener("drop", this.handleDrop);

    if (this.overlay) {
      this.container.removeChild(this.overlay);
    }
  }

  // ========================================================================
  // Drag & Drop Event Handlers
  // ========================================================================

  private handleDragEnter = (e: DragEvent): void => {
    e.preventDefault();
    e.stopPropagation();

    this.dragCounter++;

    if (this.hasModelFiles(e)) {
      this.showDropOverlay();
    }
  };

  private handleDragLeave = (e: DragEvent): void => {
    e.preventDefault();
    e.stopPropagation();

    this.dragCounter--;

    if (this.dragCounter === 0) {
      this.hideDropOverlay();
    }
  };

  private handleDragOver = (e: DragEvent): void => {
    e.preventDefault();
    e.stopPropagation();

    if (this.hasModelFiles(e)) {
      e.dataTransfer!.dropEffect = "copy";
    }
  };

  private handleDrop = async (e: DragEvent): Promise<void> => {
    e.preventDefault();
    e.stopPropagation();

    this.dragCounter = 0;
    this.hideDropOverlay();

    const files = Array.from(e.dataTransfer?.files || []);
    const modelFile = files.find((f) => this.isModelFile(f));

    if (modelFile) {
      await this.loadFile(modelFile);
    } else {
      this.handleError(new Error("No .glb or .gltf file found"));
    }
  };

  // ========================================================================
  // File Loading
  // ========================================================================

  private async loadFile(file: File): Promise<void> {
    try {
      console.log(`[ModelDropZone] Loading file: ${file.name}`);
      const entry = await dynamicModelLoader.loadFromFile(file);

      if (this.onModelDrop) {
        await this.onModelDrop(entry);
      }

      this.showSuccessToast(file.name);
    } catch (error) {
      this.handleError(error as Error);
    }
  }

  // ========================================================================
  // UI Helpers
  // ========================================================================

  private createOverlay(): HTMLElement {
    const overlay = document.createElement("div");
    overlay.className = "model-drop-overlay";
    overlay.style.cssText = `
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 255, 255, 0.1);
      border: 3px dashed rgba(0, 255, 255, 0.5);
      display: none;
      align-items: center;
      justify-content: center;
      pointer-events: none;
      z-index: 1000;
      font-family: 'VT323', monospace;
      font-size: 2rem;
      color: rgba(0, 255, 255, 0.9);
      text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
    `;
    overlay.textContent = "DROP .GLB FILE TO LOAD";
    return overlay;
  }

  private showDropOverlay(): void {
    if (this.overlay) {
      this.overlay.style.display = "flex";
    }
  }

  private hideDropOverlay(): void {
    if (this.overlay) {
      this.overlay.style.display = "none";
    }
  }

  private showSuccessToast(filename: string): void {
    const toast = document.createElement("div");
    toast.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: rgba(0, 255, 0, 0.2);
      border: 1px solid rgba(0, 255, 0, 0.5);
      padding: 12px 20px;
      font-family: 'VT323', monospace;
      font-size: 1.2rem;
      color: rgba(0, 255, 0, 0.9);
      z-index: 10000;
      pointer-events: none;
      animation: fadeOut 3s forwards;
    `;
    toast.textContent = `✓ LOADED: ${filename}`;
    document.body.appendChild(toast);

    setTimeout(() => {
      document.body.removeChild(toast);
    }, 3000);
  }

  // ========================================================================
  // Validation
  // ========================================================================

  private hasModelFiles(e: DragEvent): boolean {
    const items = e.dataTransfer?.items;
    if (!items) return false;

    return Array.from(items).some((item) => {
      if (item.kind === "file") {
        const file = item.getAsFile();
        return file ? this.isModelFile(file) : false;
      }
      return false;
    });
  }

  private isModelFile(file: File): boolean {
    return file.name.toLowerCase().endsWith(".glb") || file.name.toLowerCase().endsWith(".gltf");
  }

  private handleError(error: Error): void {
    console.error("[ModelDropZone] Error:", error);

    if (this.onError) {
      this.onError(error);
    }

    // Show error toast
    const toast = document.createElement("div");
    toast.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: rgba(255, 0, 0, 0.2);
      border: 1px solid rgba(255, 0, 0, 0.5);
      padding: 12px 20px;
      font-family: 'VT323', monospace;
      font-size: 1.2rem;
      color: rgba(255, 0, 0, 0.9);
      z-index: 10000;
      pointer-events: none;
      animation: fadeOut 3s forwards;
    `;
    toast.textContent = `✗ ERROR: ${error.message}`;
    document.body.appendChild(toast);

    setTimeout(() => {
      document.body.removeChild(toast);
    }, 3000);
  }
}

// Add CSS animation
const style = document.createElement("style");
style.textContent = `
  @keyframes fadeOut {
    0% { opacity: 1; }
    70% { opacity: 1; }
    100% { opacity: 0; }
  }
`;
document.head.appendChild(style);
