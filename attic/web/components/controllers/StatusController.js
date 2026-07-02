/**
 * M1K3 Status Controller - Manages system status and metrics display
 */
class StatusController {
    constructor(stateManager, navigationManager) {
        this.stateManager = stateManager;
        this.navigationManager = navigationManager;
        
        // Status elements
        this.statusElements = new Map();
        this.refreshInterval = null;
        this.refreshRate = 5000; // 5 seconds
        
        console.log('📊 StatusController initialized');
    }
    
    // Initialize status controller
    async initialize() {
        this.bindElements();
        this.setupEventListeners();
        this.startAutoRefresh();
        this.restoreFromState();
        
        console.log('📊 Status interface ready');
    }
    
    // Bind DOM elements
    bindElements() {
        // Connection status elements
        this.statusElements.set('wsStatus', document.getElementById('wsStatus'));
        this.statusElements.set('msgSent', document.getElementById('msgSent'));
        this.statusElements.set('msgReceived', document.getElementById('msgReceived'));
        this.statusElements.set('statusIndicator', document.getElementById('statusIndicator'));
        
        // System metrics elements
        this.statusElements.set('cpuUsage', document.getElementById('cpuUsage'));
        this.statusElements.set('memUsage', document.getElementById('memUsage'));
        this.statusElements.set('temperature', document.getElementById('temperature'));
        this.statusElements.set('battery', document.getElementById('battery'));
        
        // AI generation elements
        this.statusElements.set('aiState', document.getElementById('aiState'));
        this.statusElements.set('genSpeed', document.getElementById('genSpeed'));
        this.statusElements.set('tokenCount', document.getElementById('tokenCount'));
        this.statusElements.set('confidence', document.getElementById('confidence'));
        
        // Care stats elements
        this.statusElements.set('healthStat', document.getElementById('healthStat'));
        this.statusElements.set('energyStat', document.getElementById('energyStat'));
        this.statusElements.set('moodStat', document.getElementById('moodStat'));
        this.statusElements.set('evolutionStat', document.getElementById('evolutionStat'));
    }
    
    // Setup event listeners
    setupEventListeners() {
        // State listeners for real-time updates
        this.stateManager.subscribe('isConnected', (connected) => {
            this.updateConnectionStatus(connected);
        });
        
        this.stateManager.subscribe('systemMetrics', (metrics) => {
            this.updateSystemMetrics(metrics);
        });
        
        this.stateManager.subscribe('currentEmotion', (emotion) => {
            this.updateMoodDisplay(emotion);
        });
        
        this.stateManager.subscribe('currentState', (state) => {
            this.updateAIState(state);
        });
        
        this.stateManager.subscribe('emotionIntensity', (intensity) => {
            this.updateHealthDisplay(intensity);
        });
        
        // Listen for WebSocket message counts
        this.stateManager.eventBus.addEventListener('websocket.message_sent', () => {
            this.incrementMessageCount('sent');
        });
        
        this.stateManager.eventBus.addEventListener('websocket.message_received', () => {
            this.incrementMessageCount('received');
        });
        
        // Listen for AI generation events
        this.stateManager.eventBus.addEventListener('ai.generation', (event) => {
            this.updateGenerationStats(event.detail);
        });
        
        // Listen for care updates
        this.stateManager.eventBus.addEventListener('care.update', (event) => {
            this.updateCareStats(event.detail);
        });
    }
    
    // Start automatic refresh of metrics
    startAutoRefresh() {
        this.refreshInterval = setInterval(() => {
            this.refreshMetrics();
        }, this.refreshRate);
    }
    
