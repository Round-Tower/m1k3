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
        
        // Loading state management
        this.loadingOverlay = null;
        this.loadingAvatarEngine = null;
        this.loadingProgressRing = null;
        
        console.log(`🚀 M1K3 Application v${this.version} initializing...`);
    }
    
    // Initialize the application
    async initialize() {
        if (this.isInitialized) {
            console.warn('🚀 Application already initialized');
            return;
        }
        
        try {
            // Initialize loading state first
            await this.initializeLoadingState();
            
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
            
            // Simulate AI model and voice model readiness (since these are external to the web interface)
            // In a real system, these would be signaled by the Python backend
            setTimeout(() => {
                this.stateManager.setComponentReady('ai_model');
            }, 100);
            
            setTimeout(() => {
                this.stateManager.setComponentReady('voice_model');  
            }, 200);
            
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
    
    // Initialize loading state and avatar hero animation
    async initializeLoadingState() {
        console.log('🎭 Initializing loading state with avatar hero...');
        
        // Get loading overlay elements
        this.loadingOverlay = document.getElementById('globalLoadingOverlay');
        const loadingCanvas = document.getElementById('loadingAvatarCanvas');
        this.loadingProgressRing = document.getElementById('progressRingFill');
        
        if (!this.loadingOverlay || !loadingCanvas || !this.loadingProgressRing) {
            console.warn('⚠️ Loading overlay elements not found');
            return;
        }
        
        // Initialize avatar engine for loading animation
        if (typeof GameboyPixelEngine !== 'undefined') {
            try {
                this.loadingAvatarEngine = new GameboyPixelEngine(loadingCanvas, {
                    basePixelSize: 6, // Smaller base for loading screen
                    minPixelSize: 3,
                    maxPixelSize: 12,
                    mode: 'avatar',
                    layoutMode: 'fullscreen', // Use fullscreen mode for loading
                    adaptiveMode: true,
                    enableCare: true,
                    enableEInk: false,
                    debugMode: false
                });
                
                // Set up loading avatar with thinking state
                this.loadingAvatarEngine.setAvatar({
                    emotion: 'thinking',
                    style: 'robot',
                    color: '#E25303',
                    intensity: 50
                });
                
                this.loadingAvatarEngine.startAnimationLoop();
                console.log('🎭 Loading avatar engine initialized');
                
            } catch (error) {
                console.warn('⚠️ Failed to initialize loading avatar engine:', error);
            }
        }
        
        // Set initial progress
        this.updateLoadingProgress(0, 'Awakening M1K3...');
    }
    
    // Update loading progress and avatar state
    updateLoadingProgress(percentage, statusText, component = null) {
        // Update progress ring
        if (this.loadingProgressRing) {
            const circumference = 2 * Math.PI * 90; // radius = 90
            const offset = circumference - (percentage / 100) * circumference;
            this.loadingProgressRing.style.strokeDashoffset = offset;
        }
        
        // Update status text
        const progressText = document.getElementById('loadingProgressText');
        if (progressText && statusText) {
            progressText.textContent = statusText;
        }
        
        // Update component status
        if (component) {
            const componentElement = document.querySelector(`[data-component="${component}"]`);
            if (componentElement) {
                componentElement.classList.add('ready');
            }
        }
        
        // Update avatar emotion based on progress
        if (this.loadingAvatarEngine) {
            let emotion = 'thinking';
            let intensity = 50 + (percentage * 0.5); // 50-100%
            
            if (percentage >= 75) {
                emotion = 'excited';
                intensity = 80;
            } else if (percentage >= 50) {
                emotion = 'happy';
                intensity = 70;
            }
            
            this.loadingAvatarEngine.setAvatar({
                emotion,
                style: 'robot',
                color: '#E25303',
                intensity
            });
        }
        
        console.log(`🎭 Loading progress: ${percentage}% - ${statusText}`);
    }
    
    // Hide loading overlay with smooth transition
    async hideLoadingOverlay() {
        if (!this.loadingOverlay) return;
        
        console.log('🎭 Hiding loading overlay...');
        
        // Final avatar celebration
        if (this.loadingAvatarEngine) {
            this.loadingAvatarEngine.setAvatar({
                emotion: 'love',
                style: 'robot',
                color: '#00FF85',
                intensity: 100
            });
            
            // Let celebration animation play briefly
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            // Stop loading avatar animation
            this.loadingAvatarEngine.stopAnimationLoop();
        }
        
        // Smooth transition out
        this.loadingOverlay.classList.add('hidden');
        
        // Show main dashboard
        const mainDashboard = document.getElementById('mainDashboard');
        if (mainDashboard) {
            mainDashboard.style.display = 'block';
        }
        
        // Clean up after animation completes
        setTimeout(() => {
            if (this.loadingOverlay) {
                this.loadingOverlay.style.display = 'none';
            }
        }, 800);
    }
    
    // Initialize core modules
    async initializeCore() {
        console.log('🔧 Initializing core modules...');
        this.updateLoadingProgress(10, 'Initializing state management...');
        
        // Initialize state management
        this.stateManager = new StateManager();
        
        // Set up loading progress listeners
        this.setupLoadingProgressListeners();
        
        this.updateLoadingProgress(20, 'Initializing navigation...');
        
        // Initialize navigation manager
        this.navigationManager = new NavigationManager(this.stateManager);
        
        console.log('✅ Core modules initialized');
    }
    
    // Setup loading progress listeners
    setupLoadingProgressListeners() {
        // Listen for component loading events from external systems
        this.stateManager.eventBus.addEventListener('app.component_ready', (event) => {
            const { component } = event.detail;
            const progress = this.stateManager.getLoadingProgress();
            const readyComponents = Array.from(this.stateManager.state.app.readyComponents);
            
            let statusText = `${component} ready...`;
            switch (component) {
                case 'ai_model': statusText = 'AI brain connected ✨'; break;
                case 'voice_model': statusText = 'Voice synthesis ready 🎤'; break;
                case 'avatar_server': statusText = 'Avatar system online 🤖'; break;
                case 'websocket': statusText = 'Communication bridge active 🌐'; break;
            }
            
            console.log(`🔄 Component ready: ${component} (${progress}%) - Ready: [${readyComponents.join(', ')}]`);
            this.updateLoadingProgress(progress, statusText, component);
        });
        
        // Listen for app ready event
        this.stateManager.eventBus.addEventListener('app.ready', async () => {
            await new Promise(resolve => setTimeout(resolve, 500)); // Brief pause
            this.updateLoadingProgress(100, 'M1K3 is ready! 🚀');
            await new Promise(resolve => setTimeout(resolve, 1000)); // Let user see completion
            await this.hideLoadingOverlay();
        });
        
        // Fallback timeout to hide loading even if components don't signal ready
        setTimeout(async () => {
            if (this.loadingOverlay && !this.loadingOverlay.classList.contains('hidden')) {
                console.warn('⚠️ Loading timeout reached, forcing overlay hide');
                this.updateLoadingProgress(100, 'Starting with available components...');
                await new Promise(resolve => setTimeout(resolve, 1000));
                await this.hideLoadingOverlay();
            }
        }, 10000); // 10 second timeout
        
        // Listen for component errors
        this.stateManager.eventBus.addEventListener('app.component_error', (event) => {
            const { component, error } = event.detail;
            const componentElement = document.querySelector(`[data-component="${component}"]`);
            if (componentElement) {
                componentElement.classList.add('error');
            }
            console.warn(`⚠️ Component ${component} failed:`, error);
        });
    }
    
    // Initialize view controllers
    async initializeControllers() {
        console.log('🎮 Initializing controllers...');
        this.updateLoadingProgress(30, 'Creating interface controllers...');
        
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
        this.updateLoadingProgress(40, 'Setting up navigation system...');
        
        await this.navigationManager.initialize();
        
        console.log('✅ Navigation system initialized');
    }
    
    // Initialize WebSocket connection
    async initializeWebSocket() {
        console.log('🌐 Initializing WebSocket connection...');
        this.updateLoadingProgress(50, 'Establishing communication bridge...');
        
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
        
        // Process any queued component notifications
        if (typeof window.processQueuedNotifications === 'function') {
            window.processQueuedNotifications();
        }
        
        // Add global helper functions for debugging
        window.switchTab = (tab) => m1k3App.switchTab(tab);
        window.getAppStats = () => m1k3App.getStats();
        window.restartApp = () => m1k3App.restart();
        
        // Add adaptive avatar debugging functions
        window.getAvatarInfo = () => {
            const avatarController = m1k3App.controllers.get('avatar');
            return avatarController ? avatarController.getAdaptiveInfo() : null;
        };
        
        window.cycleAvatarModes = () => {
            const avatarController = m1k3App.controllers.get('avatar');
            if (avatarController) {
                const newMode = avatarController.cycleLayoutModes();
                console.log(`🎨 Switched to layout mode: ${newMode}`);
                return newMode;
            }
        };
        
        window.setAvatarMode = (mode, options = {}) => {
            const avatarController = m1k3App.controllers.get('avatar');
            if (avatarController) {
                avatarController.switchLayoutMode(mode, options);
                console.log(`🎨 Avatar layout mode set to: ${mode}`);
            }
        };
        
        window.refreshAvatar = () => {
            const avatarController = m1k3App.controllers.get('avatar');
            if (avatarController) {
                avatarController.refreshContainer();
                console.log('🎨 Avatar container refreshed');
            }
        };
        
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