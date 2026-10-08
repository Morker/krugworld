import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 5173 is taken by another local project; fail loudly rather than
    // silently drifting to a different port.
    port: 5174,
    strictPort: true,
    // The browser only ever talks to Vite. Anything under /api is passed on
    // to Spring Boot, so the frontend and API share one origin in dev — no
    // CORS setup, and session cookies just work.
    proxy: {
      '/api': 'http://localhost:8085',
    },
  },
})
