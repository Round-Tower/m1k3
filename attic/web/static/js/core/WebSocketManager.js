/**
 * M1K3 Enhanced WebSocket Manager
 * Robust WebSocket handling with automatic reconnection and message queuing
 */
class WebSocketManager extends EventTarget {
    constructor(stateManager, config = {}) {
        super();
        
        this.stateManager = stateManager;
        this.config = {
            url: 'ws://localhost:8081',
            reconnectInterval: 1000,
            maxReconnectInterval: 30000,
            reconnectDecay: 1.5,
            maxReconnectAttempts: 10,
            heartbeatInterval: 30000,
            messageTimeout: 10000,
            
            // Performance and resource limits for edge computing
            maxQueueSize: 1000,
            maxPendingMessages: 100,
            maxMessageSize: 1024 * 1024, // 1MB per message
            maxBandwidth: 10 * 1024 * 1024, // 10MB/s bandwidth limit
            performanceMonitoring: true,
            adaptiveReconnect: true,
            circuitBreakerThreshold: 5,
            
            ...config
        };
        
        this.websocket = null;
        this.reconnectTimer = null;
        this.heartbeatTimer = null;
        this.reconnectAttempts = 0;
        this.messageQueue = [];
        this.pendingMessages = new Map();
        this.messageId = 0;
        
        this.isConnecting = false;
        this.isConnected = false;
        this.shouldReconnect = true;
        
        this.statistics = {
            messagesSent: 0,
            messagesReceived: 0,
            bytesTransferred: 0,
            connectionAttempts: 0,
            lastConnected: null,
            totalDowntime: 0,
            startTime: Date.now(),
            
            // Performance monitoring
            bandwidthUsage: 0,
            averageLatency: 0,
            peakLatency: 0,
            queueDropped: 0,
            circuitBreakerTrips: 0,
            lastPerformanceCheck: Date.now(),
            connectionQuality: 'unknown' // excellent, good, fair, poor
        };
        
        // Circuit breaker state
        this.circuitBreaker = {
            failures: 0,
            state: 'closed', // closed, open, half-open
            lastFailure: null,
            nextRetry: null
        };
        
        // Performance tracking
        this.performanceWindow = [];
        this.bandwidthWindow = [];
        
        // Efficient data structures for edge computing
        this.messageCache = new Map(); // LRU-style message caching
        this.compressionDictionary = new Map(); // Common strings for compression
        this.statsAggregator = {
            buffer: new Float32Array(100), // Circular buffer for metrics
            index: 0,
            size: 0
        };
        
        // Initialize compression dictionary with common WebSocket message patterns
        this.initializeCompressionDictionary();
        
        console.log('🌐 WebSocketManager initialized with edge computing optimizations');
    }
    
    /**
     * Connect to WebSocket server
     */
    connect() {
        if (this.isConnecting || this.isConnected) {
            console.log('🌐 Already connecting/connected');
            return;
        }
        
        this.isConnecting = true;
        this.statistics.connectionAttempts++;
        
        this.stateManager.updateConnectionState('websocket', 'connecting');
        this.emit('connecting', { attempt: this.statistics.connectionAttempts });
        
        console.log(`🌐 Connecting to WebSocket: ${this.config.url} (attempt ${this.statistics.connectionAttempts})`);
        
        try {
            this.websocket = new WebSocket(this.config.url);
            this.setupEventHandlers();
        } catch (error) {
            console.error('🌐 WebSocket connection failed:', error);
            this.handleConnectionError(error);
        }
    }
    
    /**
     * Disconnect from WebSocket server
     */
    disconnect() {
        console.log('🌐 Disconnecting WebSocket');
        
        this.shouldReconnect = false;
        this.clearReconnectTimer();
        this.clearHeartbeatTimer();
        
        if (this.websocket) {
            this.websocket.close(1000, 'User requested disconnect');
            this.websocket = null;
        }
        
        this.isConnecting = false;
        this.isConnected = false;
        this.stateManager.updateConnectionState('websocket', 'disconnected');
    }
    
