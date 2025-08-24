/**
 * M1K3 Navigation Manager - Handles lazy loading and tab navigation
 */
class NavigationManager {
    constructor(stateManager) {
        this.stateManager = stateManager;
        this.views = new Map();
        this.activeView = null;
        this.viewControllers = new Map();
        this.isTransitioning = false;
        this.swipeThreshold = 50;
        this.swipeStartX = 0;
        this.swipeStartTime = 0;
        
        this.tabOrder = ['chat', 'avatar', 'status', 'settings'];
        
        console.log('🧭 NavigationManager initialized');
    }
    
    // Initialize navigation system
    async initialize() {
        this.setupTabNavigation();
        this.setupSwipeGestures();
        this.setupKeyboardNavigation();
        
        // Hide all views initially
        this.hideAllViews();
        
        // Initialize with current tab from state
        const currentTab = this.stateManager.get('currentTab');
        await this.switchToTab(currentTab, false); // Don't animate initial load
        
        console.log('🧭 Navigation system ready');
    }
    
    // Register a view controller
    registerViewController(tabName, controller) {
        this.viewControllers.set(tabName, controller);
        console.log(`🧭 Registered controller for ${tabName}`);
    }
    
    // Setup tab navigation event listeners
    setupTabNavigation() {
        const tabItems = document.querySelectorAll('.tab-item');
        
        tabItems.forEach(tab => {
            tab.addEventListener('click', async (e) => {
                e.preventDefault();
                const targetTab = tab.getAttribute('data-tab');
                await this.switchToTab(targetTab);
            });
        });
    }
    
    // Setup swipe gestures for mobile
    setupSwipeGestures() {
        if (!('ontouchstart' in window)) return;
        
        const dashboard = document.querySelector('.dashboard');
        if (!dashboard) return;
        
        let startX = 0;
        let startY = 0;
        let startTime = 0;
        
        dashboard.addEventListener('touchstart', (e) => {
            const touch = e.touches[0];
            startX = touch.clientX;
            startY = touch.clientY;
            startTime = Date.now();
        }, { passive: true });
        
        dashboard.addEventListener('touchend', async (e) => {
            const touch = e.changedTouches[0];
            const endX = touch.clientX;
            const endY = touch.clientY;
            const endTime = Date.now();
            
            const deltaX = endX - startX;
            const deltaY = endY - startY;
            const deltaTime = endTime - startTime;
            
            // Check if it's a valid swipe (horizontal, fast enough, far enough)
            if (Math.abs(deltaX) > Math.abs(deltaY) && 
                Math.abs(deltaX) > this.swipeThreshold && 
                deltaTime < 300) {
                
                const currentTab = this.stateManager.get('currentTab');
                const currentIndex = this.tabOrder.indexOf(currentTab);
                let newIndex;
                
                if (deltaX > 0) {
                    // Swipe right - go to previous tab
                    newIndex = Math.max(0, currentIndex - 1);
                } else {
                    // Swipe left - go to next tab
                    newIndex = Math.min(this.tabOrder.length - 1, currentIndex + 1);
                }
                
                if (newIndex !== currentIndex) {
                    await this.switchToTab(this.tabOrder[newIndex]);
                }
            }
        }, { passive: true });
    }
    
    // Setup keyboard navigation
    setupKeyboardNavigation() {
        document.addEventListener('keydown', async (e) => {
            // Only handle if no input is focused
            if (document.activeElement.tagName === 'INPUT' || 
                document.activeElement.tagName === 'TEXTAREA') {
                return;
            }
            
            const currentTab = this.stateManager.get('currentTab');
            const currentIndex = this.tabOrder.indexOf(currentTab);
            
            if (e.key === 'ArrowLeft' && currentIndex > 0) {
                e.preventDefault();
                await this.switchToTab(this.tabOrder[currentIndex - 1]);
            } else if (e.key === 'ArrowRight' && currentIndex < this.tabOrder.length - 1) {
                e.preventDefault();
                await this.switchToTab(this.tabOrder[currentIndex + 1]);
            } else if (e.key >= '1' && e.key <= '4') {
                e.preventDefault();
                const tabIndex = parseInt(e.key) - 1;
                if (tabIndex < this.tabOrder.length) {
                    await this.switchToTab(this.tabOrder[tabIndex]);
                }
            }
        });
    }
    
