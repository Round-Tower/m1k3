#!/usr/bin/env python3
"""
M1K3 Avatar Server
Serves the avatar interface and handles WebSocket communication
"""

import os
import json
import threading
import time
import socket
import argparse
import sys
from datetime import datetime
from http.server import HTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlparse
from pathlib import Path
import logging

try:
    from websocket_server import WebsocketServer
    WEBSOCKET_AVAILABLE = True
except ImportError:
    print("⚠️  websocket-server not available. Install with: pip install websocket-server")
    WEBSOCKET_AVAILABLE = False

def get_network_ips():
    """Get all available network IP addresses"""
    ips = []
    
    # Get localhost
    ips.append(("localhost", "127.0.0.1"))
    
    try:
        # Get all network interfaces
        hostname = socket.gethostname()
        
        # Get primary IP (most common method)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            try:
                # Connect to a remote address (doesn't actually send data)
                s.connect(("8.8.8.8", 80))
                primary_ip = s.getsockname()[0]
                ips.append(("primary", primary_ip))
            except Exception:
                pass
        
        # Try to get hostname IP
        try:
            hostname_ip = socket.gethostbyname(hostname)
            if hostname_ip not in [ip[1] for ip in ips]:
                ips.append(("hostname", hostname_ip))
        except Exception:
            pass
            
        # Get all address info for hostname
        try:
            addr_info = socket.getaddrinfo(hostname, None, socket.AF_INET)
            for addr in addr_info:
                ip = addr[4][0]
                if ip not in [ip_pair[1] for ip_pair in ips] and not ip.startswith("127."):
                    ips.append(("network", ip))
        except Exception:
            pass
            
    except Exception as e:
        print(f"Warning: Could not detect network IPs: {e}")
    
    return ips

def format_server_urls(port, ips):
    """Format server URLs for display"""
    urls = []
    for ip_type, ip in ips:
        url = f"http://{ip}:{port}"
        if ip_type == "localhost":
            urls.append(f"   Local:   {url}")
        elif ip_type == "primary":
            urls.append(f"   Network: {url} (primary)")
        else:
            urls.append(f"   Network: {url}")
    return urls

class AvatarHTTPHandler(SimpleHTTPRequestHandler):
    """Custom HTTP handler for serving avatar files"""
    
    def __init__(self, *args, **kwargs):
        # Set the directory to serve from
        super().__init__(*args, directory=os.path.dirname(__file__), **kwargs)
    
    def do_GET(self):
        if self.path == '/':
            self.path = '/m1k3_avatar.html'
        return super().do_GET()
    
    def log_message(self, format, *args):
        # Suppress HTTP logs for cleaner output
        pass

