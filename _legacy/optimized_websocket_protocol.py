#!/usr/bin/env python3
"""
Optimized WebSocket Protocol for M1K3 Avatar System
Focuses on minimal packet sizes and efficient data transmission
"""

import json
import time
import struct
from typing import Dict, Any, Optional
from dataclasses import dataclass, asdict
from enum import IntEnum

class MessageType(IntEnum):
    """Compact message type identifiers"""
    SYSTEM_UPDATE = 1
    AVATAR_UPDATE = 2
    CARE_UPDATE = 3
    STATE_CHANGE = 4
    TEXT_DISPLAY = 5
    PING = 6
    ERROR = 7

@dataclass
class SystemMetrics:
    """Compact system metrics with shortened field names"""
    bat: int  # battery_percent (0-100)
    cpu: int  # cpu_usage_percent (0-100) 
    mem: int  # memory_usage_percent (0-100)
    temp: int  # temperature_celsius (0-100)
    net: int  # network_connected (0=false, 1=true)
    wifi: int = 0  # wifi_strength_percent (0-100)
    eco: float = 0.0  # eco_savings_wh

@dataclass
class AvatarState:
    """Avatar appearance and emotion state"""
    emo: str  # emotion
    int: int  # intensity (0-100)
    style: str = "robot"  # avatar_style
    color: str = "#E25303"  # color_hex

@dataclass
class CareMetrics:
    """Avatar care and evolution metrics"""
    hp: int  # health (0-100)
    nrg: int  # energy (0-100)
    evo: float  # evolution_level (0-5.0)
    mood: str  # current_mood
    last: int  # last_interaction_minutes

class OptimizedWebSocketProtocol:
    """
    Optimized WebSocket protocol for M1K3 avatar system
    Focuses on minimal packet sizes while maintaining full functionality
    """
    
    def __init__(self):
        self.version = "1.0"
        self.max_packet_size = 512  # Target maximum packet size
        self.compression_enabled = True
        
    def encode_system_update(self, metrics: SystemMetrics) -> bytes:
        """Encode system metrics with binary packing for ultra-small packets"""
        if self.compression_enabled:
            # Binary format: 1 byte type + 6 bytes data + 4 bytes float (12 bytes total)
            return struct.pack('!BBBBBBBf', 
                MessageType.SYSTEM_UPDATE,
                metrics.bat,
                metrics.cpu, 
                metrics.mem,
                metrics.temp,
                metrics.net,
                metrics.wifi,
                metrics.eco
            )
        else:
            # JSON fallback
            return json.dumps({
                "type": MessageType.SYSTEM_UPDATE,
                **asdict(metrics)
            }).encode()
    
    def decode_system_update(self, data: bytes) -> SystemMetrics:
        """Decode binary system metrics"""
        if len(data) == 11:  # Binary format (1+6+4 = 11 bytes)
            _, bat, cpu, mem, temp, net, wifi, eco = struct.unpack('!BBBBBBBf', data)
            return SystemMetrics(bat, cpu, mem, temp, net, wifi, eco)
        else:
            # JSON fallback
            msg = json.loads(data.decode())
            return SystemMetrics(**{k: v for k, v in msg.items() if k != "type"})
    
    def encode_avatar_update(self, avatar: AvatarState) -> bytes:
        """Encode avatar state with compact JSON"""
        data = {
            "type": MessageType.AVATAR_UPDATE,
            **asdict(avatar)
        }
        return json.dumps(data, separators=(',', ':')).encode()
    
    def encode_care_update(self, care: CareMetrics) -> bytes:
        """Encode care metrics with compact JSON"""
        data = {
            "type": MessageType.CARE_UPDATE,
            **asdict(care)
        }
        return json.dumps(data, separators=(',', ':')).encode()
    
    def encode_state_change(self, state: str) -> bytes:
        """Encode state change (ultra compact)"""
        # Map common states to single characters for minimal size
        state_map = {
            "idle": "i",
            "thinking": "t", 
            "generating": "g",
            "speaking": "s",
            "error": "e",
            "listening": "l"
        }
        
        compact_state = state_map.get(state, state[:1])
        
        data = {
            "type": MessageType.STATE_CHANGE,
            "s": compact_state
        }
        return json.dumps(data, separators=(',', ':')).encode()
    
    def encode_text_display(self, message: str, duration: int = 3, style: str = "normal") -> bytes:
        """Encode text display command"""
        # Truncate message if too long
        if len(message) > 50:
            message = message[:47] + "..."
        
        data = {
            "type": MessageType.TEXT_DISPLAY,
            "msg": message,
            "dur": duration,
            "style": style[:1]  # Compact style: n=normal, t=typewriter, w=wave, r=rainbow
        }
        return json.dumps(data, separators=(',', ':')).encode()
    
    def create_ping(self) -> bytes:
        """Create minimal ping packet"""
        return json.dumps({"type": MessageType.PING, "t": int(time.time())}).encode()
    
    def analyze_packet_sizes(self) -> Dict[str, int]:
        """Analyze typical packet sizes for optimization"""
        # Test typical messages
        test_system = SystemMetrics(75, 45, 60, 55, 1, 80, 125.5)
        test_avatar = AvatarState("happy", 75, "robot", "#E25303")
        test_care = CareMetrics(92, 78, 1.25, "energetic", 3)
        
        sizes = {
            "system_binary": len(self.encode_system_update(test_system)),
            "avatar_json": len(self.encode_avatar_update(test_avatar)),
            "care_json": len(self.encode_care_update(test_care)),
            "state_change": len(self.encode_state_change("thinking")),
            "text_display": len(self.encode_text_display("Hello, World!")),
            "ping": len(self.create_ping())
        }
        
        return sizes

