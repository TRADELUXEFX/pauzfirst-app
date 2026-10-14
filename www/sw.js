const CACHE_NAME = 'pauzfirst-v6';
const ASSETS = [
  '.',
  'index.html',
  'manifest.json',
  'favicon.png',
  'icon-192.png',
  'icon-512.png'
];

// Libraries, icons and fonts the page needs to start. They are version-pinned or static, so they are safe to keep
// on the phone: after one online visit the app also starts offline.
const CDN_PRECACHE = [
  'https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.45.0',
  'https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css',
  'https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap'
];
const CDN_HOSTS = ['cdn.jsdelivr.net', 'cdnjs.cloudflare.com', 'fonts.googleapis.com', 'fonts.gstatic.com'];

// How long to wait for the network before using the saved copy (a weak connection should not freeze the app)
const NETWORK_TIMEOUT_MS = 4000;

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      cache.addAll(ASSETS).then(() =>
        // one failing CDN file must not stop the install
        Promise.all(CDN_PRECACHE.map(u =>
          fetch(new Request(u, { mode: 'cors', credentials: 'omit' }))
            .then(r => (r.ok ? cache.put(u, r) : null))
            .catch(() => null)
        ))
      )
    )
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

function fetchWithTimeout(request, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), ms);
    fetch(request).then(
      r => { clearTimeout(timer); resolve(r); },
      e => { clearTimeout(timer); reject(e); }
    );
  });
}

function putIfGood(request, response) {
  if (response && response.ok && response.status === 200 &&
      (response.type === 'basic' || response.type === 'cors')) {
    const clone = response.clone();
    caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
  }
}

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);

  // SECURITY: Never intercept non-GET requests (POST/PUT/DELETE go straight to network)
  if (request.method !== 'GET') return;

  // SECURITY: API and auth calls are never cached
  if (url.hostname.includes('supabase.co')) return;

  // SECURITY: Only https (or localhost)
  if (url.protocol !== 'https:' && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') return;

  // Libraries, icons and fonts: use the saved copy first, refresh it quietly in the background
  if (CDN_HOSTS.some(h => url.hostname === h)) {
    event.respondWith(
      caches.match(request).then(cached => {
        const refresh = fetch(request).then(r => { putIfGood(request, r); return r; }).catch(() => null);
        if (cached) return cached;
        return refresh.then(r => r || Response.error());
      })
    );
    return;
  }

  // Everything else on this site: network first (so updates show up), saved copy when offline or too slow
  const isPage = request.mode === 'navigate';
  event.respondWith(
    fetchWithTimeout(request, NETWORK_TIMEOUT_MS)
      .then(response => {
        putIfGood(request, response);
        return response;
      })
      .catch(() =>
        caches.match(request).then(cached => {
          if (cached) return cached;
          // only a page load may fall back to the app shell; other files just fail
          return isPage ? caches.match('index.html') : Response.error();
        })
      )
  );
});
