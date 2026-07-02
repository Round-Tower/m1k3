
// Optimized WebSocket Protocol Decoder for M1K3 Avatar
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
    }
    
    decodeMessage(data) {
        try {
            // Try binary decode first (for system updates)
            if (data instanceof ArrayBuffer && data.byteLength === 12) {
                return this.decodeSystemBinary(data);
            }
            
            // Otherwise decode as JSON
            const text = typeof data === 'string' ? data : new TextDecoder().decode(data);
            const msg = JSON.parse(text);
            
            switch (msg.type) {
                case this.MessageType.SYSTEM_UPDATE:
                    return { type: 'system', data: msg };
                case this.MessageType.AVATAR_UPDATE:
                    return { type: 'avatar', data: msg };
                case this.MessageType.CARE_UPDATE:
                    return { type: 'care', data: msg };
                case this.MessageType.STATE_CHANGE:
                    return { type: 'state', data: this.expandState(msg.s) };
                case this.MessageType.TEXT_DISPLAY:
                    return { type: 'text', data: msg };
                case this.MessageType.PING:
                    return { type: 'ping', data: msg };
                default:
                    return { type: 'unknown', data: msg };
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
                eco: view.getFloat32(7, false) // big-endian
            }
        };
    }
    
    expandState(compactState) {
        const stateMap = {
            'i': 'idle',
            't': 'thinking',
            'g': 'generating', 
            's': 'speaking',
            'e': 'error',
            'l': 'listening'
        };
        return stateMap[compactState] || compactState;
    }
    
    // Helper to calculate bandwidth usage
    calculateBandwidth(messagesPerSecond) {
        // Typical message mix per minute:
        // - 12 system updates (every 5s)
        // - 2 avatar updates (on emotion change)
        // - 2 care updates (every 30s)
        // - 10 state changes (during conversation)
        // - 5 text displays (during conversation)
        // - 12 pings (every 5s)
        
        const bytesPerMinute = (
            12 * 12 +    // system updates (binary)
            2 * 60 +     // avatar updates  
            2 * 80 +     // care updates
            10 * 25 +    // state changes
            5 * 45 +     // text displays
            12 * 20      // pings
        );
        
        return {
            bytesPerMinute,
            bytesPerHour: bytesPerMinute * 60,
            bytesPerDay: bytesPerMinute * 60 * 24
        };
    }
}

// Usage example:
const protocol = new M1K3Protocol();

websocket.onmessage = function(event) {
    const decoded = protocol.decodeMessage(event.data);
    
    switch (decoded.type) {
        case 'system':
            updateSystemMetrics(decoded.data);
            break;
        case 'avatar':
            updateAvatarAppearance(decoded.data);
            break;
        case 'care':
            updateCareMetrics(decoded.data);
            break;
        case 'state':
            updateAvatarState(decoded.data);
            break;
        case 'text':
            displayText(decoded.data);
            break;
    }
};
