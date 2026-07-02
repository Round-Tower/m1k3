/**
 * M1K3 Navigation Manager
 * Handles tab navigation and view switching
 */
class NavigationManager extends EventTarget {
    constructor(stateManager) {
        super();
        
        this.stateManager = stateManager;
        this.currentTab = 'dashboard';
        this.history = [];
        this.tabs = new Map();
        
        this.registerDefaultTabs();
        console.log('🧭 NavigationManager initialized');
    }
    
    /**
     * Register default tabs
     */
    registerDefaultTabs() {
        this.registerTab('dashboard', {
            name: 'Dashboard',
            icon: '📊',
            selector: '#dashboardTab',
            contentSelector: '#dashboardContent'
        });
        
        this.registerTab('chat', {
            name: 'Chat',
            icon: '💬', 
            selector: '#chatTab',
            contentSelector: '#chatContent'
        });
        
        this.registerTab('avatar', {
            name: 'Avatar',
            icon: '🤖',
            selector: '#avatarTab',
            contentSelector: '#avatarContent'
        });
        
        this.registerTab('settings', {
            name: 'Settings',
            icon: '⚙️',
            selector: '#settingsTab',
            contentSelector: '#settingsContent'
        });
        
        this.registerTab('debug', {
            name: 'Debug',
            icon: '🐛',
            selector: '#debugTab',
            contentSelector: '#debugContent'
        });
    }
    
    /**
     * Register a new tab
     */
    registerTab(id, config) {
        this.tabs.set(id, {
            id,
            ...config,
            active: false,
            loaded: false,
            element: null,
            contentElement: null
        });
        
        console.log(`🧭 Registered tab: ${id}`);
        return this;
    }
    
    /**
     * Initialize navigation from DOM
     */
    initialize() {
        // Find and setup tab elements
        for (const [tabId, tab] of this.tabs.entries()) {
            const element = document.querySelector(tab.selector);
            const contentElement = document.querySelector(tab.contentSelector);
            
            if (element) {
                tab.element = element;
                element.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.switchTo(tabId);
                });
            }
            
