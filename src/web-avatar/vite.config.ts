import { defineConfig } from "vite";

export default defineConfig({
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
});
