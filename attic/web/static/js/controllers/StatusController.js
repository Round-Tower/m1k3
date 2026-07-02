/**
 * M1K3 Status Controller
 * Real-time system monitoring and performance visualization
 */
class StatusController extends EventTarget {
    constructor(stateManager, websocketManager) {
        super();
        
        this.stateManager = stateManager;
        this.websocketManager = websocketManager;
        
        this.elements = {};
        this.charts = new Map();
        this.updateInterval = null;
        this.dataHistory = {
            cpu: [],
            memory: [],
            temperature: [],
            network: [],
            generation: [],
            maxPoints: 50
        };
        
        console.log('📊 StatusController initialized');
    }
    
    /**
     * Initialize status controller
     */
    async initialize() {
        this.findElements();
        this.setupEventListeners();
        this.initializeCharts();
        this.startUpdates();
        
        // Subscribe to state changes
        this.subscribeToStateChanges();
        
        console.log('📊 Status controller initialized');
        return this;
    }
    
    /**
     * Find DOM elements
     */
    findElements() {
        this.elements = {
            // Connection status
            connectionStatus: document.getElementById('connectionStatus'),
            wsStatus: document.getElementById('wsStatus'),
            httpStatus: document.getElementById('httpStatus'),
            lastPing: document.getElementById('lastPing'),
            
            // System metrics
            cpuUsage: document.getElementById('cpuUsage'),
            memoryUsage: document.getElementById('memoryUsage'),
            temperature: document.getElementById('temperature'),
            battery: document.getElementById('battery'),
            networkStatus: document.getElementById('networkStatus'),
            
            // AI metrics
            aiEngine: document.getElementById('aiEngine'),
            aiState: document.getElementById('aiState'),
            confidence: document.getElementById('confidence'),
            generationSpeed: document.getElementById('generationSpeed'),
            tokenCount: document.getElementById('tokenCount'),
            
            // Voice metrics
            voiceEngine: document.getElementById('voiceEngine'),
            voiceProfile: document.getElementById('voiceProfile'),
            ttsStatus: document.getElementById('ttsStatus'),
            
            // Performance metrics
            messagesSent: document.getElementById('messagesSent'),
            messagesReceived: document.getElementById('messagesReceived'),
            uptime: document.getElementById('uptime'),
            ecoSavings: document.getElementById('ecoSavings'),
            
            // Charts
            cpuChart: document.getElementById('cpuChart'),
            memoryChart: document.getElementById('memoryChart'),
            tempChart: document.getElementById('tempChart'),
            networkChart: document.getElementById('networkChart'),
            
            // Component status
            components: document.getElementById('componentStatus'),
            
            // Controls
            refreshButton: document.getElementById('refreshStatus'),
            exportButton: document.getElementById('exportStatus'),
            resetButton: document.getElementById('resetMetrics')
        };
    }
    
    /**
     * Setup event listeners
     */
    setupEventListeners() {
        if (this.elements.refreshButton) {
            this.elements.refreshButton.addEventListener('click', () => {
                this.refreshAll();
            });
        }
        
        if (this.elements.exportButton) {
            this.elements.exportButton.addEventListener('click', () => {
                this.exportMetrics();
            });
        }
        
        if (this.elements.resetButton) {
            this.elements.resetButton.addEventListener('click', () => {
                this.resetMetrics();
            });
        }
        
        // WebSocket connection events
        if (this.websocketManager) {
            this.websocketManager.addEventListener('connected', () => {
                this.updateConnectionStatus('connected');
            });
            
            this.websocketManager.addEventListener('disconnected', () => {
                this.updateConnectionStatus('disconnected');
            });
            
            this.websocketManager.addEventListener('connecting', () => {
                this.updateConnectionStatus('connecting');
            });
            
            this.websocketManager.addEventListener('error', () => {
                this.updateConnectionStatus('error');
            });
        }
    }
    
    /**
     * Subscribe to state changes
     */
    subscribeToStateChanges() {
        // System metrics
        this.stateManager.subscribe('system', (event) => {
            this.updateSystemMetrics(event.detail.value);
        });
        
        // AI state
        this.stateManager.subscribe('ai', (event) => {
            this.updateAIMetrics(event.detail.value);
        });
        
        // Voice state
        this.stateManager.subscribe('voice', (event) => {
            this.updateVoiceMetrics(event.detail.value);
        });
        
        // Performance metrics
        this.stateManager.subscribe('performance', (event) => {
            this.updatePerformanceMetrics(event.detail.value);
        });
        
        // Component status
        this.stateManager.subscribe('components', (event) => {
            this.updateComponentStatus();
        });
        
        // Connection state
        this.stateManager.subscribe('connection', (event) => {
            this.updateConnectionDisplay();
        });
    }
    
