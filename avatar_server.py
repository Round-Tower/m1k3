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
    
    def __init__(self, http_port=8080, ws_port=8081):
        self.http_port = http_port
        self.ws_port = ws_port
        self.http_server = None
        self.websocket_server = None
        self.http_thread = None
        self.ws_thread = None
        self.running = False
        self.clients = []
        
        # Setup logging
        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger('AvatarServer')
    
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
            self.logger.debug(f"Received from client {client['id']}: {data}")
            
            # Handle client messages if needed
            if data.get("type") == "ping":
                pong_data = {"type": "pong", "timestamp": time.time()}
                server.send_message(client, json.dumps(pong_data))
                
        except json.JSONDecodeError:
            self.logger.warning(f"Invalid JSON from client {client['id']}: {message}")
        except Exception as e:
            self.logger.error(f"Error handling message from client {client['id']}: {e}")

# Global server instance
_avatar_server = None

def get_avatar_server():
    """Get or create the global avatar server instance"""
    global _avatar_server
    if _avatar_server is None:
        _avatar_server = AvatarServer()
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

if __name__ == "__main__":
    # Test the server
    server = AvatarServer()
    if server.start():
        print("Server started successfully")
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\nShutting down...")
            server.stop()
    else:
        print("Failed to start server")