/**
 * M1K3 PWA Service Worker
 * Handles caching, offline support, and model pre-loading
 */

const CACHE_NAME = 'm1k3-pwa-v1';
const MODEL_CACHE = 'm1k3-models-v1';
const RUNTIME_CACHE = 'm1k3-runtime-v1';

// Core app files to cache immediately
const CORE_FILES = [
    '/',
    '/index.html',
    '/manifest.json',
    '/src/styles.css',
    '/src/device-detector.js',
    '/src/model-loader.js', 
    '/src/chat-interface.js',
    '/src/app.js'
];

// External dependencies to cache
const EXTERNAL_DEPS = [
    'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/ort.min.js',
    'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/ort-wasm.wasm',
    'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/ort-wasm-simd.wasm'
];

// Model files that should be cached when accessed
const MODEL_PATTERNS = [
    /\/models\/.*\.onnx$/,
    /\/models\/.*\.json$/,
    /\/models\/.*\.bin$/
];

self.addEventListener('install', event => {
    console.log('🔧 Service Worker installing...');
    
    event.waitUntil(
        Promise.all([
            // Cache core app files
            caches.open(CACHE_NAME).then(cache => {
                console.log('📦 Caching core files...');
                return cache.addAll(CORE_FILES);
            }),
            
            // Cache external dependencies
            caches.open(RUNTIME_CACHE).then(cache => {
                console.log('📦 Caching external dependencies...');
                return cache.addAll(EXTERNAL_DEPS.map(url => new Request(url, {
                    mode: 'cors',
                    credentials: 'omit'
                })));
            })
        ]).then(() => {
            console.log('✅ Service Worker installed successfully');
            // Skip waiting to activate immediately
            return self.skipWaiting();
        }).catch(error => {
            console.error('❌ Service Worker installation failed:', error);
        })
    );
});

self.addEventListener('activate', event => {
    console.log('🚀 Service Worker activating...');
    
    event.waitUntil(
        Promise.all([
            // Clean up old caches
            caches.keys().then(cacheNames => {
                return Promise.all(
                    cacheNames.map(cacheName => {
                        if (cacheName !== CACHE_NAME && 
                            cacheName !== MODEL_CACHE && 
                            cacheName !== RUNTIME_CACHE) {
                            console.log('🗑️ Deleting old cache:', cacheName);
                            return caches.delete(cacheName);
                        }
                    })
                );
            }),
            
            // Take control of all clients
            self.clients.claim()
        ]).then(() => {
            console.log('✅ Service Worker activated successfully');
        })
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    const url = new URL(request.url);
    
    // Handle different types of requests
    if (url.origin === location.origin) {
        // Same-origin requests
        event.respondWith(handleSameOriginRequest(request));
    } else {
        // Cross-origin requests (external dependencies)
        event.respondWith(handleCrossOriginRequest(request));
    }
});

async function handleSameOriginRequest(request) {
    const url = new URL(request.url);
    
    // Handle model files with special caching
    if (isModelFile(url.pathname)) {
        return handleModelRequest(request);
    }
    
    // Handle API requests (deployment manifest, etc.)
    if (url.pathname.includes('/models/') && url.pathname.endsWith('.json')) {
        return handleAPIRequest(request);
    }
    
    // Handle core app files
    return handleCoreFileRequest(request);
}

async function handleCrossOriginRequest(request) {
    const cache = await caches.open(RUNTIME_CACHE);
    
    try {
        // Try cache first for external dependencies
        const cachedResponse = await cache.match(request);
        if (cachedResponse) {
            return cachedResponse;
        }
        
        // Fetch and cache if not found
        const response = await fetch(request);
        if (response.ok) {
            cache.put(request, response.clone());
        }
        return response;
        
    } catch (error) {
        console.error('Cross-origin request failed:', error);
        
        // Return cached version if network fails
        const cachedResponse = await cache.match(request);
        if (cachedResponse) {
            return cachedResponse;
        }
        
        // Return error response
        return new Response('Network error', { status: 503 });
    }
}

async function handleModelRequest(request) {
    const cache = await caches.open(MODEL_CACHE);
    
    try {
        // Always try to fetch fresh model files first
        const response = await fetch(request);
        if (response.ok) {
            // Cache successful model downloads
            cache.put(request, response.clone());
            
            // Notify clients about model caching
            notifyClients('model-cached', { 
                url: request.url,
                size: response.headers.get('content-length')
            });
            
            return response;
        }
    } catch (error) {
        console.warn('Model fetch failed, trying cache:', error);
    }
    
    // Fallback to cache
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
        return cachedResponse;
    }
    
    // If no cache and network failed, return error
    return new Response('Model not available offline', { 
        status: 503,
        statusText: 'Service Unavailable'
    });
}