    /**
     * Initialize charts
     */
    initializeCharts() {
        // Simple canvas-based charts for performance
        this.initializeChart('cpu', this.elements.cpuChart, '#E25303', '%');
        this.initializeChart('memory', this.elements.memoryChart, '#00FF85', '%');
        this.initializeChart('temperature', this.elements.tempChart, '#FF4500', '°C');
        this.initializeChart('network', this.elements.networkChart, '#00BFFF', 'ms');
    }
    
    /**
     * Initialize individual chart
     */
    initializeChart(name, canvas, color, unit) {
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        this.charts.set(name, {
            canvas,
            ctx,
            color,
            unit,
            data: [],
            max: 100,
            min: 0
        });
        
        this.resizeChart(name);
    }
    
    /**
     * Start regular updates
     */
    startUpdates() {
        this.updateInterval = setInterval(() => {
            this.updateAll();
        }, 2000); // Update every 2 seconds
        
        // Initial update
        this.updateAll();
    }
    
    /**
     * Update all metrics
     */
    updateAll() {
        this.updateDisplays();
        this.updateCharts();
        this.updateComponentStatus();
        this.updateConnectionDisplay();
    }
    
    /**
     * Update connection status
     */
    updateConnectionStatus(status) {
        this.stateManager.updateConnectionState('websocket', status);
    }
    
    /**
     * Update connection display
     */
    updateConnectionDisplay() {
        const connection = this.stateManager.getState('connection');
        
        if (this.elements.wsStatus) {
            this.elements.wsStatus.textContent = connection.websocket || 'unknown';
            this.elements.wsStatus.className = `status-${connection.websocket}`;
        }
        
        if (this.elements.httpStatus) {
            this.elements.httpStatus.textContent = connection.http || 'unknown';
        }
        
        if (this.elements.lastPing && connection.lastPing) {
            const timeSince = Date.now() - connection.lastPing;
            this.elements.lastPing.textContent = `${Math.round(timeSince)}ms ago`;
        }
    }
    
    /**
     * Update system metrics display
     */
    updateSystemMetrics(system) {
        if (this.elements.cpuUsage) {
            this.elements.cpuUsage.textContent = `${system.cpu || 0}%`;
            this.updateProgressBar(this.elements.cpuUsage.parentElement, system.cpu || 0);
        }
        
        if (this.elements.memoryUsage) {
            this.elements.memoryUsage.textContent = `${system.memory || 0}%`;
            this.updateProgressBar(this.elements.memoryUsage.parentElement, system.memory || 0);
        }
        
        if (this.elements.temperature) {
            this.elements.temperature.textContent = `${system.temperature || 25}°C`;
            this.updateTemperatureColor(system.temperature || 25);
        }
        
        if (this.elements.battery) {
            this.elements.battery.textContent = `${system.battery || 100}%`;
            this.updateBatteryColor(system.battery || 100);
        }
        
        if (this.elements.networkStatus) {
            this.elements.networkStatus.textContent = system.network ? 'Connected' : 'Disconnected';
            this.elements.networkStatus.className = system.network ? 'connected' : 'disconnected';
        }
        
        // Update chart data
        this.addDataPoint('cpu', system.cpu || 0);
        this.addDataPoint('memory', system.memory || 0);
        this.addDataPoint('temperature', system.temperature || 25);
    }
    
    /**
     * Update AI metrics display
     */
    updateAIMetrics(ai) {
        if (this.elements.aiEngine) {
            this.elements.aiEngine.textContent = ai.engine || 'unknown';
        }
        
        if (this.elements.aiState) {
            this.elements.aiState.textContent = ai.state || 'idle';
            this.elements.aiState.className = `ai-state-${ai.state || 'idle'}`;
        }
        
        if (this.elements.confidence) {
            this.elements.confidence.textContent = `${Math.round((ai.confidence || 0) * 100)}%`;
        }
        
        if (this.elements.generationSpeed) {
            this.elements.generationSpeed.textContent = `${(ai.generationSpeed || 0).toFixed(1)} tok/s`;
        }
        
        if (this.elements.tokenCount) {
            this.elements.tokenCount.textContent = ai.tokenCount || 0;
        }
        
        // Update generation chart
        if (ai.generationSpeed) {
            this.addDataPoint('generation', ai.generationSpeed);
        }
    }
    
