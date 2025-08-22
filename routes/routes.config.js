/**
 * M1K3 Route Configuration
 * Defines all application routes and their handlers
 */

// Import router
import router from './router.js';

/**
 * Route handlers
 */
const routeHandlers = {
    /**
     * Home/Landing page
     */
    home: async ({ container }) => {
        // Load landing page content
        const response = await fetch('/index.html');
        const html = await response.text();
        
        // Extract body content
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');
        const content = doc.querySelector('.hero-section') || doc.body;
        
        container.innerHTML = content.innerHTML;
        
        // Initialize any landing page scripts
        if (window.initLandingPage) {
            window.initLandingPage();
        }
    },

    /**
     * Avatar Dashboard
     */
    dashboard: async ({ container, params }) => {
        // Check if WebSocket is available
        if (!window.WebSocket) {
            container.innerHTML = `
                <div class="error-message">
                    <p>WebSocket support is required for the dashboard.</p>
                </div>
            `;
            return;
        }
        
        // Load dashboard content
        const response = await fetch('/m1k3.html');
        const html = await response.text();
        
        // Extract dashboard content
        const parser = new DOMParser();
        const doc = parser.parseFromString(html, 'text/html');
        const content = doc.querySelector('.dashboard-container') || doc.body;
        
        container.innerHTML = content.innerHTML;
        
        // Initialize dashboard
        if (window.initDashboard) {
            window.initDashboard(params);
        }
    },

    /**
     * PWA Chat Interface
     */
    chat: async ({ container }) => {
        container.innerHTML = `
            <div class="chat-container">
                <div class="chat-header">
                    <h2>M1K3 AI Chat</h2>
                    <span class="status-indicator">Loading...</span>
                </div>
                <iframe 
                    src="/pwa-deployment/frontend/index.html" 
                    class="chat-iframe"
                    title="M1K3 Chat Interface">
                </iframe>
            </div>
        `;
        
        // Style the iframe container
        const style = document.createElement('style');
        style.textContent = `
            .chat-container {
                width: 100%;
                height: 100vh;
                display: flex;
                flex-direction: column;
            }
            .chat-header {
                padding: var(--space-md);
                background: var(--bg-secondary);
                border-bottom: 1px solid var(--border-subtle);
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            .chat-iframe {
                flex: 1;
                width: 100%;
                border: none;
                background: var(--bg-primary);
            }
        `;
        document.head.appendChild(style);
    },

    /**
     * Settings Page
     */
    settings: async ({ container }) => {
        container.innerHTML = `
            <div class="settings-container">
                <h1>Settings</h1>
                <div class="settings-sections">
                    <section class="settings-section">
                        <h2>Appearance</h2>
                        <div class="setting-item">
                            <label for="theme">Theme</label>
                            <select id="theme" class="setting-control">
                                <option value="pure-black">Pure Black</option>
                                <option value="dark">Dark</option>
                                <option value="light">Light</option>
                            </select>
                        </div>
                        <div class="setting-item">
                            <label for="animations">Enable Animations</label>
                            <input type="checkbox" id="animations" checked>
                        </div>
                    </section>
                    
                    <section class="settings-section">
                        <h2>AI Configuration</h2>
                        <div class="setting-item">
                            <label for="model-tier">Model Tier</label>
                            <select id="model-tier" class="setting-control">
                                <option value="tiny">Tiny (2GB RAM)</option>
                                <option value="small">Small (4GB RAM)</option>
                                <option value="medium">Medium (8GB RAM)</option>
                            </select>
                        </div>
                        <div class="setting-item">
                            <label for="offline-mode">Offline Mode</label>
                            <input type="checkbox" id="offline-mode" checked>
                        </div>
                    </section>
                    
                    <section class="settings-section">
                        <h2>Avatar</h2>
                        <div class="setting-item">
                            <label for="avatar-style">Avatar Style</label>
                            <select id="avatar-style" class="setting-control">
                                <option value="robot">Robot</option>
                                <option value="organic">Organic</option>
                                <option value="crystal">Crystal</option>
                                <option value="ghost">Ghost</option>
                                <option value="energy">Energy</option>
                                <option value="cute">Cute</option>
                            </select>
                        </div>
                    </section>
                </div>
            </div>
        `;
        
        // Initialize settings handlers
        if (window.initSettings) {
            window.initSettings();
        }
    },

    /**
     * Debug Tools (Development Only)
     */
    debug: async ({ container }) => {
        container.innerHTML = `
            <div class="debug-container">
                <h1>Debug Tools</h1>
                <div class="debug-grid">
                    <a href="#/debug/websocket" class="debug-card">
                        <h3>WebSocket Logger</h3>
                        <p>Monitor WebSocket connections</p>
                    </a>
                    <a href="#/debug/avatar" class="debug-card">
                        <h3>Avatar Test</h3>
                        <p>Test avatar rendering</p>
                    </a>
                    <a href="#/debug/sound" class="debug-card">
                        <h3>Sound System</h3>
                        <p>Test audio components</p>
                    </a>
                    <a href="#/debug/engine" class="debug-card">
                        <h3>Engine Test</h3>
                        <p>Test rendering engine</p>
                    </a>
                </div>
            </div>
        `;
    },

    /**
     * Documentation
     */
    docs: async ({ container, params }) => {
        const docPage = params.page || 'architecture';
        
        container.innerHTML = `
            <div class="docs-container">
                <nav class="docs-sidebar">
                    <h3>Documentation</h3>
                    <ul>
                        <li><a href="#/docs?page=architecture">Architecture</a></li>
                        <li><a href="#/docs?page=urls">URL Structure</a></li>
                        <li><a href="#/docs?page=api">API Reference</a></li>
                        <li><a href="#/docs?page=deployment">Deployment</a></li>
                    </ul>
                </nav>
                <div class="docs-content">
                    <iframe 
                        src="/pwa-deployment/architecture.html" 
                        class="docs-iframe"
                        title="Documentation">
                    </iframe>
                </div>
            </div>
        `;
    },

    /**
     * Test Suite (Development Only)
     */
    test: async ({ container }) => {
        container.innerHTML = `
            <div class="test-container">
                <h1>Test Suite</h1>
                <div class="test-grid">
                    <a href="/test_engine_minimal.html" target="_blank" class="test-card">
                        <h3>Minimal Engine</h3>
                    </a>
                    <a href="/basic_engine_test.html" target="_blank" class="test-card">
                        <h3>Basic Engine</h3>
                    </a>
                    <a href="/simple_working_test.html" target="_blank" class="test-card">
                        <h3>Simple Test</h3>
                    </a>
                    <a href="/tests/test_dashboard.html" target="_blank" class="test-card">
                        <h3>Dashboard Test</h3>
                    </a>
                </div>
            </div>
        `;
    }
};

