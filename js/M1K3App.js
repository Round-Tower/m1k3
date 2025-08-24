/**
 * M1K3 Main Application - Orchestrates all modules and manages application lifecycle
 */
class M1K3App {
    constructor() {
        this.version = '2.0.0';
        this.startTime = Date.now();
        
        // Core modules
        this.stateManager = null;
        this.navigationManager = null;
        this.webSocketManager = null;
        
        // View controllers
        this.controllers = new Map();
        
        // Application state
        this.isInitialized = false;
        this.isShuttingDown = false;
        
        console.log(`🚀 M1K3 Application v${this.version} initializing...`);
    }
    
    // Initialize the application
    async initialize() {
        if (this.isInitialized) {
            console.warn('🚀 Application already initialized');
            return;
        }
        
        try {
            // Initialize core modules
            await this.initializeCore();
            
            // Initialize controllers
            await this.initializeControllers();
            
            // Initialize navigation system
            await this.initializeNavigation();
            
            // Initialize WebSocket connection
            await this.initializeWebSocket();
            
            // Setup global event handlers
            this.setupGlobalHandlers();
            
            // Mark as initialized
            this.isInitialized = true;
            this.stateManager.set('startTime', this.startTime);
            this.stateManager.set('appVersion', this.version);
            
            console.log(`✅ M1K3 Application v${this.version} initialized successfully`);
            
            // Notify components of successful initialization
            this.stateManager.eventBus.dispatchEvent(new CustomEvent('app.initialized', {
                detail: { version: this.version, startTime: this.startTime }
            }));
            
        } catch (error) {
            console.error('❌ Failed to initialize M1K3 Application:', error);
            throw error;
        }
    }
    
    // Initialize core modules
    async initializeCore() {
        console.log('🔧 Initializing core modules...');
        
        // Initialize state management
        this.stateManager = new StateManager();
        
        // Initialize navigation manager
        this.navigationManager = new NavigationManager(this.stateManager);
        
        console.log('✅ Core modules initialized');
    }
    
    // Initialize view controllers
    async initializeControllers() {
        console.log('🎮 Initializing controllers...');
        
        // Create controller instances
        const ChatController = window.ChatController;
        const AvatarController = window.AvatarController;
        const StatusController = window.StatusController;
        const SettingsController = window.SettingsController;
        
        if (ChatController) {
            this.controllers.set('chat', new ChatController(this.stateManager, this.navigationManager));
            this.navigationManager.registerViewController('chat', this.controllers.get('chat'));
        }
        
        if (AvatarController) {
            this.controllers.set('avatar', new AvatarController(this.stateManager, this.navigationManager));
            this.navigationManager.registerViewController('avatar', this.controllers.get('avatar'));
        }
        
        if (StatusController) {
            this.controllers.set('status', new StatusController(this.stateManager, this.navigationManager));
            this.navigationManager.registerViewController('status', this.controllers.get('status'));
        }
        
        if (SettingsController) {
            this.controllers.set('settings', new SettingsController(this.stateManager, this.navigationManager));
            this.navigationManager.registerViewController('settings', this.controllers.get('settings'));
        }
        
        console.log(`✅ ${this.controllers.size} controllers initialized`);
    }
    
    // Initialize navigation system
    async initializeNavigation() {
        console.log('🧭 Initializing navigation...');
        
        await this.navigationManager.initialize();
        
        console.log('✅ Navigation system initialized');
    }
    
    // Initialize WebSocket connection
    async initializeWebSocket() {
        console.log('🌐 Initializing WebSocket connection...');
        
        this.webSocketManager = new WebSocketManager(this.stateManager);
        await this.webSocketManager.initialize();
        
        // Setup WebSocket message routing to controllers
        this.setupWebSocketRouting();
        
        console.log('✅ WebSocket system initialized');
    }
    