    // Switch to a specific tab
    async switchToTab(tabName, animate = true) {
        if (this.isTransitioning || !this.tabOrder.includes(tabName)) {
            return;
        }
        
        const currentTab = this.stateManager.get('currentTab');
        if (currentTab === tabName) {
            return;
        }
        
        this.isTransitioning = true;
        
        try {
            // Update tab visual state immediately for responsiveness
            this.updateTabVisualState(tabName);
            
            // Hide current view
            if (this.activeView) {
                await this.hideView(currentTab, animate);
            }
            
            // Load and show new view
            await this.showView(tabName, animate);
            
            // Update state
            this.stateManager.set('currentTab', tabName);
            
            // Handle tab-specific logic
            await this.handleTabSpecificLogic(tabName);
            
            console.log(`🧭 Switched to ${tabName} tab`);
            
        } catch (error) {
            console.error('🧭 Error switching tabs:', error);
        } finally {
            this.isTransitioning = false;
        }
    }
    
    // Update visual state of tabs
    updateTabVisualState(activeTab) {
        const tabItems = document.querySelectorAll('.tab-item');
        
        tabItems.forEach(tab => {
            const isActive = tab.getAttribute('data-tab') === activeTab;
            tab.classList.toggle('active', isActive);
        });
    }
    
    // Hide a view
    async hideView(tabName, animate) {
        const viewElement = document.getElementById(tabName + 'View');
        if (!viewElement) return;
        
        // Notify controller before hiding
        const controller = this.viewControllers.get(tabName);
        if (controller && typeof controller.onHide === 'function') {
            await controller.onHide();
        }
        
        if (animate) {
            // Animate out
            viewElement.style.opacity = '0';
            viewElement.style.transform = 'translateY(-10px)';
            
            await new Promise(resolve => setTimeout(resolve, 150));
        }
        
        viewElement.classList.remove('active');
        viewElement.style.display = 'none';
        
        // Clean up resources for inactive views
        this.cleanupView(tabName);
    }
    
    // Show a view
    async showView(tabName, animate) {
        let viewElement = document.getElementById(tabName + 'View');
        
        // Check if view needs content (contains only loading indicator)
        if (viewElement && viewElement.querySelector('.loading-indicator')) {
            const content = await this.loadViewContent(tabName);
            viewElement.innerHTML = content;
            console.log(`🧭 Populated ${tabName} view with content`);
        } else if (!viewElement) {
            // Create new view if it doesn't exist
            await this.createView(tabName);
            viewElement = document.getElementById(tabName + 'View');
        }
        
        // Initialize view content
        await this.initializeView(tabName);
        
        // Show view
        viewElement.style.display = 'flex';
        viewElement.classList.add('active');
        
        if (animate) {
            // Set initial animation state
            viewElement.style.opacity = '0';
            viewElement.style.transform = 'translateY(10px)';
            
            // Force reflow then animate in
            requestAnimationFrame(() => {
                viewElement.style.opacity = '1';
                viewElement.style.transform = 'translateY(0)';
            });
        } else {
            viewElement.style.opacity = '1';
            viewElement.style.transform = 'translateY(0)';
        }
        
        this.activeView = tabName;
        
        // Notify controller after showing
        const controller = this.viewControllers.get(tabName);
        if (controller && typeof controller.onShow === 'function') {
            await controller.onShow();
        }
    }
    
    // Create a view element (lazy loading)
    async createView(tabName) {
        const container = document.querySelector('.dashboard');
        if (!container) return;
        
        // Create view container
        const viewElement = document.createElement('div');
        viewElement.className = 'view-container ' + tabName + '-view';
        viewElement.id = tabName + 'View';
        
        // Load view content based on tab
        const content = await this.loadViewContent(tabName);
        viewElement.innerHTML = content;
        
        container.appendChild(viewElement);
        
        console.log(`🧭 Lazy loaded ${tabName} view`);
    }
    
    // Load content for a specific view
    async loadViewContent(tabName) {
        switch (tabName) {
            case 'chat':
                return this.getChatViewHTML();
            case 'avatar':
                return this.getAvatarViewHTML();
            case 'status':
                return this.getStatusViewHTML();
            case 'settings':
                return this.getSettingsViewHTML();
            default:
                return '<div>View not found</div>';
        }
    }
    
    // Initialize view with data and event listeners
    async initializeView(tabName) {
        const controller = this.viewControllers.get(tabName);
        if (controller && typeof controller.initialize === 'function') {
            await controller.initialize();
        }
    }
    
    // Clean up resources when view becomes inactive
    cleanupView(tabName) {
        const controller = this.viewControllers.get(tabName);
        if (controller && typeof controller.cleanup === 'function') {
            controller.cleanup();
        }
    }
    
    // Hide all views initially
    hideAllViews() {
        const allViews = document.querySelectorAll('.view-container');
        allViews.forEach(view => {
            view.style.display = 'none';
            view.classList.remove('active');
        });
        console.log('🧭 All views hidden for proper initialization');
    }
    
