/**
 * M1K3 Main Application
 * Orchestrates all controllers and manages the complete real-time interface
 */
class M1K3App extends EventTarget {
    constructor(config = {}) {
        super();
        
        this.config = {
            websocketUrl: 'ws://localhost:8081',
            autoStart: true,
            enableDebug: false,
            theme: 'dark',
            ...config
        };
        
        // Core managers
        this.stateManager = null;
        this.websocketManager = null;
        this.navigationManager = null;
        
        // Controllers
        this.chatController = null;
        this.avatarController = null;
        this.statusController = null;
        this.settingsController = null;
        
        // App state
        this.initialized = false;
        this.started = false;
        this.startTime = Date.now();
        
        console.log('🚀 M1K3App created');
    }
    
    /**
     * Initialize the application
     */
    async initialize() {
        if (this.initialized) {
            console.warn('🚀 M1K3App already initialized');
            return this;
        }
        
        try {
            console.log('🚀 Initializing M1K3App...');
            
            // Initialize core managers first
            await this.initializeCore();
            
            // Initialize controllers
            await this.initializeControllers();
            
            // Setup global event handling
            this.setupGlobalEvents();
            
            // Process any queued component notifications
            this.processQueuedNotifications();
            
            this.initialized = true;
            
            console.log('🚀 M1K3App initialized successfully');
            this.emit('app.initialized');
            
            // Auto-start if configured
            if (this.config.autoStart) {
                await this.start();
            }
            
        } catch (error) {
            console.error('🚀 Failed to initialize M1K3App:', error);
            this.emit('app.error', { error, phase: 'initialization' });
            throw error;
        }
        
        return this;
    }
    
    /**
     * Initialize core managers
     */
    async initializeCore() {
        console.log('🔧 Initializing core managers...');
        
        // State Manager
        this.stateManager = new StateManager();
        
        // WebSocket Manager
        this.websocketManager = new WebSocketManager(this.stateManager, {
            url: this.config.websocketUrl
        });
        
        // Navigation Manager
        this.navigationManager = new NavigationManager(this.stateManager);
        
        console.log('🔧 Core managers initialized');
    }
    
    /**
     * Initialize controllers
     */
    async initializeControllers() {
        console.log('🎛️ Initializing controllers...');
        
        // Initialize controllers in dependency order
        this.settingsController = new SettingsController(this.stateManager, this.websocketManager);
        await this.settingsController.initialize();
        
        this.statusController = new StatusController(this.stateManager, this.websocketManager);
        await this.statusController.initialize();
        
        this.chatController = new ChatController(this.stateManager, this.websocketManager);
        await this.chatController.initialize();
        
        this.avatarController = new AvatarController(this.stateManager, this.websocketManager);
        await this.avatarController.initialize();
        
        // Initialize navigation after all controllers
        this.navigationManager.initialize();
        
        console.log('🎛️ Controllers initialized');
    }
    
