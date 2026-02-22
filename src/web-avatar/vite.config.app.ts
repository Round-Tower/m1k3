import { defineConfig } from "vite";
import { resolve } from "path";

/**
 * Vite config for WebView app build
 *
 * Builds src/app.ts as IIFE bundle (no ES modules) for mobile WebView compatibility.
 * Output: Single JS file with all dependencies inlined.
 */
export default defineConfig({
  // Use relative paths for assets
  base: "./",

  build: {
    outDir: "dist-app",
    // Build library mode with IIFE format
    lib: {
      entry: resolve(__dirname, "src/app.ts"),
      name: "M1K3Avatar",
      formats: ["iife"],
      fileName: () => "app.js",
    },
    rollupOptions: {
      output: {
        // Inline everything (no external dependencies)
        inlineDynamicImports: true,
        // Don't hash filenames for easier debugging
        entryFileNames: "[name].js",
        assetFileNames: "[name].[ext]",
      },
    },
    // Disable minify for easier debugging
    minify: false,
  },

  // CSS processing
  css: {
    postcss: "./postcss.config.js",
  },

  // Dev server
  server: {
    port: 5174,
    cors: true,
  },
});
