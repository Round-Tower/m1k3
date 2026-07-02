/**
 * StatusOverlay - Event-driven status indicator system for M1K3
 * Provides a responsive, mobile-first status overlay that updates via WebSocket events
 */

class StatusOverlay {
    constructor(options = {}) {
        this.options = {
            containerId: 'statusOverlay',
            position: 'floating', // 'floating' or 'embedded'
            autoHide: true,
            hideTimeout: 5000,
            ...options
        };
        
        this.indicators = new Map();
        this.element = null;
        this.eventBus = null;
        this.hideTimer = null;
        this.isVisible = false;
        
        // Event bindings
        this.handleEvent = this.handleEvent.bind(this);
        this.show = this.show.bind(this);
        this.hide = this.hide.bind(this);
        this.toggle = this.toggle.bind(this);
        
        this.init();
    }
    
    /**
     * Initialize the status overlay
     */
    init() {
        this.createElement();
        this.setupEventListeners();
        this.registerDefaultIndicators();
        console.log('✅ StatusOverlay initialized');
    }
    
    /**
     * Create the overlay DOM element
     */
    createElement() {
        // Remove existing element if present
        const existing = document.getElementById(this.options.containerId);
        if (existing) {
            existing.remove();
        }
        
        this.element = document.createElement('div');
        this.element.id = this.options.containerId;
        this.element.className = `status-overlay ${this.options.position}`;
        this.element.innerHTML = `
            <div class="status-indicators">
                <div class="indicator-grid" id="indicatorGrid"></div>
            </div>
        `;
        
        document.body.appendChild(this.element);
    }
    
    /**
     * Setup DOM event listeners
     */
    setupEventListeners() {
        // Touch/click to show/hide on mobile
        if (this.element) {
            this.element.addEventListener('click', this.toggle);
            
            // Auto-hide on mobile after timeout
            if (this.options.autoHide && window.innerWidth <= 768) {
                this.element.addEventListener('mouseenter', () => {
                    if (this.hideTimer) {
                        clearTimeout(this.hideTimer);
                        this.hideTimer = null;
                    }
                });
                
                this.element.addEventListener('mouseleave', () => {
                    this.startHideTimer();
                });
            }
        }
        
        // Responsive layout changes
        window.addEventListener('resize', () => {
            this.updateLayout();
        });
    }
    
    /**
     * Subscribe to event bus
     */
    subscribe(eventBus) {
        this.eventBus = eventBus;
        
        // Listen for status update events
        const eventTypes = [
            'avatar.emotion',
            'avatar.state', 
            'system.metrics',
            'ai.classification',
            'ai.thinking',
            'ai.generation',
            'care.update',
            'status.update'
        ];
        
        eventTypes.forEach(type => {
            eventBus.addEventListener(type, this.handleEvent);
        });
        
        console.log('📡 StatusOverlay subscribed to events:', eventTypes);
    }
    
    /**
     * Handle incoming events
     */
    handleEvent(event) {
        const { type, detail } = event;
        console.log('📊 StatusOverlay received event:', type, detail);
        
        switch (type) {
            case 'avatar.emotion':
                this.updateIndicator('emotion', {
                    value: `${this.getEmotionEmoji(detail.emotion)} ${this.capitalize(detail.emotion)}`,
                    intensity: detail.intensity || 50
                });
                break;
                
            case 'avatar.state':
                this.updateIndicator('state', {
                    value: `${this.getStateEmoji(detail.state)} ${this.capitalize(detail.state)}`,
                    animated: detail.state === 'thinking' || detail.state === 'generating'
                });
                break;
                
            case 'system.metrics':
                this.updateIndicator('system', {
                    value: `CPU: ${detail.cpu}% | MEM: ${detail.memory}%`,
                    status: detail.cpu > 80 ? 'warning' : 'normal'
                }, true);
                break;
                
            case 'ai.classification':
                this.updateIndicator('confidence', {
                    value: `${(detail.confidence * 100).toFixed(1)}%`,
                    status: detail.confidence > 0.8 ? 'good' : detail.confidence > 0.5 ? 'normal' : 'warning'
                });
                break;
                
            case 'ai.thinking':
                this.updateIndicator('generation', {
                    value: `${detail.phase}: ${detail.progress}%`,
                    progress: detail.progress,
                    animated: true
                }, true);
                break;
                
            case 'ai.generation':
                this.updateIndicator('generation', {
                    value: `${detail.generationSpeed.toFixed(1)} tok/s`,
                    status: 'active'
                }, true);
                break;
                
            case 'care.update':
                this.updateIndicator('care', {
                    value: `❤️ ${detail.hp}% | ⚡️ ${detail.nrg}%`,
                    status: detail.hp < 30 ? 'critical' : detail.hp < 60 ? 'warning' : 'good'
                }, true);
                break;
                
            case 'status.update':
                this.updateIndicator(detail.id, detail.data, detail.contextual);
                break;
        }
        
        // Show overlay and start hide timer
        this.show();
        this.startHideTimer();
    }
    