    /**
     * Send message to WebSocket server
     */
    send(data, options = {}) {
        const message = {
            id: ++this.messageId,
            timestamp: Date.now(),
            data: typeof data === 'string' ? data : JSON.stringify(data),
            ...options
        };
        
        // Check circuit breaker
        if (!this.checkCircuitBreaker()) {
            console.warn('🌐 Circuit breaker is open - dropping message:', message.id);
            this.emit('message.failed', { message, error: 'Circuit breaker open' });
            return false;
        }
        
        // Check message size limits
        if (message.data.length > this.config.maxMessageSize) {
            console.warn('🌐 Message too large:', message.data.length, 'bytes');
            this.emit('message.failed', { message, error: 'Message too large' });
            return false;
        }
        
        // Check bandwidth limits
        if (!this.checkBandwidthLimits(message.data.length)) {
            console.warn('🌐 Bandwidth limit exceeded - dropping message:', message.id);
            this.statistics.queueDropped++;
            this.emit('message.failed', { message, error: 'Bandwidth limit exceeded' });
            return false;
        }
        
        // Check queue size limits
        if (!this.isConnected && this.messageQueue.length >= this.config.maxQueueSize) {
            console.warn('🌐 Message queue full - dropping oldest messages');
            const dropped = this.messageQueue.splice(0, Math.floor(this.config.maxQueueSize * 0.1));
            this.statistics.queueDropped += dropped.length;
            this.emit('messages.dropped', { count: dropped.length });
        }
        
        if (!this.isConnected) {
            if (options.queue !== false) {
                console.log('🌐 Queueing message for later delivery:', message.id);
                this.messageQueue.push(message);
                this.emit('message.queued', { message });
            } else {
                console.warn('🌐 Cannot send message - not connected:', message.id);
                this.emit('message.failed', { message, error: 'Not connected' });
            }
            return false;
        }
        
        try {
            const sendStart = Date.now();
            this.websocket.send(message.data);
            const sendLatency = Date.now() - sendStart;
            
            this.statistics.messagesSent++;
            this.statistics.bytesTransferred += message.data.length;
            
            // Update performance metrics
            this.updatePerformanceMetrics(sendLatency);
            this.recordCircuitBreakerSuccess();
            
            if (options.timeout !== false) {
                this.pendingMessages.set(message.id, {
                    ...message,
                    timeout: setTimeout(() => {
                        this.pendingMessages.delete(message.id);
                        this.emit('message.timeout', { message });
                    }, this.config.messageTimeout)
                });
            }
            
            this.stateManager.updatePerformanceMetrics({
                messagesSent: this.statistics.messagesSent
            });
            
            this.emit('message.sent', { message });
            return true;
            
        } catch (error) {
            console.error('🌐 Failed to send message:', error);
            this.emit('message.failed', { message, error });
            return false;
        }
    }
    
    /**
     * Setup WebSocket event handlers
     */
    setupEventHandlers() {
        this.websocket.onopen = (event) => {
            console.log('🌐 WebSocket connected');
            
            this.isConnecting = false;
            this.isConnected = true;
            this.reconnectAttempts = 0;
            this.statistics.lastConnected = Date.now();
            
            this.stateManager.updateConnectionState('websocket', 'connected');
            this.stateManager.updatePerformanceMetrics({ 
                reconnectAttempts: this.reconnectAttempts 
            });
            
            this.emit('connected', { event });
            this.startHeartbeat();
            this.processMessageQueue();
        };
        
        this.websocket.onmessage = (event) => {
            this.statistics.messagesReceived++;
            this.statistics.bytesTransferred += event.data.length;
            
            this.stateManager.updatePerformanceMetrics({
                messagesReceived: this.statistics.messagesReceived
            });
            
            try {
                const data = JSON.parse(event.data);
                this.handleMessage(data);
                this.emit('message.received', { data, raw: event.data });
            } catch (error) {
                console.warn('🌐 Failed to parse message:', event.data);
                this.emit('message.parse_error', { data: event.data, error });
            }
        };
        
        this.websocket.onclose = (event) => {
            console.log('🌐 WebSocket closed:', event.code, event.reason);
            
            this.isConnecting = false;
            this.isConnected = false;
            this.clearHeartbeatTimer();
            
            this.stateManager.updateConnectionState('websocket', 'disconnected', {
                code: event.code,
                reason: event.reason
            });
            
            this.emit('disconnected', { event });
            
            if (this.shouldReconnect && event.code !== 1000) {
                this.scheduleReconnect();
            }
        };
        
        this.websocket.onerror = (event) => {
            console.error('🌐 WebSocket error:', event);
            this.emit('error', { event });
            this.handleConnectionError(event);
        };
    }
    