class AvatarServer:
    """M1K3 Avatar Server with WebSocket support"""
    
    def __init__(self, http_port=8080, ws_port=8081, verbose=False):
        self.http_port = http_port
        self.ws_port = ws_port
        self.verbose = verbose
        self.http_server = None
        self.websocket_server = None
        self.http_thread = None
        self.ws_thread = None
        self.running = False
        self.clients = []
        
        # Message statistics
        self.message_stats = {
            'total_sent': 0,
            'total_received': 0,
            'by_type': {},
            'start_time': time.time()
        }
        
        # Setup logging
        log_level = logging.DEBUG if verbose else logging.INFO
        logging.basicConfig(
            level=log_level,
            format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
            datefmt='%H:%M:%S'
        )
        self.logger = logging.getLogger('AvatarServer')
        
        if verbose:
            self.logger.info("🔍 Verbose logging enabled")
    
    def start(self):
        """Start both HTTP and WebSocket servers"""
        if not WEBSOCKET_AVAILABLE:
            self.logger.error("WebSocket server not available")
            return False
        
        if self.running:
            self.logger.warning("Server already running")
            return True
        
        try:
            # Start HTTP server on all interfaces
            self.http_server = HTTPServer(('0.0.0.0', self.http_port), AvatarHTTPHandler)
            self.http_thread = threading.Thread(target=self.http_server.serve_forever, daemon=True)
            self.http_thread.start()
            
            # Start WebSocket server on all interfaces
            self.websocket_server = WebsocketServer(host='0.0.0.0', port=self.ws_port)
            self.websocket_server.set_fn_new_client(self._new_client)
            self.websocket_server.set_fn_client_left(self._client_left)
            self.websocket_server.set_fn_message_received(self._message_received)
            
            self.ws_thread = threading.Thread(target=self.websocket_server.run_forever, daemon=True)
            self.ws_thread.start()
            
            self.running = True
            
            # Get all network IPs and display them
            network_ips = get_network_ips()
            urls = format_server_urls(self.http_port, network_ips)
            
            self.logger.info("🌐 Avatar server started:")
            for url in urls:
                self.logger.info(url)
            self.logger.info(f"   WebSocket: ws://0.0.0.0:{self.ws_port} (all interfaces)")
            
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to start server: {e}")
            self.stop()
            return False
    
    def stop(self):
        """Stop both servers"""
        if not self.running:
            return
        
        self.running = False
        
        if self.http_server:
            self.http_server.shutdown()
            self.http_server = None
        
        if self.websocket_server:
            self.websocket_server.shutdown()
            self.websocket_server = None
        
        self.clients = []
        self.logger.info("🔴 Avatar server stopped")
    
    def is_running(self):
        """Check if server is running"""
        return self.running
    
    def send_emotion_update(self, emotion, intensity=50, message=""):
        """Send emotion update to all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "emotion",
            "emotion": emotion,
            "intensity": intensity,
            "message": message,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        self._log_sent_message(data)
        
        for client in self.clients[:]:  # Copy list to avoid modification during iteration
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_state_update(self, state):
        """Send state update to all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "state",
            "state": state,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        self._log_sent_message(data)
        
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_style_update(self, style, color="#E25303"):
        """Send style update to all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "style",
            "style": style,
            "color": color,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_progress_update(self, stage, progress, tokens=0, message=""):
        """Send progress update to all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "progress",
            "stage": stage,
            "progress": progress,
            "tokens": tokens,
            "message": message,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_metrics_update(self, metrics_data):
        """Send metrics update to all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "metrics",
            "metrics": metrics_data,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        self._log_sent_message(data)
        
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send to client {client['id']}: {e}")
                self._remove_client(client)
    
    def get_status(self):
        """Get server status"""
        status = {
            "running": self.running,
            "http_port": self.http_port,
            "ws_port": self.ws_port,
            "connected_clients": len(self.clients),
            "websocket_available": WEBSOCKET_AVAILABLE
        }
        
        if self.running:
            # Add network information
            network_ips = get_network_ips()
            status["network_ips"] = network_ips
            status["urls"] = [f"http://{ip}:{self.http_port}" for _, ip in network_ips]
            status["http_url"] = f"http://localhost:{self.http_port}"  # Keep for compatibility
        else:
            status["http_url"] = None
            status["network_ips"] = []
            status["urls"] = []
            
        return status
    
    def _new_client(self, client, server):
        """Handle new WebSocket client connection"""
        self.clients.append(client)
        self.logger.info(f"👤 New avatar client connected: {client['id']}")
        
        # Send initial state to new client
        welcome_data = {
            "type": "welcome",
            "message": "Connected to M1K3 Avatar Server",
            "timestamp": time.time()
        }
        server.send_message(client, json.dumps(welcome_data))
    
    def _client_left(self, client, server):
        """Handle client disconnection"""
        self._remove_client(client)
        self.logger.info(f"👤 Avatar client disconnected: {client['id']}")
    
    def _remove_client(self, client):
        """Remove client from list"""
        if client in self.clients:
            self.clients.remove(client)
    
    def _message_received(self, client, server, message):
        """Handle messages from clients"""
        try:
            data = json.loads(message)
            message_type = data.get("type", "unknown")
            
            # Update statistics
            self.message_stats['total_received'] += 1
            self.message_stats['by_type'][message_type] = self.message_stats['by_type'].get(message_type, 0) + 1
            
            if self.verbose:
                timestamp = datetime.now().strftime('%H:%M:%S.%f')[:-3]
                self.logger.info(f"📨 [{timestamp}] RECV {message_type.upper()}: {self._format_message_for_log(data)}")
            else:
                self.logger.debug(f"Received from client {client['id']}: {data}")
            
            if message_type == "ping":
                pong_data = {"type": "pong", "timestamp": time.time()}
                server.send_message(client, json.dumps(pong_data))
                self._log_sent_message(pong_data)
            
            elif message_type == "chat_user":
                # Handle user chat message - forward to CLI if needed
                self.logger.info(f"Chat message from client: {data.get('message', '')}")
                # Broadcast to other clients or handle as needed
                self._handle_chat_message(client, data)
            
            elif message_type == "voice_data":
                # Handle voice data from speech-to-text
                self.logger.info(f"Voice data from client: {data.get('text', '')}")
                # Process voice input
                self._handle_voice_input(client, data)
            
            elif message_type == "sound_trigger":
                # Handle sound effect trigger
                sound_name = data.get('sound')
                if sound_name:
                    self._broadcast_sound(sound_name)
            
            elif message_type == "metrics_request":
                # Handle metrics request
                self._send_metrics_update(client)
                
        except json.JSONDecodeError as e:
            self.logger.warning(f"Invalid JSON from client {client['id']}: {e}")
        except Exception as e:
            self.logger.error(f"Error handling message from client {client['id']}: {e}")
    
    def _format_message_for_log(self, data):
        """Format message data for logging"""
        msg_type = data.get('type', 'unknown')
        
        if msg_type == 'chat_user':
            return f"User message: \"{data.get('message', '')[:50]}...\""
        elif msg_type == 'chat_ai_chunk':
            return f"AI chunk: \"{data.get('chunk', '')[:30]}...\""
        elif msg_type == 'emotion':
            return f"{data.get('emotion', 'unknown')} ({data.get('intensity', 0)}%)"
        elif msg_type == 'state':
            return f"State: {data.get('state', 'unknown')}"
        elif msg_type == 'sound':
            return f"Sound: {data.get('sound', 'unknown')}"
        elif msg_type == 'metrics':
            return f"Metrics update"
        elif msg_type in ['ping', 'pong']:
            return "Keep-alive"
        else:
            return json.dumps(data, separators=(',', ':'))[:100]
    
    def _log_sent_message(self, data):
        """Log sent message with statistics"""
        message_type = data.get('type', 'unknown')
        self.message_stats['total_sent'] += 1
        
        if self.verbose:
            timestamp = datetime.now().strftime('%H:%M:%S.%f')[:-3]
            self.logger.info(f"📤 [{timestamp}] SENT {message_type.upper()}: {self._format_message_for_log(data)}")
    
    def _handle_chat_message(self, sender_client, data):
        """Handle chat message from client"""
        # Store the message (in a real implementation, you might save to a database)
        message_data = {
            "type": "chat_user",
            "message": data.get("message", ""),
            "timestamp": time.time(),
            "client_id": sender_client["id"]
        }
        
        # Broadcast to other clients (excluding sender)
        for client in self.clients[:]:
            if client["id"] != sender_client["id"]:
                try:
                    self.websocket_server.send_message(client, json.dumps(message_data))
                except Exception as e:
                    self.logger.warning(f"Failed to broadcast message to client {client['id']}: {e}")
                    self._remove_client(client)
    
    def _handle_voice_input(self, client, data):
        """Handle voice input from client"""
        voice_text = data.get("text", "")
        if voice_text:
            # Convert voice to chat message
            chat_data = {
                "type": "chat_user", 
                "message": voice_text,
                "source": "voice"
            }
            self._handle_chat_message(client, chat_data)
    
    def _broadcast_sound(self, sound_name):
        """Broadcast sound effect to all clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "sound",
            "sound": sound_name,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send sound to client {client['id']}: {e}")
                self._remove_client(client)
    
    def _send_metrics_update(self, client):
        """Send current metrics to a specific client"""
        # This would typically fetch real metrics from the CLI/system
        metrics_data = {
            "type": "metrics",
            "energy_saved": "12.5",
            "water_saved": "450",
            "co2_saved": "35",
            "message_count": len(self.clients),
            "timestamp": time.time()
        }
        
        try:
            self.websocket_server.send_message(client, json.dumps(metrics_data))
        except Exception as e:
            self.logger.warning(f"Failed to send metrics to client {client['id']}: {e}")
            self._remove_client(client)
    
    def send_chat_ai_start(self):
        """Notify clients that AI is starting to respond"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "chat_ai_start",
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send AI start to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_chat_ai_chunk(self, chunk):
        """Send AI response chunk to all clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "chat_ai_chunk",
            "chunk": chunk,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send AI chunk to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_chat_ai_complete(self):
        """Notify clients that AI response is complete"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "chat_ai_complete",
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send AI complete to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_metrics_broadcast(self, metrics):
        """Broadcast metrics update to all clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "metrics",
            "timestamp": time.time(),
            **metrics
        }
        
        message_json = json.dumps(data)
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send metrics to client {client['id']}: {e}")
                self._remove_client(client)
    
    def send_sound_trigger(self, sound_name):
        """Trigger a sound effect on all connected clients"""
        if not self.running or not self.clients:
            return
        
        data = {
            "type": "sound",
            "sound": sound_name,
            "timestamp": time.time()
        }
        
        message_json = json.dumps(data)
        self._log_sent_message(data)
        
        for client in self.clients[:]:
            try:
                self.websocket_server.send_message(client, message_json)
            except Exception as e:
                self.logger.warning(f"Failed to send sound trigger to client {client['id']}: {e}")
                self._remove_client(client)
    
    def get_message_statistics(self):
        """Get message statistics"""
        uptime = time.time() - self.message_stats['start_time']
        
        return {
            'uptime_seconds': uptime,
            'uptime_formatted': f"{int(uptime // 3600)}h {int((uptime % 3600) // 60)}m {int(uptime % 60)}s",
            'total_sent': self.message_stats['total_sent'],
            'total_received': self.message_stats['total_received'],
            'total_messages': self.message_stats['total_sent'] + self.message_stats['total_received'],
            'messages_per_minute': (self.message_stats['total_sent'] + self.message_stats['total_received']) / (uptime / 60) if uptime > 0 else 0,
            'by_type': dict(self.message_stats['by_type']),
            'connected_clients': len(self.clients)
        }
    
    def print_statistics(self):
        """Print current statistics to console"""
        stats = self.get_message_statistics()
        
        print("\n" + "="*60)
        print("📊 M1K3 WebSocket Server Statistics")
        print("="*60)
        print(f"⏱️  Uptime: {stats['uptime_formatted']}")
        print(f"👥 Connected clients: {stats['connected_clients']}")
        print(f"📤 Messages sent: {stats['total_sent']}")
        print(f"📥 Messages received: {stats['total_received']}")
        print(f"💬 Total messages: {stats['total_messages']}")
        print(f"📊 Rate: {stats['messages_per_minute']:.1f} msg/min")
        
        if stats['by_type']:
            print(f"\n📋 Message types:")
            for msg_type, count in sorted(stats['by_type'].items(), key=lambda x: x[1], reverse=True):
                print(f"   {msg_type}: {count}")
        
        print("="*60)