    /**
     * Register default status indicators
     */
    registerDefaultIndicators() {
        // Primary indicators (always visible)
        this.registerIndicator('emotion', {
            label: 'Emotion',
            value: '😊 Happy',
            priority: 1,
            contextual: false
        });
        
        this.registerIndicator('state', {
            label: 'State', 
            value: 'Idle',
            priority: 2,
            contextual: false
        });
        
        this.registerIndicator('confidence', {
            label: 'Confidence',
            value: '95.0%',
            priority: 3,
            contextual: false
        });
        
        // Contextual indicators (shown when relevant)
        this.registerIndicator('system', {
            label: 'System',
            value: 'OK',
            priority: 4,
            contextual: true
        });
        
        this.registerIndicator('generation', {
            label: 'Generation',
            value: '128 tok/s',
            priority: 5,
            contextual: true
        });
        
        this.registerIndicator('care', {
            label: 'Care',
            value: '⚡️ Energized',
            priority: 6,
            contextual: true
        });
    }
    
    /**
     * Register a new indicator type
     */
    registerIndicator(id, config) {
        const indicator = {
            id,
            label: config.label || id,
            value: config.value || '',
            status: config.status || 'normal',
            priority: config.priority || 999,
            contextual: config.contextual || false,
            visible: !config.contextual,
            animated: config.animated || false,
            progress: config.progress || null,
            ...config
        };
        
        this.indicators.set(id, indicator);
        this.renderIndicator(indicator);
        console.log('📋 Registered indicator:', id, indicator);
    }
    
    /**
     * Update an existing indicator
     */
    updateIndicator(id, data, contextual = false) {
        const indicator = this.indicators.get(id);
        if (!indicator) {
            console.warn('Unknown indicator:', id);
            return;
        }
        
        // Update indicator data
        Object.assign(indicator, data);
        
        // Show contextual indicators when updated
        if (contextual) {
            indicator.visible = true;
        }
        
        this.renderIndicator(indicator);
    }
    
    /**
     * Render a single indicator
     */
    renderIndicator(indicator) {
        const grid = document.getElementById('indicatorGrid');
        if (!grid) return;
        
        let element = document.getElementById(`indicator-${indicator.id}`);
        
        if (!element) {
            element = document.createElement('div');
            element.id = `indicator-${indicator.id}`;
            element.className = 'status-indicator';
            grid.appendChild(element);
        }
        
        // Update classes
        element.className = `status-indicator ${indicator.status} ${indicator.animated ? 'animated' : ''} ${indicator.visible ? 'visible' : 'hidden'}`;
        
        // Update content
        element.innerHTML = `
            <div class="indicator-value">${indicator.value}</div>
            <div class="indicator-label">${indicator.label}</div>
            ${indicator.progress !== null ? `<div class="indicator-progress" style="width: ${indicator.progress}%"></div>` : ''}
        `;
        
        // Sort indicators by priority
        this.sortIndicators();
    }
    
