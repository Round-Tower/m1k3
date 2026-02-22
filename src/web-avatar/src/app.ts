/**
 * M1K3 Web Avatar - WebView Entry Point
 *
 * Bundled as IIFE for mobile WebView compatibility (no ES modules)
 */

import { AvatarRenderer } from "./renderer/AvatarRenderer";
import type { AvatarState } from "./animation/AvatarState";

// Global initialization when DOM is ready
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}

async function init() {
  console.log("[M1K3 Avatar] Initializing...");
  console.log("[M1K3 Avatar] Current location:", window.location.href);

  try {
    // Create renderer
    const renderer = new AvatarRenderer({
      container: "#avatar-container",
      modelId: "colobus",
      enableShaders: false, // Disable shaders for now to isolate issues
      autoRotate: true,
    });

    console.log("[M1K3 Avatar] Renderer created, loading model...");

    // Load initial model with explicit relative path
    await renderer.loadModel("./models/Colobus_Animations.glb");

    console.log("[M1K3 Avatar] Model loaded, starting renderer...");
    renderer.start();

    // Expose renderer globally for Android/iOS bridge
    (window as any).renderer = renderer;

    console.log("[M1K3 Avatar] Ready!");

    // Hide loading indicator
    const loading = document.getElementById("loading");
    if (loading) loading.style.display = "none";

  } catch (error) {
    console.error("[M1K3 Avatar] Initialization failed:", error);
    const loading = document.getElementById("loading");
    if (loading) {
      loading.innerHTML = `
        <div style="color: red; text-align: center; padding: 20px;">
          <div style="font-size: 48px; margin-bottom: 10px;">❌</div>
          <div>Failed to load avatar</div>
          <div style="font-size: 12px; margin-top: 10px; color: #666;">
            ${error instanceof Error ? error.message : String(error)}
          </div>
        </div>
      `;
    }
  }
}