# Global server instance
_avatar_server = None

def get_avatar_server():
    """Get or create the global avatar server instance"""
    global _avatar_server
    if _avatar_server is None:
        _avatar_server = AvatarServer(verbose=True)  # Enable verbose logging for debugging
    return _avatar_server

def start_avatar_server():
    """Start the avatar server"""
    server = get_avatar_server()
    return server.start()

def stop_avatar_server():
    """Stop the avatar server"""
    server = get_avatar_server()
    server.stop()

def is_avatar_server_running():
    """Check if avatar server is running"""
    server = get_avatar_server()
    return server.is_running()

def send_avatar_emotion(emotion, intensity=50, message=""):
    """Send emotion update to avatar"""
    server = get_avatar_server()
    server.send_emotion_update(emotion, intensity, message)

def send_avatar_state(state):
    """Send state update to avatar"""
    server = get_avatar_server()
    server.send_state_update(state)

def send_avatar_progress(stage, progress, tokens=0, message=""):
    """Send progress update to avatar"""
    server = get_avatar_server()
    server.send_progress_update(stage, progress, tokens, message)

def get_avatar_server_status():
    """Get avatar server status"""
    server = get_avatar_server()
    return server.get_status()

def send_chat_ai_start():
    """Notify clients that AI is starting to respond"""
    server = get_avatar_server()
    server.send_chat_ai_start()