    /**
     * Update voice metrics display
     */
    updateVoiceMetrics(voice) {
        if (this.elements.voiceEngine) {
            this.elements.voiceEngine.textContent = voice.engine || 'auto';
        }
        
        if (this.elements.voiceProfile) {
            this.elements.voiceProfile.textContent = voice.profile || 'natural';
        }
        
        if (this.elements.ttsStatus) {
            const status = voice.synthesizing ? 'Synthesizing' : 'Ready';
            this.elements.ttsStatus.textContent = status;
            this.elements.ttsStatus.className = voice.synthesizing ? 'synthesizing' : 'ready';
        }
    }
    
    /**
     * Update performance metrics display
     */
    updatePerformanceMetrics(performance) {
        if (this.elements.messagesSent) {
            this.elements.messagesSent.textContent = performance.messagesSent || 0;
        }
        
        if (this.elements.messagesReceived) {
            this.elements.messagesReceived.textContent = performance.messagesReceived || 0;
        }
        
        if (this.elements.uptime) {
            const uptimeSeconds = Math.floor((Date.now() - (performance.startTime || Date.now())) / 1000);
            this.elements.uptime.textContent = this.formatUptime(uptimeSeconds);
        }
        
        if (this.elements.ecoSavings) {
            const system = this.stateManager.getState('system');
            this.elements.ecoSavings.textContent = `${(system.ecoSavings || 0).toFixed(1)} Wh`;
        }
    }
    
    /**
     * Update component status display
     */
    updateComponentStatus() {
        if (!this.elements.components) return;
        
        const components = this.stateManager.getState('components') || {};
        const container = this.elements.components;
        
        container.innerHTML = '';
        
        for (const [name, status] of Object.entries(components)) {
            const element = document.createElement('div');
            element.className = `component-status component-${status}`;
            element.innerHTML = `
                <div class="component-name">${this.formatComponentName(name)}</div>
                <div class="component-indicator ${status}"></div>
            `;
            container.appendChild(element);
        }
    }
    
    /**
     * Update all displays
     */
    updateDisplays() {
        const state = this.stateManager.getSnapshot();
        
        this.updateSystemMetrics(state.system || {});
        this.updateAIMetrics(state.ai || {});
        this.updateVoiceMetrics(state.voice || {});
        this.updatePerformanceMetrics(state.performance || {});
    }
    
    /**
     * Add data point to chart
     */
    addDataPoint(chartName, value) {
        const history = this.dataHistory[chartName];
        if (!history) return;
        
        history.push({
            value,
            timestamp: Date.now()
        });
        
        // Keep only recent points
        if (history.length > this.dataHistory.maxPoints) {
            history.shift();
        }
    }
    
    /**
     * Update charts
     */
    updateCharts() {
        for (const [name, chart] of this.charts.entries()) {
            this.drawChart(name, chart);
        }
    }
    