            if (contentElement) {
                tab.contentElement = contentElement;
            }
        }
        
        // Setup keyboard navigation
        this.setupKeyboardNavigation();
        
        // Set initial tab
        this.switchTo(this.currentTab);
        
        console.log('🧭 Navigation initialized');
        return this;
    }
    
    /**
     * Switch to a specific tab
     */
    switchTo(tabId, options = {}) {
        if (!this.tabs.has(tabId)) {
            console.warn(`🧭 Tab not found: ${tabId}`);
            return false;
        }
        
        const previousTab = this.currentTab;
        const tab = this.tabs.get(tabId);
        
        // Check if tab is disabled
        if (tab.disabled && !options.force) {
            console.warn(`🧭 Tab is disabled: ${tabId}`);
            return false;
        }
        
        // Deactivate current tab
        if (previousTab && previousTab !== tabId) {
            this.deactivateTab(previousTab);
        }
        
        // Activate new tab
        this.activateTab(tabId);
        
        // Update state
        this.currentTab = tabId;
        this.stateManager.updateState('ui.currentTab', tabId);
        
        // Add to history (unless navigating via history)
        if (!options.fromHistory) {
            this.history.push({
                tab: previousTab,
                timestamp: Date.now()
            });
            
            // Keep history reasonable
            if (this.history.length > 50) {
                this.history = this.history.slice(-25);
            }
        }
        
        // Emit events
        this.emit('tab.changed', {
            from: previousTab,
            to: tabId,
            tab,
            options
        });
        
        this.emit(`tab.activated.${tabId}`, { tab });
        
        console.log(`🧭 Switched to tab: ${tabId}`);
        return true;
    }
    
    /**
     * Activate a tab
     */
    activateTab(tabId) {
        const tab = this.tabs.get(tabId);
        if (!tab) return;
        
        tab.active = true;
        
        // Update DOM classes
        if (tab.element) {
            tab.element.classList.add('active');
            tab.element.setAttribute('aria-selected', 'true');
        }
        
        if (tab.contentElement) {
            tab.contentElement.classList.add('active');
            tab.contentElement.style.display = 'block';
        }
        
        // Load tab content if not loaded
        if (!tab.loaded) {
            this.loadTabContent(tabId);
        }
        
        // Focus management
        if (tab.contentElement) {
            const focusTarget = tab.contentElement.querySelector('[autofocus]') || 
                              tab.contentElement.querySelector('input, textarea, button');
            if (focusTarget && options.focus !== false) {
                setTimeout(() => focusTarget.focus(), 100);
            }
        }
    }
    
    /**
     * Deactivate a tab
     */
    deactivateTab(tabId) {
        const tab = this.tabs.get(tabId);
        if (!tab) return;
        
        tab.active = false;
        
        // Update DOM classes
        if (tab.element) {
            tab.element.classList.remove('active');
            tab.element.setAttribute('aria-selected', 'false');
        }
        
        if (tab.contentElement) {
            tab.contentElement.classList.remove('active');
            tab.contentElement.style.display = 'none';
        }
        
        this.emit(`tab.deactivated.${tabId}`, { tab });
    }
    
    /**
     * Load tab content dynamically
     */
    async loadTabContent(tabId) {
        const tab = this.tabs.get(tabId);
        if (!tab || tab.loaded) return;
        
        try {
            this.emit(`tab.loading.${tabId}`, { tab });
            
            // Custom loading logic per tab
            switch (tabId) {
                case 'dashboard':
                    await this.loadDashboardContent(tab);
                    break;
                case 'chat':
                    await this.loadChatContent(tab);
                    break;
                case 'avatar':
                    await this.loadAvatarContent(tab);
                    break;
                case 'settings':
                    await this.loadSettingsContent(tab);
                    break;
                case 'debug':
                    await this.loadDebugContent(tab);
                    break;
            }
            
            tab.loaded = true;
            this.emit(`tab.loaded.${tabId}`, { tab });
            
        } catch (error) {
            console.error(`🧭 Failed to load tab content: ${tabId}`, error);
            this.emit(`tab.error.${tabId}`, { tab, error });
        }
    }
    
    /**
     * Setup keyboard navigation
     */
    setupKeyboardNavigation() {
        document.addEventListener('keydown', (e) => {
            // Ctrl/Cmd + Number keys for tab switching
            if ((e.ctrlKey || e.metaKey) && e.key >= '1' && e.key <= '9') {
                e.preventDefault();
                const tabIndex = parseInt(e.key) - 1;
                const tabIds = Array.from(this.tabs.keys());
                if (tabIds[tabIndex]) {
                    this.switchTo(tabIds[tabIndex]);
                }
            }
            
            // Alt + Left/Right for tab navigation
            if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
                e.preventDefault();
                if (e.key === 'ArrowLeft') {
                    this.previousTab();
                } else {
                    this.nextTab();
                }
            }
        });
    }
    
    /**
     * Go to next tab
     */
    nextTab() {
        const tabIds = Array.from(this.tabs.keys());
        const currentIndex = tabIds.indexOf(this.currentTab);
        const nextIndex = (currentIndex + 1) % tabIds.length;
        return this.switchTo(tabIds[nextIndex]);
    }
    
    /**
     * Go to previous tab
     */
    previousTab() {
        const tabIds = Array.from(this.tabs.keys());
        const currentIndex = tabIds.indexOf(this.currentTab);
        const prevIndex = currentIndex === 0 ? tabIds.length - 1 : currentIndex - 1;
        return this.switchTo(tabIds[prevIndex]);
    }
    
    /**
     * Go back in history
     */
    back() {
        if (this.history.length === 0) return false;
        
        const historyEntry = this.history.pop();
        return this.switchTo(historyEntry.tab, { fromHistory: true });
    }
    
    /**
     * Enable/disable tab
     */
    setTabEnabled(tabId, enabled) {
        const tab = this.tabs.get(tabId);
        if (!tab) return;
        
        tab.disabled = !enabled;
        
        if (tab.element) {
            tab.element.classList.toggle('disabled', !enabled);
            tab.element.setAttribute('aria-disabled', !enabled);
        }
        
        // Switch away if current tab is being disabled
        if (!enabled && this.currentTab === tabId) {
            this.nextTab();
        }
        
        return this;
    }
    
    /**
     * Update tab badge/notification
     */
    updateTabBadge(tabId, count = 0, type = 'info') {
        const tab = this.tabs.get(tabId);
        if (!tab || !tab.element) return;
        
        let badge = tab.element.querySelector('.tab-badge');
        
        if (count > 0) {
            if (!badge) {
                badge = document.createElement('span');
                badge.className = `tab-badge badge-${type}`;
                tab.element.appendChild(badge);
            }
            
            badge.textContent = count > 99 ? '99+' : count;
            badge.className = `tab-badge badge-${type}`;
            
        } else if (badge) {
            badge.remove();
        }
        
        return this;
    }
    
    /**
     * Load specific tab content
     */
    async loadDashboardContent(tab) {
        // Dashboard content is usually static
        console.log('🧭 Loading dashboard content');
    }
    
    async loadChatContent(tab) {
        console.log('🧭 Loading chat content');
        // Initialize chat interface
        if (window.m1k3App && window.m1k3App.chatController) {
            await window.m1k3App.chatController.initialize();
        }
    }
    
    async loadAvatarContent(tab) {
        console.log('🧭 Loading avatar content');
        // Initialize avatar controls
        if (window.m1k3App && window.m1k3App.avatarController) {
            await window.m1k3App.avatarController.initialize();
        }
    }
    
    async loadSettingsContent(tab) {
        console.log('🧭 Loading settings content');
        // Initialize settings interface
        if (window.m1k3App && window.m1k3App.settingsController) {
            await window.m1k3App.settingsController.initialize();
        }
    }
    
    async loadDebugContent(tab) {
        console.log('🧭 Loading debug content');
        // Debug content is usually already loaded
    }
    
    /**
     * Get current tab info
     */
    getCurrentTab() {
        return this.tabs.get(this.currentTab);
    }
    
    /**
     * Get all tabs
     */
    getAllTabs() {
        return Array.from(this.tabs.values());
    }
    
    /**
     * Emit custom event
     */
    emit(eventName, data) {
        const event = new CustomEvent(eventName, { detail: data });
        this.dispatchEvent(event);
    }
}

// Export for global use
window.NavigationManager = NavigationManager;