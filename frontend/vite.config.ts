import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    // Makes KrugWorld installable ("Add to Home Screen") and opens it instantly:
    // the service worker keeps the app shell (JS, CSS, icons) in the browser.
    // Data is never cached by it: /api always goes to the network, so balances
    // and sessions can't go stale or leak between users.
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/icon.svg', 'icons/apple-touch-icon.png'],
      manifest: {
        name: 'KrugWorld',
        short_name: 'KrugWorld',
        description: 'A realm of your own: money, calendar, meals, training and ideas, with Gaby the steward.',
        theme_color: '#151a22',
        background_color: '#151a22',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        categories: ['lifestyle', 'finance', 'productivity'],
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        cleanupOutdatedCaches: true,
        // Opening /ledger offline still gets the app, but never the API.
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            // The pixel and fantasy fonts: show the cached copy, refresh it quietly.
            urlPattern: /^https:\/\/fonts\.(googleapis|gstatic)\.com\//,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'krugworld-fonts',
              expiration: { maxEntries: 20, maxAgeSeconds: 60 * 60 * 24 * 365 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
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
