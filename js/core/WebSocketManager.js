/**
 * M1K3 WebSocket Manager - Handles WebSocket communication with optimized protocol
 */
class WebSocketManager {
    constructor(stateManager) {
        this.stateManager = stateManager;
        this.websocket = null;
        this.reconnectTimer = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 3000;
        this.protocol = new M1K3Protocol();
        this.debugConsole = null;
        
        // Message statistics
        this.stats = {
            messagesSent: 0,
            messagesReceived: 0,
            connectionAttempts: 0,
            lastActivity: null
        };
        
        console.log('🌐 WebSocketManager initialized');
    }
    
    // Initialize WebSocket connection
    async initialize() {
        this.setupDebugConsole();
        await this.connect();
        
        console.log('🌐 WebSocket system ready');
    }
    
    // Setup debug console integration
    setupDebugConsole() {
        if (window.DebugConsole) {
            this.debugConsole = new window.DebugConsole();
        }
    }
    
    // Connect to WebSocket server
    async connect() {
        if (this.websocket && this.websocket.readyState !== WebSocket.CLOSED) {
            return;
        }
        
        this.stats.connectionAttempts++;
        
        try {
            this.websocket = new WebSocket('ws://localhost:8081');
            this.stateManager.set('websocket', this.websocket);
            
            this.websocket.onopen = (event) => this.handleOpen(event);
            this.websocket.onmessage = (event) => this.handleMessage(event);
            this.websocket.onclose = (event) => this.handleClose(event);
            this.websocket.onerror = (error) => this.handleError(error);
            
            console.log('🌐 WebSocket connection initiated');
            
        } catch (error) {
            console.error('🌐 Failed to create WebSocket:', error);
            this.debugConsole?.logError(`Connection failed: ${error.message}`);
            this.scheduleReconnect();
        }
    }
    