    // Setup WebSocket message routing
    setupWebSocketRouting() {
        // Route messages to appropriate controllers
        this.stateManager.eventBus.addEventListener('websocket.message_received', (event) => {
            const { data } = event.detail;
            
            // Route to chat controller for chat messages
            if (data.type && (data.type.startsWith('chat_') || data.type === 'text')) {
                const chatController = this.controllers.get('chat');
                if (chatController && typeof chatController.handleWebSocketMessage === 'function') {
                    chatController.handleWebSocketMessage(data.data || data);
                }
            }
            
            // Route to status controller for system/metrics messages
            if (data.type && (data.type === 'system' || data.type === 'metrics' || data.type === 'care')) {
                const statusController = this.controllers.get('status');
                if (statusController && typeof statusController.handleWebSocketMessage === 'function') {
                    statusController.handleWebSocketMessage(data.data || data);
                }
            }
        });
        
        // Handle chat streaming events
        this.stateManager.eventBus.addEventListener('chat.chunk', (event) => {
            const chatController = this.controllers.get('chat');
            if (chatController && typeof chatController.appendToLastMessage === 'function') {
                chatController.appendToLastMessage(event.detail.chunk);
            }
        });
        
        this.stateManager.eventBus.addEventListener('chat.ai_start', () => {
            const chatController = this.controllers.get('chat');
            if (chatController && typeof chatController.showTypingIndicator === 'function') {
                chatController.addMessage('', 'ai');
                chatController.showTypingIndicator(true);
            }
        });
        
        this.stateManager.eventBus.addEventListener('chat.ai_complete', () => {
            const chatController = this.controllers.get('chat');
            if (chatController && typeof chatController.showTypingIndicator === 'function') {
                chatController.showTypingIndicator(false);
            }
        });
    }
    
