/**
 * M1K3 State Manager
 * Centralized state management for the real-time web interface
 * Optimized for edge computing and resource-constrained environments
 */
class StateManager extends EventTarget {
    constructor(options = {}) {
        super();
        
        // Memory and performance configuration
        this.config = {
            maxHistorySize: options.maxHistorySize || 1000,
            maxListeners: options.maxListeners || 100,
            cleanupInterval: options.cleanupInterval || 30000,
            memoryThreshold: options.memoryThreshold || 50 * 1024 * 1024, // 50MB
            performanceMonitoring: options.performanceMonitoring !== false,
            ...options
        };
        
        // Performance monitoring
        this.metrics = {
            stateUpdates: 0,
            eventsEmitted: 0,
            memoryUsage: 0,
            lastCleanup: Date.now(),
            avgUpdateTime: 0
        };
        
        // Circular buffer for state history (memory efficient)
        this.stateHistory = new Array(this.config.maxHistorySize);
        this.historyIndex = 0;
        this.historySize = 0;
        
        // WeakMap for listener cleanup tracking
        this.listenerRefs = new WeakMap();
        
        this.state = {
            // Connection state
            connection: {
                websocket: 'disconnected', // disconnected, connecting, connected, error
                http: 'unknown',
                lastPing: null,
                reconnectAttempts: 0
            },
            
            // Avatar state
            avatar: {
                emotion: 'happy',
                intensity: 50,
                state: 'idle',
                style: 'robot',
                color: '#E25303'
            },
            
            // System metrics
            system: {
                cpu: 0,
                memory: 0,
                temperature: 25,
                battery: 100,
                network: true,
                networkStrength: 80,
                ecoSavings: 0
            },
            
            // AI state
            ai: {
                engine: 'unknown',
                state: 'idle', // idle, thinking, generating, speaking
                confidence: 0,
                generationSpeed: 0,
                tokenCount: 0,
                thinking: {
                    phase: null,
                    progress: 0,
                    insight: null
                }
            },
            
            // Voice/TTS state
            voice: {
                engine: 'auto', // auto, vibevoice, kitten, fallback
                profile: 'natural',
                enabled: true,
                synthesizing: false,
                queue: [],
                currentText: null
            },
            
            // Chat state
            chat: {
                messages: [],
                isTyping: false,
                lastActivity: null
            },
            
            // UI state
            ui: {
                currentTab: 'dashboard',
                debugVisible: false,
                fullscreen: false,
                theme: 'dark'
            },
            
            // Component loading states
            components: {
                ai_model: 'loading',
                voice_model: 'loading', 
                avatar_server: 'loading',
                websocket: 'loading',
                rag_system: 'loading'
            },
            
            // Performance metrics
            performance: {
                messagesSent: 0,
                messagesReceived: 0,
                startTime: Date.now(),
                lastActivity: Date.now()
            }
        };
        
        // Listener management with automatic cleanup
        this.listeners = new Map();
        this.activeListeners = 0;
        
        // Start cleanup interval
        this.startCleanupTimer();
        
        console.log('🔧 StateManager initialized with performance optimizations');
    }
    
    /**
     * Update state and emit events with performance monitoring
     */
    updateState(path, value, options = {}) {
        const startTime = this.config.performanceMonitoring ? performance.now() : 0;
        
        // Check memory usage before large updates
        if (this.config.performanceMonitoring && this.shouldCheckMemory()) {
            this.checkMemoryUsage();
        }
        
        const oldValue = this.getState(path);
        this.setState(path, value);
        
        // Add to history (circular buffer)
        this.addToHistory(path, value, oldValue);
        
        if (!options.silent && oldValue !== value) {
            this.metrics.stateUpdates++;
            
            this.emit(`state.${path}`, {
                path,
                value,
                oldValue,
                timestamp: Date.now()
            });
            
            // Emit parent path updates (with throttling for performance)
            if (!options.skipParentUpdates) {
                const pathParts = path.split('.');
                for (let i = pathParts.length - 1; i > 0; i--) {
                    const parentPath = pathParts.slice(0, i).join('.');
                    this.emit(`state.${parentPath}`, {
                        path,
                        value,
                        oldValue,
                        timestamp: Date.now()
                    });
                }
            }
        }
        
        // Update performance metrics
        if (this.config.performanceMonitoring && startTime) {
            const updateTime = performance.now() - startTime;
            this.metrics.avgUpdateTime = (this.metrics.avgUpdateTime + updateTime) / 2;
        }
        
        return this;
    }
    