    /**
     * Handle incoming WebSocket messages
     */
    handleMessage(data) {
        // Handle acknowledgments
        if (data.ack && this.pendingMessages.has(data.ack)) {
            const pending = this.pendingMessages.get(data.ack);
            clearTimeout(pending.timeout);
            this.pendingMessages.delete(data.ack);
            this.emit('message.acknowledged', { messageId: data.ack });
        }
        
        // Handle heartbeat
        if (data.type === 'pong') {
            this.stateManager.updateState('connection.lastPing', Date.now());
            return;
        }
        
        // Route message to appropriate handler based on type
        switch (data.type) {
            case 'emotion':
            case 'avatar_emotion':
                this.stateManager.updateAvatarEmotion(
                    data.emotion || data.emo,
                    data.intensity || data.int || 50,
                    data
                );
                break;
                
            case 'state':
            case 'avatar_state':
                this.stateManager.updateAvatarState(data.state);
                break;
                
            case 'system_metrics':
            case 'system':
                this.stateManager.updateSystemMetrics({
                    cpu: data.cpu,
                    memory: data.mem || data.memory,
                    temperature: data.temp || data.temperature,
                    battery: data.bat || data.battery,
                    network: data.net !== undefined ? !!data.net : data.network,
                    networkStrength: data.wifi || data.wifi_strength || 80,
                    ecoSavings: data.eco || data.eco_savings || 0
                });
                break;
                
            case 'ai_classification':
            case 'classification':
                this.stateManager.updateAIState({
                    confidence: data.confidence || 0
                });
                break;
                
            case 'thinking_phase':
            case 'ai_thinking':
                this.stateManager.updateAIState({
                    'thinking.phase': data.phase || data.stage,
                    'thinking.progress': data.progress || 0,
                    'thinking.insight': data.insight
                });
                break;
                
            case 'generation_stream':
            case 'ai_generation':
                this.stateManager.updateAIState({
                    generationSpeed: data.generation_speed || data.speed || 0,
                    tokenCount: data.token_count || data.tokens || 0
                });
                break;
                
            case 'voice_status':
            case 'tts_status':
                this.stateManager.updateVoiceState({
                    engine: data.engine,
                    synthesizing: data.synthesizing || false,
                    currentText: data.current_text
                });
                break;
                
            case 'chat_ai':
            case 'ai_response':
                if (data.message) {
                    this.stateManager.addChatMessage(data.message, 'ai');
                }
                if (data.chunk) {
                    this.emit('chat.chunk', { chunk: data.chunk });
                }
                // Emit specific chat events for controllers to listen to
                this.emit('message.chat_ai', { data });
                this.emit('message.ai_response', { data });
                break;
                
            case 'chat_user':
            case 'user_message':
                if (data.message) {
                    this.stateManager.addChatMessage(data.message, 'user');
                }
                this.emit('message.chat_user', { data });
                this.emit('message.user_message', { data });
                break;
                
            default:
                // Generic message handling
                this.emit('message.unknown', { data });
                break;
        }
        
        // Always emit the raw message for custom handling
        this.emit(`message.${data.type}`, { data });
    }
    
    /**
     * Process queued messages
     */
    processMessageQueue() {
        if (this.messageQueue.length === 0) return;
        
        console.log(`🌐 Processing ${this.messageQueue.length} queued messages`);
        
        const messages = [...this.messageQueue];
        this.messageQueue = [];
        
        for (const message of messages) {
            this.send(message.data, { ...message, queue: false });
        }
    }
    