    // Handle tab-specific initialization logic
    async handleTabSpecificLogic(tabName) {
        switch (tabName) {
            case 'avatar':
                // Ensure canvas is properly sized and avatar is rendered
                setTimeout(() => {
                    const controller = this.viewControllers.get('avatar');
                    if (controller && typeof controller.resizeCanvas === 'function') {
                        controller.resizeCanvas();
                        controller.generateAvatar();
                    }
                }, 100);
                break;
                
            case 'status':
                // Refresh status data
                const statusController = this.viewControllers.get('status');
                if (statusController && typeof statusController.refreshMetrics === 'function') {
                    statusController.refreshMetrics();
                }
                break;
        }
    }
    
    // Get current tab
    getCurrentTab() {
        return this.stateManager.get('currentTab');
    }
    
    // Check if transitioning
    isNavigationTransitioning() {
        return this.isTransitioning;
    }
    
    // Placeholder HTML methods - these will be filled with actual content
    getChatViewHTML() {
        return `
            <div class="chat-header">
                <div class="chat-header-left">
                    <div class="mini-avatar" id="miniAvatar">
                        <span id="miniAvatarEmoji">😊</span>
                    </div>
                    <div>
                        <div class="chat-title">M1K3</div>
                        <div class="chat-subtitle" id="connectionStatus">
                            <span class="status-dot" id="statusDot"></span>
                            <span id="connectionText">Disconnected</span>
                        </div>
                    </div>
                </div>
                <div class="chat-header-right">
                    <button class="header-button" id="clearChatBtn">Clear</button>
                    <button class="header-button" id="voiceToggleBtn">🔊 Voice</button>
                </div>
            </div>
            
            <div class="chat-panel">
                <div class="chat-messages" id="chatMessages">
                    <div class="message ai">
                        <div class="message-content">
                            <div class="message-text">Hello! I'm M1K3, your AI companion. How can I assist you today?</div>
                            <div class="message-time">Just now</div>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="chat-input-panel">
                <div class="input-group">
                    <textarea 
                        class="chat-input" 
                        id="chatInput" 
                        placeholder="Type your message here... (Enter to send, Shift+Enter for new line)"
                        rows="1"></textarea>
                    <div class="input-actions">
                        <button class="action-button voice-button" id="voiceButton" title="Hold to record">🎤</button>
                        <button class="action-button primary" id="sendButton" title="Send message">➤</button>
                    </div>
                </div>
            </div>
        `;
    }
    
    getAvatarViewHTML() {
        return `
            <div class="avatar-main">
                <div class="avatar-container idle" id="avatarDisplay">
                    <div class="canvas-container">
                        <canvas class="avatar-canvas" id="avatarCanvas"></canvas>
                        <canvas class="avatar-canvas" id="particleCanvas"></canvas>
                        <div class="state-overlay" id="stateOverlay" style="display:none;">
                            <div class="state-icon" id="stateIcon">💭</div>
                            <div class="progress-bar" id="progressBar" style="display:none;">
                                <div class="progress-fill" id="progressFill"></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="avatar-controls">
                <div class="control-section">
                    <h3 class="control-title">Emotions</h3>
                    <div class="emotion-grid">
                        <button class="emotion-button active" data-emotion="happy">😊</button>
                        <button class="emotion-button" data-emotion="sad">😢</button>
                        <button class="emotion-button" data-emotion="angry">😠</button>
                        <button class="emotion-button" data-emotion="surprised">😲</button>
                        <button class="emotion-button" data-emotion="love">😍</button>
                        <button class="emotion-button" data-emotion="thinking">🤔</button>
                        <button class="emotion-button" data-emotion="sleepy">😴</button>
                        <button class="emotion-button" data-emotion="excited">🤩</button>
                    </div>
                </div>
                
                <div class="control-section">
                    <h3 class="control-title">Style</h3>
                    <select id="styleSelect" class="setting-control">
                        <option value="robot">Robot</option>
                        <option value="organic">Organic</option>
                        <option value="crystal">Crystal</option>
                        <option value="ghost">Ghost</option>
                        <option value="energy">Energy</option>
                        <option value="cute">Cute</option>
                    </select>
                </div>
                
                <div class="control-section">
                    <h3 class="control-title">Intensity</h3>
                    <input type="range" id="intensitySlider" min="0" max="100" value="70" class="setting-control">
                    <div class="metric-value" id="intensityValue">70%</div>
                </div>
            </div>
        `;
    }
    