    /**
     * Setup global event handling
     */
    setupGlobalEvents() {
        // Handle unhandled errors
        window.addEventListener('error', (e) => {
            this.handleGlobalError(e.error || e);
        });
        
        window.addEventListener('unhandledrejection', (e) => {
            this.handleGlobalError(e.reason);
        });
        
        // Handle visibility changes
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                this.handleVisibilityHidden();
            } else {
                this.handleVisibilityVisible();
            }
        });
        
        // Handle before unload
        window.addEventListener('beforeunload', () => {
            this.handleBeforeUnload();
        });
        
        // Expose app globally for debugging
        if (this.config.enableDebug) {
            window.m1k3App = this;
            window.m1k3Debug = {
                state: () => this.stateManager.getSnapshot(),
                stats: () => this.websocketManager?.getStatistics(),
                controllers: {
                    chat: this.chatController,
                    avatar: this.avatarController,
                    status: this.statusController,
                    settings: this.settingsController
                }
            };
            console.log('🔍 Debug mode enabled - use window.m1k3Debug');
        }
    }
    
    /**
     * Start the application
     */
    async start() {
        if (!this.initialized) {
            throw new Error('App must be initialized before starting');
        }
        
        if (this.started) {
            console.warn('🚀 M1K3App already started');
            return this;
        }
        
        try {
            console.log('🚀 Starting M1K3App...');
            
            // Connect WebSocket
            if (this.websocketManager) {
                this.websocketManager.connect();
            }
            
            // Start component notifications
            this.notifyComponentReady('m1k3_app');
            
            // Hide loading screen if it exists
            this.hideLoadingScreen();
            
            this.started = true;
            
            console.log('🚀 M1K3App started successfully');
            this.emit('app.started');
            
        } catch (error) {
            console.error('🚀 Failed to start M1K3App:', error);
            this.emit('app.error', { error, phase: 'startup' });
            throw error;
        }
        
        return this;
    }
    
    /**
     * Stop the application
     */
    async stop() {
        if (!this.started) {
            return this;
        }
        
        console.log('🚀 Stopping M1K3App...');
        
        try {
            // Disconnect WebSocket
            if (this.websocketManager) {
                this.websocketManager.disconnect();
            }
            
            // Stop controllers
            if (this.statusController) {
                this.statusController.destroy();
            }
            
            if (this.chatController) {
                this.chatController.destroy();
            }
            
            if (this.avatarController) {
                this.avatarController.destroy();
            }
            
            if (this.settingsController) {
                this.settingsController.destroy();
            }
            
            this.started = false;
            
            console.log('🚀 M1K3App stopped');
            this.emit('app.stopped');
            
        } catch (error) {
            console.error('🚀 Error stopping M1K3App:', error);
            this.emit('app.error', { error, phase: 'shutdown' });
        }
        
        return this;
    }
    
    /**
     * Restart the application
     */
    async restart() {
        await this.stop();
        await this.start();
        return this;
    }
    
    /**
     * Process queued component notifications
     */
    processQueuedNotifications() {
        if (window._queuedComponentNotifications && window._queuedComponentNotifications.length > 0) {
            console.log('🔄 Processing queued component notifications...');
            
            window._queuedComponentNotifications.forEach(([type, componentName, error]) => {
                switch (type) {
                    case 'ready':
                        this.stateManager.setComponentReady(componentName);
                        break;
                    case 'loading':
                        this.stateManager.setComponentLoading(componentName);
                        break;
                    case 'error':
                        this.stateManager.setComponentError(componentName, error);
                        break;
                }
            });
            
            window._queuedComponentNotifications = [];
            console.log('🔄 Queued notifications processed');
        }
    }
    
    /**
     * Hide loading screen
     */
    hideLoadingScreen() {
        const loadingOverlay = document.getElementById('globalLoadingOverlay');
        if (loadingOverlay) {
            console.log('🚀 Hiding loading screen');
            loadingOverlay.classList.add('hidden');
            
            setTimeout(() => {
                if (loadingOverlay.parentNode) {
                    loadingOverlay.style.display = 'none';
                }
            }, 1000);
        }
    }
    
    /**
     * Handle global errors
     */
    handleGlobalError(error) {
        console.error('🚀 Global error:', error);
        
        // Update state
        this.stateManager.emit('app.error', {
            error: error.message || error,
            stack: error.stack,
            timestamp: Date.now()
        });
        
        // Could show error notification
        this.emit('app.error', { error });
    }
    
    /**
     * Handle visibility hidden
     */
    handleVisibilityHidden() {
        console.log('🚀 App hidden');
        
        // Could pause updates, reduce polling, etc.
        if (this.statusController) {
            // Could reduce update frequency
        }
        
        this.emit('app.visibility', { visible: false });
    }
    
    /**
     * Handle visibility visible
     */
    handleVisibilityVisible() {
        console.log('🚀 App visible');
        
        // Resume normal operation
        if (this.statusController) {
            this.statusController.refreshAll();
        }
        
        this.emit('app.visibility', { visible: true });
    }
    
    /**
     * Handle before unload
     */
    handleBeforeUnload() {
        console.log('🚀 App unloading');
        
        // Save settings
        if (this.settingsController) {
            this.settingsController.saveSettings();
        }
        
        // Save chat history
        if (this.chatController) {
            this.chatController.saveMessageHistory();
        }
        
        this.emit('app.beforeunload');
    }
    
    /**
     * Notify component ready
     */
    notifyComponentReady(componentName) {
        if (this.stateManager) {
            this.stateManager.setComponentReady(componentName);
        }
    }
    
    /**
     * Notify component loading
     */
    notifyComponentLoading(componentName) {
        if (this.stateManager) {
            this.stateManager.setComponentLoading(componentName);
        }
    }
    
    /**
     * Notify component error
     */
    notifyComponentError(componentName, error) {
        if (this.stateManager) {
            this.stateManager.setComponentError(componentName, error);
        }
    }
    
    /**
     * Get application status
     */
    getStatus() {
        return {
            initialized: this.initialized,
            started: this.started,
            uptime: Date.now() - this.startTime,
            websocketConnected: this.websocketManager?.isConnected || false,
            components: this.stateManager?.getState('components') || {},
            state: this.stateManager?.getSnapshot() || null
        };
    }
    
    /**
     * Get controller by name
     */
    getController(name) {
        const controllers = {
            chat: this.chatController,
            avatar: this.avatarController,
            status: this.statusController,
            settings: this.settingsController,
            navigation: this.navigationManager,
            websocket: this.websocketManager,
            state: this.stateManager
        };
        
        return controllers[name] || null;
    }
    
    /**
     * Send message via WebSocket
     */
    send(data) {
        if (this.websocketManager && this.websocketManager.isConnected) {
            return this.websocketManager.send(data);
        }
        
        console.warn('🚀 Cannot send message - WebSocket not connected');
        return false;
    }
    
    /**
     * Switch to tab
     */
    switchTab(tabName) {
        if (this.navigationManager) {
            return this.navigationManager.switchTo(tabName);
        }
        return false;
    }
    
    /**
     * Update avatar emotion
     */
    setAvatarEmotion(emotion, intensity = 50) {
        if (this.avatarController) {
            this.avatarController.setEmotion(emotion, intensity);
        }
    }
    
    /**
     * Send chat message
     */
    sendChatMessage(message) {
        if (this.chatController) {
            // Set input and send
            const chatInput = document.getElementById('chatInput');
            if (chatInput) {
                chatInput.value = message;
                this.chatController.sendMessage();
            }
        }
    }
    
    /**
     * Update settings
     */
    updateSettings(settings) {
        if (this.settingsController) {
            this.settingsController.updateSettings(settings);
        }
    }
    
    /**
     * Get current settings
     */
    getSettings() {
        return this.settingsController?.getSettings() || {};
    }
    
    /**
     * Debug function
     */
    debug() {
        console.group('🚀 M1K3App Debug');
        console.log('Status:', this.getStatus());
        console.log('Settings:', this.getSettings());
        console.log('WebSocket Stats:', this.websocketManager?.getStatistics());
        if (this.stateManager) {
            this.stateManager.debug();
        }
        console.groupEnd();
    }
    
    /**
     * Emit custom event
     */
    emit(eventName, data) {
        const event = new CustomEvent(eventName, { detail: data });
        this.dispatchEvent(event);
    }
    
    /**
     * Cleanup
     */
    async destroy() {
        await this.stop();
        
        // Remove global references
        if (window.m1k3App === this) {
            delete window.m1k3App;
        }
        if (window.m1k3Debug) {
            delete window.m1k3Debug;
        }
        
        console.log('🚀 M1K3App destroyed');
    }
}

// Auto-initialize when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
    try {
        console.log('🚀 DOM ready, initializing M1K3App...');
        
        // Create and initialize the app
        const app = new M1K3App({
            enableDebug: true,
            autoStart: true
        });
        
        // Make globally available
        window.m1k3App = app;
        
        // Initialize
        await app.initialize();
        
        console.log('🚀 M1K3App ready!');
        
    } catch (error) {
        console.error('🚀 Failed to initialize M1K3App:', error);
        
        // Show error to user
        const errorElement = document.createElement('div');
        errorElement.className = 'app-error';
        errorElement.innerHTML = `
            <h2>❌ Application Error</h2>
            <p>Failed to initialize M1K3. Please refresh the page.</p>
            <details>
                <summary>Error Details</summary>
                <pre>${error.stack || error.message}</pre>
            </details>
        `;
        errorElement.style.cssText = `
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: rgba(0, 0, 0, 0.9);
            color: white;
            padding: 2rem;
            border-radius: 10px;
            border: 1px solid #ff4444;
            z-index: 10000;
            max-width: 500px;
            font-family: monospace;
        `;
        
        document.body.appendChild(errorElement);
    }
});

// Export for module use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = M1K3App;
}