/**
 * Configure routes
 */
export function configureRoutes() {
    // Main application routes
    router.route('/', {
        handler: routeHandlers.home,
        title: 'M1K3 - Local AI Assistant',
        preload: [
            '/styles/design-tokens.css',
            '/styles/utilities.css',
            '/styles/animations.css',
            '/styles/hero-section.css'
        ]
    });

    router.route('/dashboard', {
        handler: routeHandlers.dashboard,
        title: 'M1K3 Avatar Dashboard',
        preload: [
            '/styles/design-tokens.css',
            '/styles/utilities.css',
            '/styles/avatar-component.css',
            '/styles/dashboard-components.css'
        ]
    });

    router.route('/chat', {
        handler: routeHandlers.chat,
        title: 'M1K3 AI Chat'
    });

    router.route('/settings', {
        handler: routeHandlers.settings,
        title: 'M1K3 Settings'
    });

    router.route('/docs', {
        handler: routeHandlers.docs,
        title: 'M1K3 Documentation'
    });

    // Development-only routes
    router.route('/debug', {
        handler: routeHandlers.debug,
        title: 'Debug Tools',
        developmentOnly: true
    });

    router.route('/debug/*', {
        handler: async ({ container, path }) => {
            const debugPage = path.split('/')[2];
            
            const debugPages = {
                'websocket': '/websocket_logger.html',
                'avatar': '/debug_test.html',
                'sound': '/tests/test_sound_fix.html',
                'engine': '/test_engine_minimal.html'
            };
            
            const pagePath = debugPages[debugPage];
            if (pagePath) {
                window.location.href = pagePath;
            } else {
                container.innerHTML = '<h1>Debug page not found</h1>';
            }
        },
        title: 'Debug',
        developmentOnly: true
    });

    router.route('/test', {
        handler: routeHandlers.test,
        title: 'Test Suite',
        developmentOnly: true
    });

    // 404 handler
    router.route('/404', {
        handler: ({ container }) => {
            container.innerHTML = `
                <div class="error-404">
                    <div class="error-content">
                        <h1>404</h1>
                        <p>The page you're looking for doesn't exist.</p>
                        <a href="#/" class="btn-primary">Go Home</a>
                    </div>
                </div>
            `;
        },
        title: '404 - Page Not Found'
    });

    // Set up route guards
    router.beforeEach(async (from, to) => {
        // Add any authentication or permission checks here
        console.log('Navigating from', from?.path, 'to', to.path);
        return true; // Allow navigation
    });

    router.afterEach(async (route) => {
        // Analytics, logging, etc.
        console.log('Route loaded:', route.path);
        
        // Scroll to top on route change
        window.scrollTo(0, 0);
    });

    // Error handling
    router.onError((error, route) => {
        console.error('Route error:', error, route);
        
        // Show error notification
        if (window.showNotification) {
            window.showNotification('error', 'Failed to load page');
        }
    });
}

// Auto-configure routes when module loads
configureRoutes();

// Export for use in other modules
export default routeHandlers;