    // Handle WebSocket open event
    handleOpen(event) {
        console.log('✅ Connected to M1K3 Avatar Server');
        
        this.stateManager.set('isConnected', true);
        this.reconnectAttempts = 0;
        this.stats.lastActivity = Date.now();
        
        this.debugConsole?.logConnection('Connected');
        this.playConnectionSound('connect');
        
        // Send identification message
        this.sendMessage({
            type: 'client_identify',
            client_type: 'web_avatar',
            timestamp: Date.now(),
            user_agent: navigator.userAgent,
            capabilities: {
                chat: true,
                avatar: true,
                status: true,
                settings: true
            }
        });
        
        // Mark WebSocket component as ready
        this.stateManager.setComponentReady('websocket');
        
        // Notify components
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('websocket.connected', {
            detail: { timestamp: Date.now() }
        }));
    }
    
    // Handle WebSocket message
    handleMessage(event) {
        this.stats.messagesReceived++;
        this.stats.lastActivity = Date.now();
        
        try {
            const data = JSON.parse(event.data);
            this.debugConsole?.logReceived(data);
            
            // Update message count in state
            this.stateManager.set('websocket.messagesReceived', this.stats.messagesReceived);
            
            // Decode message using protocol
            const decoded = this.protocol.decodeMessage(data);
            
            // Route message to appropriate handlers
            this.routeMessage(decoded);
            
            // Notify components
            this.stateManager.eventBus.dispatchEvent(new CustomEvent('websocket.message_received', {
                detail: { data: decoded, raw: data }
            }));
            
        } catch (error) {
            console.error('🌐 Error parsing WebSocket message:', error);
            this.debugConsole?.logError(`Parse error: ${error.message}`);
        }
    }
    
    // Handle WebSocket close event
    handleClose(event) {
        console.log(`🔌 Disconnected from M1K3 Avatar Server (code: ${event.code}, reason: ${event.reason})`);
        
        this.stateManager.set('isConnected', false);
        this.stateManager.set('websocket', null);
        
        this.debugConsole?.logConnection(`Disconnected: ${event.code} - ${event.reason}`);
        
        // Only reconnect if not a normal closure
        if (event.code !== 1000 && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.scheduleReconnect();
        }
        
        // Notify components
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('websocket.disconnected', {
            detail: { code: event.code, reason: event.reason }
        }));
    }
    
    // Handle WebSocket error
    handleError(error) {
        console.error('🌐 WebSocket error:', error);
        this.debugConsole?.logError(`WebSocket error: ${error.message || error}`);
        
        // Notify components
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('websocket.error', {
            detail: { error }
        }));
    }
    
    // Route decoded messages to appropriate handlers
    routeMessage(decoded) {
        switch (decoded.type) {
            case 'system':
            case 'metrics':
                this.handleSystemMessage(decoded.data);
                break;
                
            case 'avatar':
            case 'emotion':
            case 'state':
                this.handleAvatarMessage(decoded.type, decoded.data);
                break;
                
            case 'classification':
            case 'thinking_phase':
            case 'generation_stream':
            case 'progress':
                this.handleAIMessage(decoded.type, decoded.data);
                break;
                
            case 'care':
                this.handleCareMessage(decoded.data);
                break;
                
            case 'chat_ai_chunk':
                this.handleChatChunk(decoded.data.chunk);
                break;
                
            case 'chat_user':
                this.handleChatUser(decoded.data.message);
                break;
                
            case 'chat_ai_start':
                this.handleChatStart();
                break;
                
            case 'chat_ai_complete':
                this.handleChatComplete();
                break;
                
            case 'welcome':
                console.log('🌐 Welcome message received');
                break;
                
            case 'pong':
                console.log('🌐 Pong received');
                break;
                
            default:
                console.log('🌐 Unhandled message type:', decoded.type, decoded);
                break;
        }
    }
    
    // Handle system/metrics messages
    handleSystemMessage(data) {
        const systemMetrics = this.stateManager.get('systemMetrics') || {};
        
        // Update system metrics in state
        const updatedMetrics = {
            ...systemMetrics,
            battery: data.bat || data.battery || systemMetrics.battery,
            cpu: data.cpu !== undefined ? data.cpu : systemMetrics.cpu,
            memory: data.mem || data.memory || systemMetrics.memory,
            temperature: data.temp || data.temperature || systemMetrics.temperature,
            network: data.net !== undefined ? !!data.net : systemMetrics.network,
            networkStrength: data.wifi || data.wifi_strength || systemMetrics.networkStrength,
            eco_savings: data.eco || data.eco_savings || systemMetrics.eco_savings
        };
        
        this.stateManager.set('systemMetrics', updatedMetrics);
    }
    
    // Handle avatar-related messages
    handleAvatarMessage(type, data) {
        if (type === 'emotion' || data.emo) {
            const emotion = data.emotion || data.emo;
            const intensity = data.intensity || data.int || this.stateManager.get('emotionIntensity');
            
            this.stateManager.update({
                'currentEmotion': emotion,
                'emotionIntensity': intensity
            });
        }
        
        if (type === 'state' || data.state) {
            const state = data.state || data;
            this.stateManager.set('currentState', state);
        }
    }
    
    // Handle AI processing messages
    handleAIMessage(type, data) {
        // These will be forwarded to status controller
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('ai.generation', {
            detail: { type, ...data }
        }));
    }
    
    // Handle care system messages
    handleCareMessage(data) {
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('care.update', {
            detail: data
        }));
    }
    
    // Handle chat chunk streaming
    handleChatChunk(chunk) {
        // Forward to chat controller
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('chat.chunk', {
            detail: { chunk }
        }));
    }
    
    // Handle chat user message
    handleChatUser(message) {
        // This would typically be handled by chat controller
        console.log('👤 User message:', message);
    }
    
    // Handle chat AI start
    handleChatStart() {
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('chat.ai_start'));
    }
    
    // Handle chat AI complete
    handleChatComplete() {
        this.stateManager.eventBus.dispatchEvent(new CustomEvent('chat.ai_complete'));
        this.playConnectionSound('message_received');
    }
    
    // Send message through WebSocket
    sendMessage(data) {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            try {
                const message = JSON.stringify(data);
                this.websocket.send(message);
                
                this.stats.messagesSent++;
                this.stats.lastActivity = Date.now();
                
                this.debugConsole?.logSent(data);
                
                // Update state
                this.stateManager.set('websocket.messagesSent', this.stats.messagesSent);
                
                // Notify components
                this.stateManager.eventBus.dispatchEvent(new CustomEvent('websocket.message_sent', {
                    detail: { data }
                }));
                
                console.log('📤 Sent to server:', data.type || 'unknown');
                
            } catch (error) {
                console.error('🌐 Error sending message:', error);
                this.debugConsole?.logError(`Send error: ${error.message}`);
            }
        } else {
            console.warn('⚠️ WebSocket not connected, message not sent:', data);
            this.debugConsole?.logError(`Cannot send message - WebSocket not open: ${JSON.stringify(data)}`);
        }
    }
    
    // Schedule reconnection attempt
    scheduleReconnect() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
        }
        
        this.reconnectAttempts++;
        
        if (this.reconnectAttempts <= this.maxReconnectAttempts) {
            const delay = this.reconnectDelay * Math.pow(2, Math.min(this.reconnectAttempts - 1, 5)); // Exponential backoff
            
            console.log(`🔄 Will attempt reconnection in ${delay}ms... (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
            
            this.reconnectTimer = setTimeout(() => {
                this.connect();
            }, delay);
        } else {
            console.error('🔌 Max reconnection attempts reached');
            this.debugConsole?.logError('Max reconnection attempts reached');
        }
    }
    
    // Play connection sound
    playConnectionSound(soundName) {
        const soundsEnabled = this.stateManager.get('ui.soundsEnabled');
        if (soundsEnabled) {
            console.log(`🔊 Playing sound: ${soundName}`);
            // This would integrate with audio system
        }
    }
    
    // Get connection statistics
    getStats() {
        return {
            ...this.stats,
            connected: this.stateManager.get('isConnected'),
            reconnectAttempts: this.reconnectAttempts,
            websocketState: this.websocket ? this.websocket.readyState : -1
        };
    }
    
    // Force reconnection
    reconnect() {
        if (this.websocket) {
            this.websocket.close();
        }
        this.reconnectAttempts = 0;
        this.connect();
    }
    
    // Disconnect WebSocket
    disconnect() {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        
        if (this.websocket) {
            this.websocket.close(1000, 'User disconnected');
            this.websocket = null;
        }
        
        this.stateManager.set('isConnected', false);
        this.stateManager.set('websocket', null);
        
        console.log('🔌 WebSocket disconnected');
    }
    
    // Cleanup resources
    cleanup() {
        this.disconnect();
    }
}

/**
 * M1K3 Protocol Decoder - Optimized message handling
 */
class M1K3Protocol {
    constructor() {
        this.MessageType = {
            SYSTEM_UPDATE: 1,
            AVATAR_UPDATE: 2,
            CARE_UPDATE: 3,
            STATE_CHANGE: 4,
            TEXT_DISPLAY: 5,
            PING: 6,
            ERROR: 7
        };
        
        this.stateMap = {
            'i': 'idle',
            't': 'thinking',
            'g': 'generating',
            's': 'speaking',
            'e': 'error',
            'l': 'listening'
        };
    }
    
    decodeMessage(data) {
        try {
            // Handle binary system updates
            if (data instanceof ArrayBuffer && data.byteLength === 11) {
                return this.decodeSystemBinary(data);
            }
            
            // Handle JSON messages
            const msg = typeof data === 'string' ? JSON.parse(data) : data;
            
            // Route optimized messages
            switch (msg.type) {
                case this.MessageType.SYSTEM_UPDATE:
                    return { type: 'system', data: msg };
                case this.MessageType.AVATAR_UPDATE:
                    return { type: 'avatar', data: msg };
                case this.MessageType.CARE_UPDATE:
                    return { type: 'care', data: msg };
                case this.MessageType.STATE_CHANGE:
                    return { type: 'state', data: this.stateMap[msg.s] || msg.s };
                case this.MessageType.TEXT_DISPLAY:
                    return { type: 'text', data: msg };
                default:
                    return { type: msg.type || 'unknown', data: msg };
            }
        } catch (error) {
            console.error('Protocol decode error:', error);
            return { type: 'error', error: error.message };
        }
    }
    
    decodeSystemBinary(buffer) {
        const view = new DataView(buffer);
        return {
            type: 'system',
            data: {
                bat: view.getUint8(1),
                cpu: view.getUint8(2),
                mem: view.getUint8(3),
                temp: view.getUint8(4),
                net: view.getUint8(5),
                wifi: view.getUint8(6),
                eco: view.getFloat32(7, false)
            }
        };
    }
}

// Export as global
if (typeof window !== 'undefined') {
    window.WebSocketManager = WebSocketManager;
    window.M1K3Protocol = M1K3Protocol;
}

// Module exports
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { WebSocketManager, M1K3Protocol };
}