/**
 * M1K3 Single Page Application Router
 * Hash-based routing system with support for dynamic content loading
 */

class M1K3Router {
    constructor() {
        this.routes = new Map();
        this.currentRoute = null;
        this.beforeRouteChange = null;
        this.afterRouteChange = null;
        this.errorHandler = null;
        this.developmentMode = this.detectDevelopmentMode();
        
        // Initialize router
        this.init();
    }

    /**
     * Initialize the router
     */
    init() {
        // Listen for hash changes
        window.addEventListener('hashchange', () => this.handleRouteChange());
        window.addEventListener('popstate', () => this.handleRouteChange());
        
        // Handle initial route
        window.addEventListener('DOMContentLoaded', () => {
            this.handleRouteChange();
        });
    }

    /**
     * Detect if running in development mode
     */
    detectDevelopmentMode() {
        return window.location.hostname === 'localhost' || 
               window.location.hostname === '127.0.0.1' ||
               window.location.hostname.includes('.local') ||
               window.location.search.includes('dev=true');
    }

    /**
     * Register a route
     */
    route(path, config) {
        if (typeof config === 'function') {
            config = { handler: config };
        }
        
        // Store route configuration
        this.routes.set(path, {
            path,
            handler: config.handler,
            title: config.title || 'M1K3',
            requiresAuth: config.requiresAuth || false,
            developmentOnly: config.developmentOnly || false,
            preload: config.preload || [],
            ...config
        });
        
        return this;
    }

    /**
     * Navigate to a route
     */
    navigate(path, params = {}) {
        // Build query string if params provided
        const queryString = Object.keys(params).length > 0 
            ? '?' + new URLSearchParams(params).toString()
            : '';
        
        // Update location hash
        window.location.hash = path + queryString;
    }

    /**
     * Handle route changes
     */
    async handleRouteChange() {
        // Get current path from hash
        const hash = window.location.hash.slice(1) || '/';
        const [path, queryString] = hash.split('?');
        
        // Parse query parameters
        const params = new URLSearchParams(queryString || '');
        
        // Find matching route
        let route = this.routes.get(path);
        
        // Try to match wildcard routes
        if (!route) {
            for (const [routePath, routeConfig] of this.routes) {
                if (this.matchRoute(path, routePath)) {
                    route = routeConfig;
                    break;
                }
            }
        }
        
        // Handle 404
        if (!route) {
            route = this.routes.get('/404') || {
                handler: () => this.render404(),
                title: '404 - Not Found'
            };
        }
        
        // Check development mode restriction
        if (route.developmentOnly && !this.developmentMode) {
            this.navigate('/');
            return;
        }
        
        // Execute before route change hook
        if (this.beforeRouteChange) {
            const proceed = await this.beforeRouteChange(this.currentRoute, route);
            if (!proceed) return;
        }
        
        try {
            // Update document title
            if (route.title) {
                document.title = route.title;
            }
            
            // Preload resources if specified
            if (route.preload && route.preload.length > 0) {
                await this.preloadResources(route.preload);
            }
            
            // Execute route handler
            const container = document.getElementById('app') || document.body;
            
            // Add loading state
            container.classList.add('route-loading');
            
            // Execute handler
            await route.handler({
                path,
                params: Object.fromEntries(params),
                container,
                router: this
            });
            
            // Remove loading state
            container.classList.remove('route-loading');
            
            // Update current route
            this.currentRoute = route;
            
            // Execute after route change hook
            if (this.afterRouteChange) {
                await this.afterRouteChange(route);
            }
            
            // Update active navigation
            this.updateActiveNavigation(path);
            
        } catch (error) {
            console.error('Route handler error:', error);
            if (this.errorHandler) {
                this.errorHandler(error, route);
            } else {
                this.renderError(error);
            }
        }
    }

    /**
     * Match route with wildcards
     */
    matchRoute(path, pattern) {
        if (pattern.includes('*')) {
            const regex = new RegExp('^' + pattern.replace(/\*/g, '.*') + '$');
            return regex.test(path);
        }
        return path === pattern;
    }

    /**
     * Preload resources
     */
    async preloadResources(resources) {
        const promises = resources.map(resource => {
            if (resource.endsWith('.css')) {
                return this.loadCSS(resource);
            } else if (resource.endsWith('.js')) {
                return this.loadScript(resource);
            }
            return Promise.resolve();
        });
        
        return Promise.all(promises);
    }

    /**
     * Load CSS dynamically
     */
    loadCSS(href) {
        return new Promise((resolve, reject) => {
            // Check if already loaded
            if (document.querySelector(`link[href="${href}"]`)) {
                resolve();
                return;
            }
            
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = href;
            link.onload = resolve;
            link.onerror = reject;
            document.head.appendChild(link);
        });
    }

    /**
     * Load JavaScript dynamically
     */
    loadScript(src) {
        return new Promise((resolve, reject) => {
            // Check if already loaded
            if (document.querySelector(`script[src="${src}"]`)) {
                resolve();
                return;
            }
            
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.body.appendChild(script);
        });
    }

    /**
     * Update active navigation items
     */
    updateActiveNavigation(path) {
        // Remove all active classes
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
        });
        
        // Add active class to current route
        const activeLink = document.querySelector(`.nav-link[href="#${path}"]`);
        if (activeLink) {
            activeLink.classList.add('active');
        }
    }

    /**
     * Render 404 page
     */
    render404() {
        const container = document.getElementById('app') || document.body;
        container.innerHTML = `
            <div class="error-page">
                <div class="error-content">
                    <h1 class="error-code">404</h1>
                    <p class="error-message">Page not found</p>
                    <a href="#/" class="btn-primary">Return Home</a>
                </div>
            </div>
        `;
    }

    /**
     * Render error page
     */
    renderError(error) {
        const container = document.getElementById('app') || document.body;
        container.innerHTML = `
            <div class="error-page">
                <div class="error-content">
                    <h1 class="error-code">Error</h1>
                    <p class="error-message">${error.message || 'An unexpected error occurred'}</p>
                    <a href="#/" class="btn-primary">Return Home</a>
                </div>
            </div>
        `;
    }

    /**
     * Get current route info
     */
    getCurrentRoute() {
        return this.currentRoute;
    }

    /**
     * Check if route exists
     */
    hasRoute(path) {
        return this.routes.has(path);
    }

    /**
     * Remove a route
     */
    removeRoute(path) {
        return this.routes.delete(path);
    }

    /**
     * Clear all routes
     */
    clearRoutes() {
        this.routes.clear();
    }

    /**
     * Set before route change hook
     */
    beforeEach(callback) {
        this.beforeRouteChange = callback;
        return this;
    }

    /**
     * Set after route change hook
     */
    afterEach(callback) {
        this.afterRouteChange = callback;
        return this;
    }

    /**
     * Set error handler
     */
    onError(callback) {
        this.errorHandler = callback;
        return this;
    }
}

// Export router instance
const router = new M1K3Router();

// Export for ES6 modules
export default router;

// Also attach to window for global access
window.M1K3Router = M1K3Router;
window.router = router;