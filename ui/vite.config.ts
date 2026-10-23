import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  // maplibre-gl alone is ~1.1 MB minified (≈290 kB gzipped) and already split into its own chunk
  build: { chunkSizeWarningLimit: 1200 },
  worker: { format: 'es' }, // MapLibre starts its worker as an ES module
  server: {
    port: 5173,
    // dev only: the browser calls /api on Vite, which forwards to the backend (no CORS needed)
    proxy: { '/api': 'http://localhost:8080' },
  },
})
