/**
 * Service Worker — FoodCore ERP v5.7.1 Tablet PWA
 * Aisla su propio espacio de caché por prefijo, exige mismo origen y aplica Network-First.
 */
const CACHE_PREFIX = 'foodcore-pwa-cache-';
const CACHE_NAME = `${CACHE_PREFIX}v5.7.1`;
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/mesas.html',
  '/catalogo.html',
  '/pin-login.html',
  '/manifest.json',
  '/css/styles.css',
  '/js/db.js',
  '/js/app.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(STATIC_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') {
    return;
  }

  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  // Las llamadas a /api/* son estrictamente Network-Only (el soporte offline de pedidos lo maneja IndexedDB en db.js)
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  // Estrategia Network-First con Cache-Fallback verificando mismo origen antes de cachear
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
