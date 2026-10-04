import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// Vite config — https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // Forward /api/* to the Spring Boot backend so the browser
    // never hits a cross-origin request (no CORS config needed).
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
