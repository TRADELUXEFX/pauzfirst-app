const CACHE_NAME = 'pauzfirst-v4';
const ASSETS = [
  '.',
  'index.html',
  'manifest.json',
  'favicon.png',
  'icon-192.png',
  'icon-512.png'
];

// SECURITY: Allowlist of external domains whose responses should NEVER be cached
const BYPASS_ORIGINS = [
  'supabase.co',
  'googleapis.com',
  'cdn.jsdelivr.net',
  'cdnjs.cloudflare.com',
  'fonts.googleapis.com',
  'fonts.gstatic.com'
];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
  );
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // SECURITY: Never intercept non-GET requests (POST/PUT/DELETE go straight to network)
  if (event.request.method !== 'GET') return;

  // SECURITY: Never cache API/auth calls — use allowlist approach on hostname
  const isBypassed = BYPASS_ORIGINS.some(origin => url.hostname.includes(origin));
  if (isBypassed) return;

  // SECURITY: Only cache same-origin and HTTPS requests
  if (url.protocol !== 'https:' && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') return;

  // Network-first strategy: always try network, fall back to cache for offline support
  event.respondWith(
    fetch(event.request)
      .then(response => {
        // SECURITY: Only cache valid, successful, same-origin responses
        if (
          response.ok &&
          response.status === 200 &&
          response.type === 'basic'
        ) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => {
        return caches.match(event.request).then(cached => {
          return cached || caches.match('index.html');
        });
      })
  );
});