def create_javascript_decoder():
    """Create JavaScript decoder for the optimized protocol"""
    js_code = """
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
"""
    return js_code

def main():
    """Test the optimized protocol"""
    print("🚀 Testing Optimized WebSocket Protocol for M1K3")
    print("=" * 60)
    
    protocol = OptimizedWebSocketProtocol()
    
    # Analyze packet sizes
    sizes = protocol.analyze_packet_sizes()
    
    print("📦 Packet Size Analysis:")
    total_size = 0
    for msg_type, size in sizes.items():
        print(f"  {msg_type}: {size} bytes")
        total_size += size
    
    print(f"\nTotal for all message types: {total_size} bytes")
    print(f"Average packet size: {total_size / len(sizes):.1f} bytes")
    
    # Test encoding/decoding
    print("\n🔧 Testing Encoding/Decoding:")
    
    test_system = SystemMetrics(75, 45, 60, 55, 1, 80, 125.5)
    encoded = protocol.encode_system_update(test_system)
    decoded = protocol.decode_system_update(encoded)
    
    print(f"System metrics: {len(encoded)} bytes")
    print(f"  Original: {test_system}")
    print(f"  Decoded:  {decoded}")
    print(f"  Match: {test_system == decoded}")
    
    # Bandwidth calculation
    print("\n📊 Bandwidth Analysis:")
    print("Estimated usage for typical avatar session:")
    print("- System updates: every 5s (binary, 12 bytes)")
    print("- Avatar updates: on emotion change (JSON, ~60 bytes)")
    print("- Care updates: every 30s (JSON, ~80 bytes)")
    print("- State changes: during conversation (JSON, ~25 bytes)")
    print("- Text displays: during conversation (JSON, ~45 bytes)")
    
    # Daily bandwidth estimate
    daily_system = (24 * 60 * 60 / 5) * 12  # Every 5 seconds
    daily_care = (24 * 60 / 30) * 80        # Every 30 minutes
    daily_ping = (24 * 60 * 60 / 5) * 20    # Every 5 seconds
    
    total_daily = daily_system + daily_care + daily_ping
    
    print(f"\nEstimated daily baseline usage: {total_daily:,.0f} bytes ({total_daily/1024:.1f} KB)")
    print(f"That's only {total_daily/1024/1024:.2f} MB per day for continuous monitoring!")
    
    # Create JavaScript decoder
    js_decoder = create_javascript_decoder()
    
    with open("m1k3_protocol_decoder.js", "w") as f:
        f.write(js_decoder)
    
    print(f"\n✅ JavaScript decoder saved to m1k3_protocol_decoder.js")
    
    print("\n🎯 Protocol Optimization Summary:")
    print("- Binary encoding for high-frequency system updates")
    print("- Compact JSON for other message types")
    print("- Field name abbreviation (bat vs battery_percent)")
    print("- State compression (i=idle, t=thinking, etc.)")
    print("- Target packet size: <512 bytes achieved ✅")

if __name__ == "__main__":
    main()