async function handleAPIRequest(request) {
    try {
        // Try network first for API requests
        const response = await fetch(request);
        if (response.ok) {
            // Cache API responses briefly
            const cache = await caches.open(RUNTIME_CACHE);
            cache.put(request, response.clone());
            return response;
        }
    } catch (error) {
        console.warn('API request failed, trying cache:', error);
    }
    
    // Fallback to cache
    const cache = await caches.open(RUNTIME_CACHE);
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
        return cachedResponse;
    }
    
    // Generate fallback manifest if needed
    if (request.url.includes('deployment-manifest.json')) {
        return new Response(JSON.stringify({
            version: '1.0.0-offline',
            models: {
                tiny: {
                    name: 'm1k3-tiny',
                    size_mb: 100,
                    description: 'Offline fallback model'
                }
            },
            offline: true
        }), {
            headers: { 'Content-Type': 'application/json' }
        });
    }
    
    return new Response('API not available offline', { status: 503 });
}

async function handleCoreFileRequest(request) {
    const cache = await caches.open(CACHE_NAME);
    
    // Try cache first for core files (faster)
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
        return cachedResponse;
    }
    
    try {
        // Fetch and cache if not found
        const response = await fetch(request);
        if (response.ok) {
            cache.put(request, response.clone());
        }
        return response;
    } catch (error) {
        // Offline fallback
        if (request.mode === 'navigate') {
            // Return cached index.html for navigation requests
            const indexResponse = await cache.match('/index.html');
            if (indexResponse) {
                return indexResponse;
            }
        }
        
        return new Response('Offline - Content not available', { 
            status: 503,
            statusText: 'Service Unavailable'
        });
    }
}

function isModelFile(pathname) {
    return MODEL_PATTERNS.some(pattern => pattern.test(pathname));
}

function notifyClients(type, data) {
    self.clients.matchAll().then(clients => {
        clients.forEach(client => {
            client.postMessage({
                type: type,
                data: data,
                timestamp: Date.now()
            });
        });
    });
}

// Handle messages from clients
self.addEventListener('message', event => {
    const { type, data } = event.data;
    
    switch (type) {
        case 'preload-model':
            preloadModel(data.modelUrl);
            break;
            
        case 'clear-cache':
            clearAllCaches().then(() => {
                event.ports[0].postMessage({ success: true });
            });
            break;
            
        case 'get-cache-info':
            getCacheInfo().then(info => {
                event.ports[0].postMessage(info);
            });
            break;
    }
});

async function preloadModel(modelUrl) {
    try {
        console.log('🔄 Preloading model:', modelUrl);
        
        const response = await fetch(modelUrl);
        if (response.ok) {
            const cache = await caches.open(MODEL_CACHE);
            await cache.put(modelUrl, response);
            
            notifyClients('model-preloaded', { 
                url: modelUrl,
                success: true 
            });
            
            console.log('✅ Model preloaded successfully');
        }
    } catch (error) {
        console.error('❌ Model preload failed:', error);
        notifyClients('model-preloaded', { 
            url: modelUrl,
            success: false,
            error: error.message 
        });
    }
}

async function clearAllCaches() {
    const cacheNames = await caches.keys();
    return Promise.all(
        cacheNames.map(cacheName => caches.delete(cacheName))
    );
}

async function getCacheInfo() {
    const cacheNames = await caches.keys();
    const cacheInfo = {};
    
    for (const cacheName of cacheNames) {
        const cache = await caches.open(cacheName);
        const keys = await cache.keys();
        cacheInfo[cacheName] = {
            count: keys.length,
            files: keys.map(key => key.url)
        };
    }
    
    return cacheInfo;
}

// Handle background sync (if supported)
if ('serviceWorker' in navigator && 'sync' in window.ServiceWorkerRegistration.prototype) {
    self.addEventListener('sync', event => {
        if (event.tag === 'model-sync') {
            event.waitUntil(syncModels());
        }
    });
}

async function syncModels() {
    // Background sync for model updates
    try {
        const manifestResponse = await fetch('/models/deployment-manifest.json');
        if (manifestResponse.ok) {
            const manifest = await manifestResponse.json();
            // Sync logic here
            console.log('📊 Models synced in background');
        }
    } catch (error) {
        console.warn('⚠️ Background model sync failed:', error);
    }
}