    /**
     * Start heartbeat
     */
    startHeartbeat() {
        this.clearHeartbeatTimer();
        
        this.heartbeatTimer = setInterval(() => {
            if (this.isConnected) {
                this.send({ type: 'ping' }, { timeout: false, queue: false });
            }
        }, this.config.heartbeatInterval);
    }
    
    /**
     * Clear heartbeat timer
     */
    clearHeartbeatTimer() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    scheduleReconnect() {
        if (!this.shouldReconnect || this.reconnectAttempts >= this.config.maxReconnectAttempts) {
            console.log('🌐 Max reconnection attempts reached or reconnection disabled');
            this.stateManager.updateConnectionState('websocket', 'error', {
                reason: 'Max reconnection attempts reached'
            });
            return;
        }
        
        this.clearReconnectTimer();
        
        // Adaptive reconnection based on network conditions and performance
        let delay;
        if (this.config.adaptiveReconnect) {
            delay = this.calculateAdaptiveDelay();
        } else {
            delay = Math.min(
                this.config.reconnectInterval * Math.pow(this.config.reconnectDecay, this.reconnectAttempts),
                this.config.maxReconnectInterval
            );
        }
        
        this.reconnectAttempts++;
        
        console.log(`🌐 Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}, quality: ${this.statistics.connectionQuality})`);
        
        this.reconnectTimer = setTimeout(() => {
            this.connect();
        }, delay);
        
        this.stateManager.updatePerformanceMetrics({
            reconnectAttempts: this.reconnectAttempts
        });
    }
    
    /**
     * Calculate adaptive delay based on connection quality and history
     */
    calculateAdaptiveDelay() {
        const baseDelay = this.config.reconnectInterval * Math.pow(this.config.reconnectDecay, this.reconnectAttempts);
        let adaptiveMultiplier = 1.0;
        
        // Adjust based on connection quality
        switch (this.statistics.connectionQuality) {
            case 'poor':
                adaptiveMultiplier = 2.0; // Wait longer for poor connections
                break;
            case 'fair':
                adaptiveMultiplier = 1.5;
                break;
            case 'good':
                adaptiveMultiplier = 1.0;
                break;
            case 'excellent':
                adaptiveMultiplier = 0.7; // Reconnect faster for excellent connections
                break;
            default:
                adaptiveMultiplier = 1.0;
        }
        
        // Factor in circuit breaker state
        if (this.circuitBreaker.state === 'open') {
            adaptiveMultiplier *= 3.0; // Much longer delay when circuit breaker is open
        } else if (this.circuitBreaker.state === 'half-open') {
            adaptiveMultiplier *= 0.5; // Shorter delay when testing
        }
        
        // Consider recent failure rate
        const recentFailures = this.circuitBreaker.failures;
        if (recentFailures > 2) {
            adaptiveMultiplier *= (1 + recentFailures * 0.2);
        }
        
        // Network-aware adjustment based on browser connectivity
        if (navigator.connection) {
            const effectiveType = navigator.connection.effectiveType;
            switch (effectiveType) {
                case 'slow-2g':
                case '2g':
                    adaptiveMultiplier *= 2.0;
                    break;
                case '3g':
                    adaptiveMultiplier *= 1.3;
                    break;
                case '4g':
                default:
                    // No change for good connections
                    break;
            }
        }
        
        const adaptiveDelay = Math.min(
            baseDelay * adaptiveMultiplier,
            this.config.maxReconnectInterval
        );
        
        console.log(`🌐 Adaptive delay calculation: base=${baseDelay}ms, multiplier=${adaptiveMultiplier.toFixed(2)}, final=${adaptiveDelay}ms`);
        
        return adaptiveDelay;
    }
    
