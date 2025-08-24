/**
 * M1K3 State Manager - Lightweight state management for the UI
 */
class StateManager {
    constructor() {
        this.state = {
            currentTab: 'chat',
            isConnected: false,
            websocket: null,
            currentEmotion: 'happy',
            currentState: 'idle',
            emotionIntensity: 70,
            systemMetrics: {
                battery: 100,
                cpu: 0,
                memory: 0,
                temperature: 25,
                network: true,
                networkStrength: 80,
                eco_savings: 0
            },
            chat: {
                messages: [],
                isTyping: false
            },
            avatar: {
                style: 'robot',
                color: '#E25303',
                pixelEngine: null,
                isAnimating: false
            },
            ui: {
                soundsEnabled: true,
                animationsEnabled: true,
                debugConsoleVisible: false,
                theme: 'pure-black'
            }
        };
        
        this.listeners = new Map();
        this.eventBus = new EventTarget();
        
        console.log('🏪 StateManager initialized');
    }
    
    // Subscribe to state changes
    subscribe(key, callback) {
        if (!this.listeners.has(key)) {
            this.listeners.set(key, new Set());
        }
        this.listeners.get(key).add(callback);
        
        // Return unsubscribe function
        return () => {
            const callbacks = this.listeners.get(key);
            if (callbacks) {
                callbacks.delete(callback);
            }
        };
    }
    
    // Get state value
    get(key) {
        return this.getNestedValue(this.state, key);
    }
    
    // Set state value and notify listeners
    set(key, value, silent = false) {
        const oldValue = this.get(key);
        this.setNestedValue(this.state, key, value);
        
        if (!silent && oldValue !== value) {
            this.notifyListeners(key, value, oldValue);
            this.eventBus.dispatchEvent(new CustomEvent('state.change', {
                detail: { key, value, oldValue }
            }));
        }
    }
    
    // Update multiple state values
    update(updates, silent = false) {
        const changes = [];
        
        Object.entries(updates).forEach(([key, value]) => {
            const oldValue = this.get(key);
            this.setNestedValue(this.state, key, value);
            if (oldValue !== value) {
                changes.push({ key, value, oldValue });
            }
        });
        
        if (!silent && changes.length > 0) {
            changes.forEach(({ key, value, oldValue }) => {
                this.notifyListeners(key, value, oldValue);
            });
            
            this.eventBus.dispatchEvent(new CustomEvent('state.batch_change', {
                detail: { changes }
            }));
        }
    }
    
    // Get nested value using dot notation (e.g., 'chat.messages')
    getNestedValue(obj, key) {
        return key.split('.').reduce((current, part) => current?.[part], obj);
    }
    
    // Set nested value using dot notation
    setNestedValue(obj, key, value) {
        const keys = key.split('.');
        const lastKey = keys.pop();
        const target = keys.reduce((current, part) => {
            if (!(part in current)) {
                current[part] = {};
            }
            return current[part];
        }, obj);
        target[lastKey] = value;
    }
    
    // Notify listeners of state changes
    notifyListeners(key, newValue, oldValue) {
        const callbacks = this.listeners.get(key);
        if (callbacks) {
            callbacks.forEach(callback => {
                try {
                    callback(newValue, oldValue, key);
                } catch (error) {
                    console.error('Error in state listener:', error);
                }
            });
        }
    }
    
    // Reset state to initial values
    reset(keys = null) {
        if (keys) {
            keys.forEach(key => {
                this.set(key, this.getInitialValue(key));
            });
        } else {
            const oldState = { ...this.state };
            this.state = this.getInitialState();
            this.eventBus.dispatchEvent(new CustomEvent('state.reset', {
                detail: { oldState, newState: this.state }
            }));
        }
    }
    
    // Get initial state structure
    getInitialState() {
        return {
            currentTab: 'chat',
            isConnected: false,
            websocket: null,
            currentEmotion: 'happy',
            currentState: 'idle',
            emotionIntensity: 70,
            systemMetrics: {
                battery: 100,
                cpu: 0,
                memory: 0,
                temperature: 25,
                network: true,
                networkStrength: 80,
                eco_savings: 0
            },
            chat: {
                messages: [],
                isTyping: false
            },
            avatar: {
                style: 'robot',
                color: '#E25303',
                pixelEngine: null,
                isAnimating: false
            },
            ui: {
                soundsEnabled: true,
                animationsEnabled: true,
                debugConsoleVisible: false,
                theme: 'pure-black'
            }
        };
    }
    
    // Debugging helper
    dump() {
        console.log('🏪 Current State:', JSON.parse(JSON.stringify(this.state)));
        return this.state;
    }
}

// Export as global for backward compatibility
if (typeof window !== 'undefined') {
    window.StateManager = StateManager;
}

// Also support module exports if needed
if (typeof module !== 'undefined' && module.exports) {
    module.exports = StateManager;
}