    // Setup global event handlers
    setupGlobalHandlers() {
        // Handle page visibility changes to optimize performance
        document.addEventListener('visibilitychange', () => {
            const isHidden = document.hidden;
            this.stateManager.set('ui.pageVisible', !isHidden);
            
            if (isHidden) {
                this.handlePageHidden();
            } else {
                this.handlePageVisible();
            }
        });
        
        // Handle beforeunload for cleanup
        window.addEventListener('beforeunload', (event) => {
            this.handleBeforeUnload(event);
        });
        
        // Handle errors globally
        window.addEventListener('error', (event) => {
            this.handleGlobalError(event.error);
        });
        
        window.addEventListener('unhandledrejection', (event) => {
            this.handleGlobalError(event.reason);
        });
        
        // Handle resize for performance optimization
        let resizeTimer;
        window.addEventListener('resize', () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                this.handleWindowResize();
            }, 250);
        });
        
        console.log('✅ Global handlers setup complete');
    }
    
    // Handle page becoming hidden (optimize performance)
    handlePageHidden() {
        console.log('📱 Page hidden - optimizing performance...');
        
        // Reduce update frequencies
        const statusController = this.controllers.get('status');
        if (statusController && typeof statusController.onHide === 'function') {
            statusController.onHide();
        }
        
        // Pause non-critical animations
        const avatarController = this.controllers.get('avatar');
        if (avatarController && typeof avatarController.onHide === 'function') {
            avatarController.onHide();
        }
    }
    
    // Handle page becoming visible (restore performance)
    handlePageVisible() {
        console.log('📱 Page visible - restoring performance...');
        
        // Restore update frequencies
        const currentTab = this.stateManager.get('currentTab');
        const controller = this.controllers.get(currentTab);
        if (controller && typeof controller.onShow === 'function') {
            controller.onShow();
        }
    }
    
    // Handle page unload
    handleBeforeUnload(event) {
        if (!this.isShuttingDown) {
            this.shutdown();
        }
    }
    
    // Handle global errors
    handleGlobalError(error) {
        console.error('🚨 Global error caught:', error);
        
        // Log to state for debugging
        const errors = this.stateManager.get('ui.errors') || [];
        errors.push({
            message: error.message || String(error),
            stack: error.stack,
            timestamp: Date.now()
        });
        
        // Keep only last 10 errors
        if (errors.length > 10) {
            errors.splice(0, errors.length - 10);
        }
        
        this.stateManager.set('ui.errors', errors);
        
        // Notify debug console if available
        if (this.webSocketManager && this.webSocketManager.debugConsole) {
            this.webSocketManager.debugConsole.logError(`Global error: ${error.message || error}`);
        }
    }
    
    // Handle window resize
    handleWindowResize() {
        const currentTab = this.stateManager.get('currentTab');
        
        // Notify current controller of resize
        if (currentTab === 'avatar') {
            const avatarController = this.controllers.get('avatar');
            if (avatarController && typeof avatarController.resizeCanvas === 'function') {
                avatarController.resizeCanvas();
                avatarController.generateAvatar();
            }
        }
        
        // Update viewport dimensions in state
        this.stateManager.update({
            'ui.viewportWidth': window.innerWidth,
            'ui.viewportHeight': window.innerHeight,
            'ui.isMobile': window.innerWidth <= 767
        });
    }
    
    // Send message via WebSocket
    sendMessage(data) {
        if (this.webSocketManager) {
            this.webSocketManager.sendMessage(data);
        } else {
            console.warn('⚠️ WebSocket manager not available');
        }
    }
    
    // Switch to a specific tab
    async switchTab(tabName) {
        if (this.navigationManager) {
            await this.navigationManager.switchToTab(tabName);
        }
    }
    
    // Get current application state
    getAppState() {
        return {
            version: this.version,
            startTime: this.startTime,
            uptime: Date.now() - this.startTime,
            initialized: this.isInitialized,
            currentTab: this.stateManager?.get('currentTab'),
            connected: this.stateManager?.get('isConnected'),
            controllers: Array.from(this.controllers.keys()),
            stats: this.webSocketManager?.getStats()
        };
    }
    
    // Get application statistics
    getStats() {
        return {
            ...this.getAppState(),
            stateSize: this.stateManager ? Object.keys(this.stateManager.state).length : 0,
            memoryUsage: this.getMemoryUsage(),
            performance: this.getPerformanceMetrics()
        };
    }
    
    // Get memory usage estimate
    getMemoryUsage() {
        if ('memory' in performance) {
            return {
                used: Math.round(performance.memory.usedJSHeapSize / 1024 / 1024),
                total: Math.round(performance.memory.totalJSHeapSize / 1024 / 1024),
                limit: Math.round(performance.memory.jsHeapSizeLimit / 1024 / 1024)
            };
        }
        return null;
    }
    
    // Get performance metrics
    getPerformanceMetrics() {
        const navigation = performance.getEntriesByType('navigation')[0];
        if (navigation) {
            return {
                loadTime: Math.round(navigation.loadEventEnd - navigation.fetchStart),
                domReady: Math.round(navigation.domContentLoadedEventEnd - navigation.fetchStart),
                firstPaint: this.getFirstPaintTime()
            };
        }
        return null;
    }
    
    // Get first paint time
    getFirstPaintTime() {
        const paintEntries = performance.getEntriesByType('paint');
        const firstPaint = paintEntries.find(entry => entry.name === 'first-paint');
        return firstPaint ? Math.round(firstPaint.startTime) : null;
    }
    
    // Restart the application
    async restart() {
        console.log('🔄 Restarting M1K3 Application...');
        
        await this.shutdown();
        
        // Clear existing DOM views (they'll be recreated)
        const existingViews = document.querySelectorAll('.view-container:not(.active)');
        existingViews.forEach(view => view.remove());
        
        await this.initialize();
        
        console.log('✅ M1K3 Application restarted');
    }
    
    // Shutdown the application
    async shutdown() {
        if (this.isShuttingDown) {
            return;
        }
        
        console.log('🛑 Shutting down M1K3 Application...');
        this.isShuttingDown = true;
        
        try {
            // Cleanup controllers
            for (const [name, controller] of this.controllers) {
                if (controller && typeof controller.cleanup === 'function') {
                    await controller.cleanup();
                }
            }
            
            // Cleanup WebSocket
            if (this.webSocketManager) {
                this.webSocketManager.cleanup();
            }
            
            // Cleanup navigation
            if (this.navigationManager) {
                // Cancel any pending animations
                this.navigationManager.isTransitioning = false;
            }
            
            // Save settings
            const settingsController = this.controllers.get('settings');
            if (settingsController && typeof settingsController.saveSettings === 'function') {
                settingsController.saveSettings();
            }
            
            console.log('✅ M1K3 Application shutdown complete');
            
        } catch (error) {
            console.error('❌ Error during shutdown:', error);
        }
    }
}

// Global app instance
let m1k3App = null;

// Initialize application when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
    try {
        m1k3App = new M1K3App();
        await m1k3App.initialize();
        
        // Make app instance available globally for debugging
        window.m1k3App = m1k3App;
        
        // Add global helper functions for debugging
        window.switchTab = (tab) => m1k3App.switchTab(tab);
        window.getAppStats = () => m1k3App.getStats();
        window.restartApp = () => m1k3App.restart();
        
    } catch (error) {
        console.error('❌ Failed to start M1K3 Application:', error);
        
        // Show error to user
        document.body.innerHTML = `
            <div style="padding: 2rem; color: #ff4444; font-family: monospace;">
                <h1>🚨 M1K3 Application Failed to Start</h1>
                <p><strong>Error:</strong> ${error.message}</p>
                <p>Please check the console for more details and try refreshing the page.</p>
                <button onclick="location.reload()" style="padding: 0.5rem 1rem; background: #444; color: white; border: 1px solid #666; border-radius: 4px; cursor: pointer;">
                    🔄 Retry
                </button>
            </div>
        `;
    }
});

// Export for module systems
if (typeof window !== 'undefined') {
    window.M1K3App = M1K3App;
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = M1K3App;
}