    /**
     * Clear reconnect timer
     */
    clearReconnectTimer() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }
    
    /**
     * Handle connection errors
     */
    handleConnectionError(error) {
        this.isConnecting = false;
        this.isConnected = false;
        
        // Record circuit breaker failure
        this.recordCircuitBreakerFailure();
        
        this.stateManager.updateConnectionState('websocket', 'error', {
            error: error.message || 'Connection failed',
            circuitBreakerState: this.circuitBreaker.state
        });
        
        this.emit('connection.error', { 
            error: error.message || 'Connection failed',
            reconnectAttempts: this.reconnectAttempts,
            circuitBreakerState: this.circuitBreaker.state
        });
        
        if (this.shouldReconnect && this.circuitBreaker.state !== 'open') {
            this.scheduleReconnect();
        } else if (this.circuitBreaker.state === 'open') {
            console.warn('🌐 Circuit breaker is open - stopping reconnection attempts');
        }
    }
    
    /**
     * Get connection statistics
     */
    getStatistics() {
        return {
            ...this.statistics,
            queuedMessages: this.messageQueue.length,
            pendingMessages: this.pendingMessages.size,
            reconnectAttempts: this.reconnectAttempts,
            isConnected: this.isConnected,
            uptime: this.statistics.lastConnected ? 
                Date.now() - this.statistics.lastConnected : 0
        };
    }
    
    /**
     * Emit custom event
     */
    emit(eventName, data) {
        const event = new CustomEvent(eventName, { detail: data });
        this.dispatchEvent(event);
    }
    
    /**
     * Check bandwidth usage and enforce limits
     */
    checkBandwidthLimits(messageSize) {
        const now = Date.now();
        this.bandwidthWindow.push({ timestamp: now, size: messageSize });
        
        // Keep only last 1 second of data
        this.bandwidthWindow = this.bandwidthWindow.filter(
            entry => now - entry.timestamp < 1000
        );
        
        const currentBandwidth = this.bandwidthWindow.reduce(
            (total, entry) => total + entry.size, 0
        );
        
        this.statistics.bandwidthUsage = currentBandwidth;
        
        if (currentBandwidth > this.config.maxBandwidth) {
            console.warn('🌐 Bandwidth limit exceeded:', currentBandwidth);
            return false;
        }
        
        return true;
    }
    
    /**
     * Update performance metrics
     */
    updatePerformanceMetrics(latency) {
        if (!this.config.performanceMonitoring) return;
        
        this.performanceWindow.push({
            timestamp: Date.now(),
            latency: latency
        });
        
        // Keep only last 30 seconds
        const cutoff = Date.now() - 30000;
        this.performanceWindow = this.performanceWindow.filter(
            entry => entry.timestamp > cutoff
        );
        
        if (this.performanceWindow.length > 0) {
            const totalLatency = this.performanceWindow.reduce(
                (sum, entry) => sum + entry.latency, 0
            );
            this.statistics.averageLatency = totalLatency / this.performanceWindow.length;
            this.statistics.peakLatency = Math.max(...this.performanceWindow.map(e => e.latency));
        }
        
        this.updateConnectionQuality();
    }
    
    /**
     * Update connection quality based on performance
     */
    updateConnectionQuality() {
        const { averageLatency, circuitBreakerTrips } = this.statistics;
        
        if (circuitBreakerTrips > 0 || averageLatency > 1000) {
            this.statistics.connectionQuality = 'poor';
        } else if (averageLatency > 500) {
            this.statistics.connectionQuality = 'fair';
        } else if (averageLatency > 200) {
            this.statistics.connectionQuality = 'good';
        } else {
            this.statistics.connectionQuality = 'excellent';
        }
        
        // Emit quality change event
        this.emit('connectionQuality', {
            quality: this.statistics.connectionQuality,
            metrics: this.getPerformanceSnapshot()
        });
    }
    
    /**
     * Circuit breaker logic
     */
    checkCircuitBreaker() {
        if (this.circuitBreaker.state === 'open') {
            if (Date.now() < this.circuitBreaker.nextRetry) {
                return false; // Circuit is open, don't attempt
            }
            // Try half-open
            this.circuitBreaker.state = 'half-open';
            console.log('🌐 Circuit breaker moving to half-open');
        }
        
        return true;
    }
    
    /**
     * Record circuit breaker failure
     */
    recordCircuitBreakerFailure() {
        this.circuitBreaker.failures++;
        this.circuitBreaker.lastFailure = Date.now();
        
        if (this.circuitBreaker.failures >= this.config.circuitBreakerThreshold) {
            this.circuitBreaker.state = 'open';
            this.circuitBreaker.nextRetry = Date.now() + (this.config.reconnectInterval * 3);
            this.statistics.circuitBreakerTrips++;
            
            console.warn('🌐 Circuit breaker opened due to failures:', this.circuitBreaker.failures);
            this.emit('circuitBreakerOpen', { failures: this.circuitBreaker.failures });
        }
    }
    
    /**
     * Record successful operation
     */
    recordCircuitBreakerSuccess() {
        if (this.circuitBreaker.state === 'half-open') {
            this.circuitBreaker.state = 'closed';
            this.circuitBreaker.failures = 0;
            console.log('🌐 Circuit breaker closed');
            this.emit('circuitBreakerClosed');
        }
    }
    
    /**
     * Get performance snapshot for monitoring
     */
    getPerformanceSnapshot() {
        return {
            connectionQuality: this.statistics.connectionQuality,
            averageLatency: this.statistics.averageLatency,
            peakLatency: this.statistics.peakLatency,
            bandwidthUsage: this.statistics.bandwidthUsage,
            queueSize: this.messageQueue.length,
            pendingMessages: this.pendingMessages.size,
            circuitBreakerState: this.circuitBreaker.state,
            circuitBreakerFailures: this.circuitBreaker.failures
        };
    }
    
    /**
     * Enhanced statistics with performance data
     */
    getStatistics() {
        const baseStats = super.getStatistics ? super.getStatistics() : this.statistics;
        return {
            ...baseStats,
            performance: this.getPerformanceSnapshot(),
            uptime: Date.now() - this.statistics.startTime,
            isHealthy: this.circuitBreaker.state !== 'open' && this.statistics.connectionQuality !== 'poor'
        };
    }
    
    /**
     * Initialize compression dictionary with common patterns
     */
    initializeCompressionDictionary() {
        const commonPatterns = [
            'type', 'data', 'timestamp', 'id', 'message', 'status', 'error',
            'connection', 'websocket', 'chat', 'avatar', 'emotion', 'state',
            'voice', 'system', 'performance', 'metrics', 'user', 'ai',
            'connected', 'disconnected', 'connecting', 'error', 'ready',
            'loading', 'idle', 'thinking', 'speaking', 'happy', 'sad'
        ];
        
        commonPatterns.forEach((pattern, index) => {
            this.compressionDictionary.set(pattern, `~${index}~`);
        });
        
        console.log('🌐 Compression dictionary initialized with', commonPatterns.length, 'patterns');
    }
    
    /**
     * Compress message using dictionary substitution
     */
    compressMessage(data) {
        if (typeof data !== 'string') {
            data = JSON.stringify(data);
        }
        
        let compressed = data;
        let compressionRatio = 1.0;
        
        // Apply dictionary compression
        for (const [original, replacement] of this.compressionDictionary) {
            const regex = new RegExp(`"${original}"`, 'g');
            const beforeLength = compressed.length;
            compressed = compressed.replace(regex, replacement);
            
            if (compressed.length < beforeLength) {
                compressionRatio = compressed.length / data.length;
            }
        }
        
        // Only use compression if it's significantly beneficial (>10% savings)
        if (compressionRatio < 0.9) {
            return { data: compressed, compressed: true, originalSize: data.length, compressedSize: compressed.length };
        }
        
        return { data, compressed: false, originalSize: data.length, compressedSize: data.length };
    }
    
    /**
     * Decompress message using dictionary substitution
     */
    decompressMessage(compressedData) {
        if (!compressedData.compressed) {
            return compressedData.data;
        }
        
        let decompressed = compressedData.data;
        
        // Reverse dictionary compression
        for (const [original, replacement] of this.compressionDictionary) {
            const regex = new RegExp(replacement.replace(/~/g, '\\~'), 'g');
            decompressed = decompressed.replace(regex, `"${original}"`);
        }
        
        return decompressed;
    }
    
    /**
     * Add metric to efficient aggregator (circular buffer)
     */
    addMetric(value) {
        this.statsAggregator.buffer[this.statsAggregator.index] = value;
        this.statsAggregator.index = (this.statsAggregator.index + 1) % this.statsAggregator.buffer.length;
        
        if (this.statsAggregator.size < this.statsAggregator.buffer.length) {
            this.statsAggregator.size++;
        }
    }
    
    /**
     * Get aggregated statistics efficiently
     */
    getAggregatedStats() {
        if (this.statsAggregator.size === 0) return { min: 0, max: 0, avg: 0 };
        
        let min = Infinity;
        let max = -Infinity;
        let sum = 0;
        
        for (let i = 0; i < this.statsAggregator.size; i++) {
            const value = this.statsAggregator.buffer[i];
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        
        return {
            min: min === Infinity ? 0 : min,
            max: max === -Infinity ? 0 : max,
            avg: sum / this.statsAggregator.size,
            samples: this.statsAggregator.size
        };
    }
    
    /**
     * Efficient message caching with LRU eviction
     */
    cacheMessage(id, message, maxSize = 100) {
        if (this.messageCache.size >= maxSize) {
            // Remove oldest entry (first key)
            const firstKey = this.messageCache.keys().next().value;
            this.messageCache.delete(firstKey);
        }
        
        this.messageCache.set(id, {
            message,
            timestamp: Date.now(),
            hits: 0
        });
    }
    
    /**
     * Retrieve cached message
     */
    getCachedMessage(id) {
        const cached = this.messageCache.get(id);
        if (cached) {
            cached.hits++;
            // Move to end (LRU update)
            this.messageCache.delete(id);
            this.messageCache.set(id, cached);
            return cached.message;
        }
        return null;
    }
    
    /**
     * Memory-efficient batch processing
     */
    processBatch(items, batchSize = 10) {
        const results = [];
        for (let i = 0; i < items.length; i += batchSize) {
            const batch = items.slice(i, i + batchSize);
            // Process batch and allow GC between batches
            const batchResults = batch.map(item => this.processItem(item));
            results.push(...batchResults);
            
            // Yield control to prevent blocking
            if (i + batchSize < items.length) {
                return new Promise(resolve => {
                    setTimeout(() => resolve(this.processBatch(items.slice(i + batchSize), batchSize)), 0);
                });
            }
        }
        return Promise.resolve(results);
    }
    
    /**
     * Process individual item (placeholder for batch processing)
     */
    processItem(item) {
        // This would contain actual processing logic
        return item;
    }
    
    /**
     * Get memory usage statistics for edge devices
     */
    getMemoryStats() {
        const stats = {
            messageCache: {
                size: this.messageCache.size,
                memoryEstimate: this.messageCache.size * 200 // rough estimate
            },
            messageQueue: {
                size: this.messageQueue.length,
                memoryEstimate: this.messageQueue.length * 500
            },
            performanceBuffer: {
                size: this.statsAggregator.size,
                memoryEstimate: this.statsAggregator.buffer.byteLength
            },
            compressionDictionary: {
                size: this.compressionDictionary.size,
                memoryEstimate: this.compressionDictionary.size * 50
            }
        };
        
        stats.totalEstimatedMemory = Object.values(stats).reduce(
            (total, item) => total + item.memoryEstimate, 0
        );
        
        return stats;
    }
    
    /**
     * Cleanup
     */
    destroy() {
        console.log('🌐 Destroying WebSocketManager');
        
        this.shouldReconnect = false;
        this.disconnect();
        this.clearReconnectTimer();
        this.clearHeartbeatTimer();
        
        // Clear pending messages
        for (const pending of this.pendingMessages.values()) {
            clearTimeout(pending.timeout);
        }
        this.pendingMessages.clear();
        
        this.messageQueue = [];
        this.removeAllEventListeners();
    }
}

// Export for global use
window.WebSocketManager = WebSocketManager;