    // Stop automatic refresh
    stopAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }
    
    // Restore interface from state
    restoreFromState() {
        const connected = this.stateManager.get('isConnected');
        this.updateConnectionStatus(connected);
        
        const metrics = this.stateManager.get('systemMetrics');
        if (metrics) {
            this.updateSystemMetrics(metrics);
        }
        
        const emotion = this.stateManager.get('currentEmotion');
        this.updateMoodDisplay(emotion);
        
        const state = this.stateManager.get('currentState');
        this.updateAIState(state);
        
        const intensity = this.stateManager.get('emotionIntensity');
        this.updateHealthDisplay(intensity);
    }
    
    // Refresh all metrics
    async refreshMetrics() {
        try {
            // Update system metrics (simulated for now)
            await this.updateSystemMetricsFromSystem();
            
            // Update care stats from pixel engine if available
            this.updateCareStatsFromEngine();
            
            // Update connection statistics
            this.updateConnectionStatistics();
            
        } catch (error) {
            console.error('📊 Error refreshing metrics:', error);
        }
    }
    
    // Update system metrics from actual system (simulated)
    async updateSystemMetricsFromSystem() {
        // In a real implementation, this would query actual system metrics
        const simulatedMetrics = {
            cpu: Math.floor(Math.random() * 30) + 10, // 10-40%
            memory: Math.floor(Math.random() * 40) + 30, // 30-70%
            temperature: Math.floor(Math.random() * 20) + 25, // 25-45°C
            battery: Math.max(90, Math.floor(Math.random() * 10) + 90), // 90-100%
            network: true,
            networkStrength: Math.floor(Math.random() * 20) + 80 // 80-100
        };
        
        this.stateManager.set('systemMetrics', {
            ...this.stateManager.get('systemMetrics'),
            ...simulatedMetrics
        });
        
        this.updateSystemMetrics(simulatedMetrics);
    }
    
    // Update care stats from pixel engine
    updateCareStatsFromEngine() {
        const pixelEngine = this.stateManager.get('avatar.pixelEngine');
        if (pixelEngine && pixelEngine.avatarState) {
            const health = Math.round(pixelEngine.avatarState.health || 100);
            const energy = Math.round(pixelEngine.avatarState.energy || 100);
            
            this.updateElement('healthStat', health + '%');
            this.updateElement('energyStat', energy + '%');
        }
    }
    
    // Update connection statistics
    updateConnectionStatistics() {
        // These would be tracked by WebSocket manager
        const sentCount = this.stateManager.get('websocket.messagesSent') || 0;
        const receivedCount = this.stateManager.get('websocket.messagesReceived') || 0;
        
        this.updateElement('msgSent', sentCount);
        this.updateElement('msgReceived', receivedCount);
    }
    
    // Update connection status display
    updateConnectionStatus(connected) {
        this.updateElement('wsStatus', connected ? 'Connected' : 'Disconnected');
        
        const indicator = this.statusElements.get('statusIndicator');
        if (indicator) {
            indicator.className = connected ? 'status-indicator online' : 'status-indicator offline';
            indicator.querySelector('span:last-child').textContent = connected ? 'Online' : 'Offline';
        }
    }
    
    // Update system metrics display
    updateSystemMetrics(metrics) {
        if (metrics.cpu !== undefined) {
            this.updateElement('cpuUsage', metrics.cpu + '%');
        }
        if (metrics.memory !== undefined) {
            this.updateElement('memUsage', metrics.memory + '%');
        }
        if (metrics.temperature !== undefined) {
            this.updateElement('temperature', metrics.temperature + '°C');
        }
        if (metrics.battery !== undefined) {
            this.updateElement('battery', metrics.battery + '%');
        }
    }
    
    // Update mood display
    updateMoodDisplay(emotion) {
        const moodText = emotion.charAt(0).toUpperCase() + emotion.slice(1);
        this.updateElement('moodStat', moodText);
    }
    
    // Update AI state display
    updateAIState(state) {
        const stateText = state.charAt(0).toUpperCase() + state.slice(1);
        this.updateElement('aiState', stateText);
    }
    
    // Update health display
    updateHealthDisplay(intensity) {
        this.updateElement('healthStat', intensity + '%');
    }
    
    // Update generation statistics
    updateGenerationStats(data) {
        if (data.generationSpeed !== undefined) {
            this.updateElement('genSpeed', data.generationSpeed.toFixed(1) + ' tok/s');
        }
        if (data.tokenCount !== undefined) {
            this.updateElement('tokenCount', data.tokenCount);
        }
        if (data.confidence !== undefined) {
            this.updateElement('confidence', Math.round(data.confidence * 100) + '%');
        }
    }
    
    // Update care statistics
    updateCareStats(data) {
        if (data.hp !== undefined) {
            this.updateElement('healthStat', data.hp + '%');
        }
        if (data.nrg !== undefined) {
            this.updateElement('energyStat', data.nrg + '%');
        }
        if (data.mood !== undefined) {
            this.updateElement('moodStat', data.mood.charAt(0).toUpperCase() + data.mood.slice(1));
        }
        if (data.evo !== undefined) {
            this.updateElement('evolutionStat', 'Level ' + data.evo);
        }
    }
    
    // Increment message count
    incrementMessageCount(type) {
        const currentCount = parseInt(this.statusElements.get(type === 'sent' ? 'msgSent' : 'msgReceived')?.textContent || '0');
        this.updateElement(type === 'sent' ? 'msgSent' : 'msgReceived', currentCount + 1);
        
        // Update state
        this.stateManager.set(`websocket.messages${type.charAt(0).toUpperCase() + type.slice(1)}`, currentCount + 1);
    }
    
    // Update a single element with animation
    updateElement(elementKey, value, animate = true) {
        const element = this.statusElements.get(elementKey);
        if (!element) return;
        
        const oldValue = element.textContent;
        if (oldValue === String(value)) return;
        
        if (animate && oldValue !== null) {
            // Add update animation
            element.style.transform = 'scale(1.05)';
            element.style.color = 'var(--accent-primary)';
            
            setTimeout(() => {
                element.style.transform = 'scale(1)';
                element.style.color = '';
            }, 200);
        }
        
        element.textContent = value;
    }
    
    // Get current statistics summary
    getStatsSummary() {
        return {
            connection: {
                connected: this.stateManager.get('isConnected'),
                messagesSent: parseInt(this.statusElements.get('msgSent')?.textContent || '0'),
                messagesReceived: parseInt(this.statusElements.get('msgReceived')?.textContent || '0')
            },
            system: {
                cpu: this.statusElements.get('cpuUsage')?.textContent || '0%',
                memory: this.statusElements.get('memUsage')?.textContent || '0%',
                temperature: this.statusElements.get('temperature')?.textContent || '25°C',
                battery: this.statusElements.get('battery')?.textContent || '100%'
            },
            ai: {
                state: this.statusElements.get('aiState')?.textContent || 'Idle',
                generationSpeed: this.statusElements.get('genSpeed')?.textContent || '0 tok/s',
                tokenCount: this.statusElements.get('tokenCount')?.textContent || '0',
                confidence: this.statusElements.get('confidence')?.textContent || '0%'
            },
            care: {
                health: this.statusElements.get('healthStat')?.textContent || '100%',
                energy: this.statusElements.get('energyStat')?.textContent || '100%',
                mood: this.statusElements.get('moodStat')?.textContent || 'Happy',
                evolution: this.statusElements.get('evolutionStat')?.textContent || 'Level 1'
            }
        };
    }
    
    // Export status data
    exportStatusData() {
        const summary = this.getStatsSummary();
        const exportData = {
            timestamp: new Date().toISOString(),
            uptime: Date.now() - this.stateManager.get('startTime', Date.now()),
            ...summary
        };
        
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-status-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);
        
        console.log('📊 Status data exported');
    }
    
    // Handle incoming WebSocket messages
    handleWebSocketMessage(data) {
        switch (data.type) {
            case 'system_metrics':
                this.updateSystemMetrics(data);
                break;
                
            case 'care_update':
                this.updateCareStats(data);
                break;
                
            case 'generation_stream':
                this.updateGenerationStats(data);
                break;
                
            case 'classification':
                if (data.confidence) {
                    this.updateElement('confidence', Math.round(data.confidence * 100) + '%');
                }
                break;
        }
    }
    
    // Called when view is shown
    async onShow() {
        // Immediately refresh metrics when status view is shown
        await this.refreshMetrics();
        
        // Increase refresh rate when active
        this.stopAutoRefresh();
        this.refreshRate = 2000; // 2 seconds
        this.startAutoRefresh();
    }
    
    // Called when view is hidden
    async onHide() {
        // Reduce refresh rate when inactive to save resources
        this.stopAutoRefresh();
        this.refreshRate = 10000; // 10 seconds
        this.startAutoRefresh();
    }
    
    // Cleanup resources
    cleanup() {
        this.stopAutoRefresh();
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.StatusController = StatusController;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = StatusController;
}