    /**
     * Set state value at path
     */
    setState(path, value) {
        const keys = path.split('.');
        let current = this.state;
        
        for (let i = 0; i < keys.length - 1; i++) {
            if (!current[keys[i]]) {
                current[keys[i]] = {};
            }
            current = current[keys[i]];
        }
        
        current[keys[keys.length - 1]] = value;
        return this;
    }
    
    /**
     * Get state value at path
     */
    getState(path) {
        if (!path) return this.state;
        
        const keys = path.split('.');
        let current = this.state;
        
        for (const key of keys) {
            if (current === null || current === undefined) {
                return undefined;
            }
            current = current[key];
        }
        
        return current;
    }
    
    /**
     * Subscribe to state changes
     */
    subscribe(path, callback, options = {}) {
        const eventName = `state.${path}`;
        this.addEventListener(eventName, callback);
        
        // Call immediately with current value if requested
        if (options.immediate) {
            callback({
                path,
                value: this.getState(path),
                oldValue: undefined,
                timestamp: Date.now()
            });
        }
        
        return () => this.removeEventListener(eventName, callback);
    }
    
    /**
     * Emit custom event
     */
    emit(eventName, data) {
        const event = new CustomEvent(eventName, { detail: data });
        this.dispatchEvent(event);
        return this;
    }
    
    /**
     * Set component loading state
     */
    setComponentLoading(component) {
        return this.updateState(`components.${component}`, 'loading');
    }
    
    /**
     * Set component ready state
     */
    setComponentReady(component) {
        return this.updateState(`components.${component}`, 'ready');
    }
    
    /**
     * Set component error state
     */
    setComponentError(component, error) {
        return this.updateState(`components.${component}`, 'error');
    }
    
