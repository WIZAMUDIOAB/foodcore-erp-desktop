/**
 * Service Worker — FoodCore ERP v4.0 Tablet PWA (SPEC-FOODCORE-v4.0 §3)
 * Garantiza carga instantánea de archivos estáticos (HTML, CSS, JS, imágenes)
 * incluso cuando el servidor Javalin está temporalmente fuera de línea.
 */
const CACHE_NAME = 'foodcore-pwa-cache-v4.0.1';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/mesas.html',
  '/catalogo.html',
  '/manifest.json',
  '/css/styles.css',
  '/js/db.js',
  '/js/app.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Las llamadas a /api/* siempre van a red primero en tiempo real
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(event.request));
    return;
  }

  // Estrategia Network-First con Cache-Fallback para garantizar actualización en tiempo real al recargar
  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200 && event.request.method === 'GET') {
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