def send_chat_ai_chunk(chunk):
    """Send AI response chunk to clients"""
    server = get_avatar_server()
    server.send_chat_ai_chunk(chunk)

def send_chat_ai_complete():
    """Notify clients that AI response is complete"""
    server = get_avatar_server()
    server.send_chat_ai_complete()

def send_sound_trigger(sound_name):
    """Trigger a sound effect on all clients"""
    server = get_avatar_server()
    server.send_sound_trigger(sound_name)

def send_metrics_update(metrics_data):
    """Send real-time metrics update to avatar"""
    server = get_avatar_server()
    server.send_metrics_update(metrics_data)

if __name__ == "__main__":
    # Command line interface
    parser = argparse.ArgumentParser(description="M1K3 Avatar Server")
    parser.add_argument('--port', '-p', type=int, default=8080, help='HTTP server port')
    parser.add_argument('--ws-port', type=int, default=8081, help='WebSocket server port')
    parser.add_argument('--verbose', '-v', action='store_true', help='Enable verbose logging')
    parser.add_argument('--stats', '-s', action='store_true', help='Show statistics every 30 seconds')
    
    args = parser.parse_args()
    
    # Create server with verbose logging if requested
    server = AvatarServer(http_port=args.port, ws_port=args.ws_port, verbose=args.verbose)
    
    if server.start():
        print("Server started successfully")
        
        if args.verbose:
            print("🔍 Verbose logging enabled - all WebSocket messages will be logged")
        
        if args.stats:
            print("📊 Statistics mode enabled - stats will be printed every 30 seconds")
        
        try:
            stats_counter = 0
            while True:
                time.sleep(1)
                stats_counter += 1
                
                # Print statistics every 30 seconds if enabled
                if args.stats and stats_counter >= 30:
                    server.print_statistics()
                    stats_counter = 0
                    
        except KeyboardInterrupt:
            print("\nShutting down...")
            if args.verbose or args.stats:
                server.print_statistics()
            server.stop()
    else:
        print("Failed to start server")