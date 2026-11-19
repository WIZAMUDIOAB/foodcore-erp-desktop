/**
 * Service Worker — FoodCore ERP v5.7.1 KDS Cocina & Bar PWA
 * Elimina únicamente cachés con su prefijo propio y aplica estrategia Network-First con verificación de origen.
 */
const CACHE_PREFIX = 'foodcore-kds-';
const CACHE_NAME = `${CACHE_PREFIX}v5.7.1`;
const URLS_TO_CACHE = [
  './',
  './index.html',
  './css/kds.css',
  './js/kds.js',
  './manifest.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(URLS_TO_CACHE))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((k) => k.startsWith(CACHE_PREFIX) && k !== CACHE_NAME)
          .map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') {
    return;
  }

  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return;
  }

  // Network-First para garantizar vistas/scripts KDS actualizados con respaldo offline
  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (
          networkResponse
          && networkResponse.status === 200
          && networkResponse.type === 'basic'
          && url.origin === self.location.origin
        ) {
          const responseClone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseClone);
          });
        }
        return networkResponse;
      })
      .catch(() => caches.match(event.request, { ignoreSearch: true }))
  );
});
