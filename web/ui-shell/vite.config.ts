import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const BACKEND = "http://localhost:8080";
const GENERATED = fileURLToPath(new URL("./generated", import.meta.url));

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@generated": GENERATED,
    },
  },
  server: {
    watch: {
      // WSL2 /mnt/c mounts do not propagate inotify events; polling keeps HMR alive.
      usePolling: true,
      interval: 500,
    },
    proxy: {
      "/exceptions": BACKEND,
      "/orders": BACKEND,
      "/kotlin-order-summary": BACKEND,
    },
  },
});