    /**
     * Update avatar emotion
     */
    updateAvatarEmotion(emotion, intensity = 50, metadata = null) {
        this.updateState('avatar.emotion', emotion);
        this.updateState('avatar.intensity', intensity);
        
        this.emit('avatar.emotion', {
            emotion,
            intensity,
            metadata,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update avatar state
     */
    updateAvatarState(state) {
        this.updateState('avatar.state', state);
        
        this.emit('avatar.state', {
            state,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update system metrics
     */
    updateSystemMetrics(metrics) {
        for (const [key, value] of Object.entries(metrics)) {
            this.updateState(`system.${key}`, value);
        }
        
        this.emit('system.metrics', {
            metrics,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update AI state
     */
    updateAIState(updates) {
        for (const [key, value] of Object.entries(updates)) {
            this.updateState(`ai.${key}`, value);
        }
        
        this.emit('ai.state', {
            updates,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update voice/TTS state
     */
    updateVoiceState(updates) {
        for (const [key, value] of Object.entries(updates)) {
            this.updateState(`voice.${key}`, value);
        }
        
        this.emit('voice.state', {
            updates,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Add chat message
     */
    addChatMessage(message, sender = 'user') {
        const chatMessage = {
            id: Date.now() + Math.random(),
            message,
            sender,
            timestamp: Date.now(),
            formatted: new Date().toLocaleTimeString()
        };
        
        const messages = this.getState('chat.messages') || [];
        messages.push(chatMessage);
        this.updateState('chat.messages', messages);
        this.updateState('chat.lastActivity', Date.now());
        
        this.emit('chat.message', {
            message: chatMessage,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update connection state
     */
    updateConnectionState(type, state, details = null) {
        this.updateState(`connection.${type}`, state);
        
        this.emit('connection.state', {
            type,
            state,
            details,
            timestamp: Date.now()
        });
        
        return this;
    }
    
    /**
     * Update performance metrics
     */
    updatePerformanceMetrics(updates) {
        for (const [key, value] of Object.entries(updates)) {
            this.updateState(`performance.${key}`, value);
        }
        
        this.updateState('performance.lastActivity', Date.now());
        
        return this;
    }
    
    /**
     * Get full state snapshot
     */
    getSnapshot() {
        return JSON.parse(JSON.stringify(this.state));
    }
    
    /**
     * Reset state to initial values
     */
    reset() {
        const currentState = this.getSnapshot();
        
        // Preserve certain values through reset
        const preserve = {
            startTime: currentState.performance.startTime,
            messages: currentState.chat.messages
        };
        
        // Reset to initial state but restore preserved values
        this.state = new StateManager().state;
        this.updateState('performance.startTime', preserve.startTime);
        this.updateState('chat.messages', preserve.messages);
        
        this.emit('state.reset', { timestamp: Date.now() });
        
        return this;
    }
    
    /**
     * Add to circular history buffer
     */
    addToHistory(path, value, oldValue) {
        const historyEntry = {
            path,
            value,
            oldValue,
            timestamp: Date.now()
        };
        
        this.stateHistory[this.historyIndex] = historyEntry;
        this.historyIndex = (this.historyIndex + 1) % this.config.maxHistorySize;
        
        if (this.historySize < this.config.maxHistorySize) {
            this.historySize++;
        }
    }
    
    /**
     * Start cleanup timer for performance optimization
     */
    startCleanupTimer() {
        setInterval(() => {
            this.performCleanup();
        }, this.config.cleanupInterval);
    }
    
    /**
     * Perform periodic cleanup
     */
    performCleanup() {
        const now = Date.now();
        this.metrics.lastCleanup = now;
        
        // Cleanup inactive listeners
        this.cleanupInactiveListeners();
        
        // Check memory usage
        if (this.config.performanceMonitoring) {
            this.checkMemoryUsage();
        }
        
        // Emit cleanup event
        this.emit('system.cleanup', { timestamp: now });
    }
    
    /**
     * Cleanup inactive listeners
     */
    cleanupInactiveListeners() {
        // This is a placeholder - browsers handle this automatically
        // In a real implementation, you might track listener activity
        if (this.activeListeners > this.config.maxListeners) {
            console.warn('⚠️ High listener count detected:', this.activeListeners);
        }
    }
    
    /**
     * Check memory usage (approximation)
     */
    checkMemoryUsage() {
        if (performance.memory) {
            const memUsage = performance.memory.usedJSHeapSize;
            this.metrics.memoryUsage = memUsage;
            
            if (memUsage > this.config.memoryThreshold) {
                console.warn('⚠️ Memory threshold exceeded:', memUsage);
                this.emit('system.memoryWarning', { 
                    usage: memUsage, 
                    threshold: this.config.memoryThreshold 
                });
                
                // Force cleanup
                this.forceCleanup();
            }
        }
    }
    
    /**
     * Should check memory usage (throttling)
     */
    shouldCheckMemory() {
        return this.metrics.stateUpdates % 100 === 0; // Check every 100 updates
    }
    
    /**
     * Force cleanup when memory is high
     */
    forceCleanup() {
        // Clear old history entries
        const keepRecent = Math.floor(this.config.maxHistorySize * 0.5);
        this.stateHistory.fill(null, keepRecent);
        this.historySize = Math.min(this.historySize, keepRecent);
        
        // Force garbage collection if available
        if (window.gc) {
            window.gc();
        }
        
        console.log('🧹 Forced cleanup completed');
    }
    
    /**
     * Get performance metrics
     */
    getMetrics() {
        return {
            ...this.metrics,
            historySize: this.historySize,
            activeListeners: this.activeListeners,
            memoryUsage: this.config.performanceMonitoring && performance.memory 
                ? performance.memory.usedJSHeapSize 
                : 'unavailable'
        };
    }
    
    /**
     * Enhanced destroy method with proper cleanup
     */
    destroy() {
        // Clear all timers
        if (this.cleanupTimer) {
            clearInterval(this.cleanupTimer);
        }
        
        // Clear circular buffer
        this.stateHistory.fill(null);
        this.historySize = 0;
        
        // Clear listener tracking
        this.listeners.clear();
        this.listenerRefs = new WeakMap();
        
        // Reset metrics
        this.metrics = {
            stateUpdates: 0,
            eventsEmitted: 0,
            memoryUsage: 0,
            lastCleanup: Date.now(),
            avgUpdateTime: 0
        };
        
        console.log('🧹 StateManager destroyed and cleaned up');
        return this;
    }
    
    /**
     * Debug state with performance metrics
     */
    debug() {
        console.group('🔍 StateManager Debug');
        console.log('Current State:', this.getSnapshot());
        console.log('Performance Metrics:', this.getMetrics());
        console.log('Event Listeners:', this.listeners?.size || 0);
        console.log('History Buffer:', `${this.historySize}/${this.config.maxHistorySize}`);
        console.groupEnd();
        
        return this;
    }
}

// Export for global use
window.StateManager = StateManager;