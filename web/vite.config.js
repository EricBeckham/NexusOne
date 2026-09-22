import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const apiTarget = process.env.NEXUS_API_ORIGIN || "http://127.0.0.1:8080";

export default defineConfig({
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    port: Number(process.env.NEXUS_WEB_PORT || 5173),
    proxy: {
      "/api": { target: apiTarget, changeOrigin: false },
      "/actuator": { target: apiTarget, changeOrigin: false },
    },
  },
  preview: {
    host: "127.0.0.1",
    port: 4173,
    proxy: {
      "/api": { target: apiTarget, changeOrigin: false },
      "/actuator": { target: apiTarget, changeOrigin: false },
    },
  },
});