    /**
     * Draw individual chart
     */
    drawChart(name, chart) {
        const { canvas, ctx, color } = chart;
        const data = this.dataHistory[name] || [];
        
        if (data.length === 0) return;
        
        const width = canvas.width;
        const height = canvas.height;
        
        // Clear canvas
        ctx.clearRect(0, 0, width, height);
        
        // Background
        ctx.fillStyle = 'rgba(0, 0, 0, 0.1)';
        ctx.fillRect(0, 0, width, height);
        
        if (data.length < 2) return;
        
        // Calculate bounds
        const values = data.map(d => d.value);
        const max = Math.max(...values, 1);
        const min = Math.min(...values, 0);
        
        // Draw grid
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
        ctx.lineWidth = 1;
        for (let i = 0; i < 5; i++) {
            const y = (height / 4) * i;
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(width, y);
            ctx.stroke();
        }
        
        // Draw line
        ctx.strokeStyle = color;
        ctx.lineWidth = 2;
        ctx.beginPath();
        
        data.forEach((point, index) => {
            const x = (index / (data.length - 1)) * width;
            const y = height - ((point.value - min) / (max - min)) * height;
            
            if (index === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        });
        
        ctx.stroke();
        
        // Fill area under curve
        ctx.fillStyle = color.replace('rgb', 'rgba').replace(')', ', 0.2)');
        ctx.lineTo(width, height);
        ctx.lineTo(0, height);
        ctx.closePath();
        ctx.fill();
        
        // Draw current value
        const currentValue = values[values.length - 1];
        ctx.fillStyle = color;
        ctx.font = '12px monospace';
        ctx.fillText(`${currentValue.toFixed(1)}${chart.unit}`, 5, 15);
    }
    
    /**
     * Resize chart
     */
    resizeChart(name) {
        const chart = this.charts.get(name);
        if (!chart) return;
        
        const { canvas } = chart;
        const rect = canvas.getBoundingClientRect();
        
        canvas.width = rect.width * window.devicePixelRatio;
        canvas.height = rect.height * window.devicePixelRatio;
        chart.ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
    }
    
    /**
     * Update progress bar
     */
    updateProgressBar(container, value) {
        let progressBar = container.querySelector('.progress-bar');
        if (!progressBar) {
            progressBar = document.createElement('div');
            progressBar.className = 'progress-bar';
            container.appendChild(progressBar);
        }
        
        progressBar.style.width = `${Math.min(value, 100)}%`;
        progressBar.className = `progress-bar ${this.getProgressColor(value)}`;
    }
    
    /**
     * Get progress color based on value
     */
    getProgressColor(value) {
        if (value >= 90) return 'critical';
        if (value >= 70) return 'warning';
        return 'normal';
    }
    
    /**
     * Update temperature color
     */
    updateTemperatureColor(temp) {
        if (this.elements.temperature) {
            let className = 'temp-normal';
            if (temp >= 80) className = 'temp-critical';
            else if (temp >= 60) className = 'temp-warning';
            
            this.elements.temperature.className = className;
        }
    }
    
    /**
     * Update battery color
     */
    updateBatteryColor(battery) {
        if (this.elements.battery) {
            let className = 'battery-normal';
            if (battery <= 20) className = 'battery-critical';
            else if (battery <= 50) className = 'battery-warning';
            
            this.elements.battery.className = className;
        }
    }
    
    /**
     * Format component name
     */
    formatComponentName(name) {
        return name
            .replace(/_/g, ' ')
            .replace(/\b\w/g, l => l.toUpperCase());
    }
    
    /**
     * Format uptime
     */
    formatUptime(seconds) {
        const hours = Math.floor(seconds / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const secs = seconds % 60;
        
        if (hours > 0) {
            return `${hours}h ${minutes}m ${secs}s`;
        } else if (minutes > 0) {
            return `${minutes}m ${secs}s`;
        } else {
            return `${secs}s`;
        }
    }
    
    /**
     * Refresh all metrics
     */
    refreshAll() {
        // Request fresh data from server
        if (this.websocketManager && this.websocketManager.isConnected) {
            this.websocketManager.send({
                type: 'request_metrics',
                timestamp: Date.now()
            });
        }
        
        this.updateAll();
        this.emit('refreshed');
    }
    
    /**
     * Export metrics data
     */
    exportMetrics() {
        const data = {
            timestamp: Date.now(),
            state: this.stateManager.getSnapshot(),
            history: this.dataHistory,
            statistics: this.websocketManager ? this.websocketManager.getStatistics() : null
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `m1k3-metrics-${Date.now()}.json`;
        a.click();
        URL.revokeObjectURL(url);
        
        this.emit('exported', { data });
    }
    
    /**
     * Reset metrics
     */
    resetMetrics() {
        if (confirm('Reset all metrics and history?')) {
            // Clear data history
            for (const key of Object.keys(this.dataHistory)) {
                if (Array.isArray(this.dataHistory[key])) {
                    this.dataHistory[key] = [];
                }
            }
            
            // Reset performance metrics
            this.stateManager.updatePerformanceMetrics({
                messagesSent: 0,
                messagesReceived: 0,
                startTime: Date.now()
            });
            
            this.updateCharts();
            this.emit('reset');
        }
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
    destroy() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
        }
        
        this.charts.clear();
        
        console.log('📊 StatusController destroyed');
    }
}

// Export for global use
window.StatusController = StatusController;