/**
 * M1K3 Service Worker
 * Basic service worker for the unified routing app
 */

const CACHE_NAME = 'm1k3-app-v1';
const CACHE_URLS = [
    '/',
    '/app.html',
    '/routes/router.js',
    '/routes/routes.config.js',
    '/routes/navigation.js',
    '/styles/design-tokens.css',
    '/styles/utilities.css',
    '/styles/animations.css',
    '/styles/background-animations.css',
    '/styles/avatar-component.css',
    '/styles/dashboard-components.css',
    '/styles/hero-section.css'
];

// Install event
self.addEventListener('install', event => {
    console.log('🔧 M1K3 Service Worker installing...');
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            console.log('📦 Caching app resources');
            return cache.addAll(CACHE_URLS.map(url => new Request(url, {
                cache: 'reload'
            })));
        })
    );
    self.skipWaiting();
});

// Activate event  
self.addEventListener('activate', event => {
    console.log('✅ M1K3 Service Worker activated');
    event.waitUntil(
        caches.keys().then(names => {
            return Promise.all(
                names.filter(name => name !== CACHE_NAME)
                     .map(name => caches.delete(name))
            );
        })
    );
    self.clients.claim();
});

// Fetch event
self.addEventListener('fetch', event => {
    // Only handle GET requests
    if (event.request.method !== 'GET') return;
    
    // Don't cache browser extensions and chrome requests
    if (event.request.url.startsWith('chrome-extension://') || 
        event.request.url.startsWith('moz-extension://')) {
        return;
    }
    
    event.respondWith(
        caches.match(event.request).then(response => {
            if (response) {
                return response;
            }
            
            return fetch(event.request).then(response => {
                // Only cache successful responses
                if (!response || response.status !== 200 || response.type !== 'basic') {
                    return response;
                }
                
                // Cache the response
                const responseToCache = response.clone();
                caches.open(CACHE_NAME).then(cache => {
                    cache.put(event.request, responseToCache);
                });
                
                return response;
            });
        }).catch(() => {
            // Return offline page or basic error
            if (event.request.headers.get('accept').includes('text/html')) {
                return new Response(`
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>M1K3 - Offline</title>
                        <style>
                            body { 
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, monospace;
                                background: #000;
                                color: #fff;
                                text-align: center;
                                padding: 50px;
                            }
                        </style>
                    </head>
                    <body>
                        <h1>M1K3 - Offline</h1>
                        <p>You're currently offline. Please check your connection.</p>
                    </body>
                    </html>
                `, {
                    headers: { 'Content-Type': 'text/html' }
                });
            }
        })
    );
});