    getStatusViewHTML() {
        return `
            <div class="status-card">
                <div class="status-card-header">
                    <h3 class="status-card-title">Connection</h3>
                    <div class="status-indicator online" id="statusIndicator">
                        <span class="status-dot"></span>
                        <span>Online</span>
                    </div>
                </div>
                <div class="status-metrics">
                    <div class="metric-row">
                        <span class="metric-label">WebSocket</span>
                        <span class="metric-value" id="wsStatus">Disconnected</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Messages Sent</span>
                        <span class="metric-value" id="msgSent">0</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Messages Received</span>
                        <span class="metric-value" id="msgReceived">0</span>
                    </div>
                </div>
            </div>
            
            <div class="status-card">
                <div class="status-card-header">
                    <h3 class="status-card-title">System Metrics</h3>
                </div>
                <div class="status-metrics">
                    <div class="metric-row">
                        <span class="metric-label">CPU Usage</span>
                        <span class="metric-value" id="cpuUsage">0%</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Memory</span>
                        <span class="metric-value" id="memUsage">0%</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Temperature</span>
                        <span class="metric-value" id="temperature">25°C</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Battery</span>
                        <span class="metric-value" id="battery">100%</span>
                    </div>
                </div>
            </div>
            
            <div class="status-card">
                <div class="status-card-header">
                    <h3 class="status-card-title">AI Generation</h3>
                </div>
                <div class="status-metrics">
                    <div class="metric-row">
                        <span class="metric-label">Current State</span>
                        <span class="metric-value" id="aiState">Idle</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Generation Speed</span>
                        <span class="metric-value" id="genSpeed">0 tok/s</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Token Count</span>
                        <span class="metric-value" id="tokenCount">0</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Confidence</span>
                        <span class="metric-value" id="confidence">0%</span>
                    </div>
                </div>
            </div>
            
            <div class="status-card">
                <div class="status-card-header">
                    <h3 class="status-card-title">Care Stats</h3>
                </div>
                <div class="status-metrics">
                    <div class="metric-row">
                        <span class="metric-label">Health</span>
                        <span class="metric-value" id="healthStat">100%</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Energy</span>
                        <span class="metric-value" id="energyStat">100%</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Mood</span>
                        <span class="metric-value" id="moodStat">Happy</span>
                    </div>
                    <div class="metric-row">
                        <span class="metric-label">Evolution</span>
                        <span class="metric-value" id="evolutionStat">Level 1</span>
                    </div>
                </div>
            </div>
        `;
    }
    
    getSettingsViewHTML() {
        return `
            <div class="settings-section">
                <h2 class="settings-title">Voice Settings</h2>
                <div class="settings-group">
                    <div class="setting-item">
                        <span class="setting-label">Enable Voice Synthesis</span>
                        <div class="setting-control">
                            <div class="toggle-switch active" id="voiceToggle"></div>
                        </div>
                    </div>
                    <div class="setting-item">
                        <span class="setting-label">Voice Profile</span>
                        <div class="setting-control">
                            <select id="voiceProfile">
                                <option value="natural">Natural</option>
                                <option value="broadcast">Broadcast</option>
                                <option value="terminal">Terminal</option>
                                <option value="robotic">Robotic</option>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="settings-section">
                <h2 class="settings-title">Interface</h2>
                <div class="settings-group">
                    <div class="setting-item">
                        <span class="setting-label">Enable Animations</span>
                        <div class="setting-control">
                            <div class="toggle-switch active" id="animToggle"></div>
                        </div>
                    </div>
                    <div class="setting-item">
                        <span class="setting-label">Show Debug Console</span>
                        <div class="setting-control">
                            <div class="toggle-switch" id="debugToggle"></div>
                        </div>
                    </div>
                    <div class="setting-item">
                        <span class="setting-label">Theme</span>
                        <div class="setting-control">
                            <select id="themeSelect">
                                <option value="pure-black">Pure Black</option>
                                <option value="dark">Dark</option>
                                <option value="midnight">Midnight</option>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="settings-section">
                <h2 class="settings-title">Performance</h2>
                <div class="settings-group">
                    <div class="setting-item">
                        <span class="setting-label">Enable Particle Effects</span>
                        <div class="setting-control">
                            <div class="toggle-switch active" id="particleToggle"></div>
                        </div>
                    </div>
                    <div class="setting-item">
                        <span class="setting-label">Avatar Frame Rate</span>
                        <div class="setting-control">
                            <select id="fpsSelect">
                                <option value="30">30 FPS</option>
                                <option value="60">60 FPS</option>
                                <option value="auto">Auto</option>
                            </select>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.NavigationManager = NavigationManager;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = NavigationManager;
}