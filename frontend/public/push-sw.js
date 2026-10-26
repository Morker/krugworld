// Web Push for the service worker that vite-plugin-pwa generates (it pulls
// this file in with importScripts). The browser wakes the worker when a push
// arrives, even with KrugWorld closed; it must show a notification right away.
// The server sends JSON: { title, body, url, tag }.

self.addEventListener('push', (event) => {
  let data = {}
  try {
    data = event.data ? event.data.json() : {}
  } catch {
    data = { body: event.data ? event.data.text() : '' }
  }
  event.waitUntil(
    self.registration.showNotification(data.title || 'KrugWorld', {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      // Same tag replaces the older one instead of stacking ("Training awaits" twice).
      tag: data.tag || undefined,
      renotify: Boolean(data.tag),
      data: { url: data.url || '/' },
    }),
  )
})

// Tapping it opens KrugWorld on the right page, reusing an open window if there is one.
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const target = new URL(event.notification.data?.url || '/', self.location.origin).href
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (windows) => {
      const open = windows.find((w) => w.url.startsWith(self.location.origin))
      if (open) {
        await open.focus()
        return open.navigate(target).catch(() => undefined)
      }
      return self.clients.openWindow(target)
    }),
  )
})
