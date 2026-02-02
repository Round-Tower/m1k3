import { defineConfig } from "vite";

export default defineConfig({
  server: {
    port: 5174,
    cors: true,
  },
  build: {
    lib: {
      entry: "src/index.ts",
      name: "M1K3WebAvatar",
      fileName: "web-avatar",
      formats: ["es", "umd"],
    },
    rollupOptions: {
      external: ["three"],
      output: {
        globals: {
          three: "THREE",
        },
      },
    },
  },
  optimizeDeps: {
    include: ["three"],
  },
  css: {
    postcss: "./postcss.config.js",
  },
});