    /**
     * Sort indicators by priority
     */
    sortIndicators() {
        const grid = document.getElementById('indicatorGrid');
        if (!grid) return;
        
        const sortedIndicators = Array.from(this.indicators.values())
            .filter(ind => ind.visible)
            .sort((a, b) => a.priority - b.priority);
        
        sortedIndicators.forEach(indicator => {
            const element = document.getElementById(`indicator-${indicator.id}`);
            if (element && grid.contains(element)) {
                grid.appendChild(element);
            }
        });
    }
    
    /**
     * Show overlay
     */
    show() {
        if (!this.element || this.isVisible) return;
        
        this.element.classList.add('visible');
        this.isVisible = true;
        
        // Trigger entrance animation
        requestAnimationFrame(() => {
            this.element.classList.add('animate-in');
        });
    }
    
    /**
     * Hide overlay
     */
    hide() {
        if (!this.element || !this.isVisible) return;
        
        this.element.classList.remove('animate-in');
        this.element.classList.add('animate-out');
        
        setTimeout(() => {
            if (this.element) {
                this.element.classList.remove('visible', 'animate-out');
                this.isVisible = false;
            }
        }, 300);
    }
    
    /**
     * Toggle overlay visibility
     */
    toggle() {
        this.isVisible ? this.hide() : this.show();
    }
    
    /**
     * Start auto-hide timer
     */
    startHideTimer() {
        if (this.hideTimer) {
            clearTimeout(this.hideTimer);
        }
        
        if (this.options.autoHide && window.innerWidth <= 768) {
            this.hideTimer = setTimeout(() => {
                this.hide();
            }, this.options.hideTimeout);
        }
    }
    
    /**
     * Update layout based on screen size
     */
    updateLayout() {
        if (!this.element) return;
        
        const isMobile = window.innerWidth <= 768;
        const isTablet = window.innerWidth > 768 && window.innerWidth <= 1200;
        
        this.element.classList.toggle('mobile', isMobile);
        this.element.classList.toggle('tablet', isTablet);
        this.element.classList.toggle('desktop', window.innerWidth > 1200);
        
        // Update position based on screen size
        if (isMobile) {
            this.setPosition('floating');
        } else {
            this.setPosition(this.options.position);
        }
    }
    
    /**
     * Set overlay position
     */
    setPosition(position) {
        if (!this.element) return;
        
        this.element.classList.remove('floating', 'embedded');
        this.element.classList.add(position);
        this.options.position = position;
    }
    
    /**
     * Utility: Get emotion emoji
     */
    getEmotionEmoji(emotion) {
        const emojis = {
            happy: '😊', sad: '😢', angry: '😠', surprised: '😲',
            love: '😍', thinking: '🤔', sleepy: '😴', excited: '🤩'
        };
        return emojis[emotion] || '😐';
    }
    
    /**
     * Utility: Get state emoji
     */
    getStateEmoji(state) {
        const emojis = {
            idle: '💤', thinking: '🤔', speaking: '🗣️', listening: '👂',
            generating: '⚡', error: '❌', loading: '⏳'
        };
        return emojis[state] || '●';
    }
    
    /**
     * Utility: Capitalize string
     */
    capitalize(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    }
    
    /**
     * Destroy overlay and cleanup
     */
    destroy() {
        if (this.hideTimer) {
            clearTimeout(this.hideTimer);
        }
        
        if (this.eventBus) {
            // Remove event listeners
            const eventTypes = [
                'avatar.emotion', 'avatar.state', 'system.metrics',
                'ai.classification', 'ai.thinking', 'ai.generation',
                'care.update', 'status.update'
            ];
            
            eventTypes.forEach(type => {
                this.eventBus.removeEventListener(type, this.handleEvent);
            });
        }
        
        if (this.element) {
            this.element.remove();
        }
        
        console.log('🗑️ StatusOverlay destroyed');
    }
}

// Export for module use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = StatusOverlay;
}

// Global window export for direct HTML usage
if (typeof window !== 'undefined') {
    window.StatusOverlay = StatusOverlay;
}