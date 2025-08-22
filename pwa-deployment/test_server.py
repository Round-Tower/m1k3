#!/usr/bin/env python3
"""
M1K3 PWA Local Test Server
Simple HTTP server for testing the PWA locally before Docker deployment
"""

import http.server
import socketserver
import os
import json
import threading
import time
import webbrowser
from pathlib import Path
from urllib.parse import urlparse, parse_qs

class M1K3PWAHandler(http.server.SimpleHTTPRequestHandler):
    """Custom handler for M1K3 PWA with API endpoints"""
    
    def __init__(self, *args, **kwargs):
        # Change to frontend directory
        os.chdir(Path(__file__).parent / "frontend")
        super().__init__(*args, **kwargs)
    
    def do_GET(self):
        """Handle GET requests with custom routing"""
        parsed_path = urlparse(self.path)
        path = parsed_path.path
        
        # API endpoints
        if path.startswith('/api/'):
            self.handle_api_request(path)
        elif path == '/models/deployment-manifest.json':
            self.serve_deployment_manifest()
        else:
            # Serve static files or default to index.html for PWA routing
            if path == '/' or path == '/index.html':
                self.serve_file('index.html')
            elif path.endswith('.js') or path.endswith('.css') or path.endswith('.json'):
                super().do_GET()
            elif path.startswith('/src/'):
                super().do_GET()
            else:
                # PWA routing - serve index.html for unknown routes
                self.serve_file('index.html')
    
    def handle_api_request(self, path):
        """Handle API requests"""
        if path == '/api/models':
            self.serve_json({
                "models": self.get_mock_models(),
                "total_models": 3,
                "available_models": 3
            })
        elif path.startswith('/api/models/'):
            tier = path.split('/')[-1]
            models = self.get_mock_models()
            if tier in models:
                self.serve_json(models[tier])
            else:
                self.send_error(404, "Model not found")
        else:
            self.send_error(404, "API endpoint not found")
    
    def serve_deployment_manifest(self):
        """Serve deployment manifest"""
        manifest = {
            "version": "1.0.0-dev",
            "models": self.get_mock_models(),
            "api_version": "v1",
            "features": {
                "device_detection": True,
                "progressive_loading": True,
                "offline_support": True,
                "webgpu_acceleration": True
            },
            "deployment": {
                "type": "development",
                "platform": "web",
                "build_date": "2025-08-22",
                "environment": "development"
            }
        }
        self.serve_json(manifest)
    
    def get_mock_models(self):
        """Get mock model information for testing"""
        return {
            "tiny": {
                "name": "m1k3-tiny-dev",
                "size_mb": 100,
                "description": "Development tiny model",
                "available": True,
                "min_memory_gb": 2,
                "capabilities": ["basic_chat"],
                "file": "tiny/model.onnx"
            },
            "small": {
                "name": "m1k3-small-dev",
                "size_mb": 350,
                "description": "Development small model",
                "available": True,
                "min_memory_gb": 4,
                "capabilities": ["chat", "qa"],
                "file": "small/model.onnx"
            },
            "medium": {
                "name": "m1k3-medium-dev",
                "size_mb": 800,
                "description": "Development medium model",
                "available": False,  # Not available in dev
                "min_memory_gb": 8,
                "capabilities": ["advanced_chat", "reasoning"],
                "file": "medium/model.onnx"
            }
        }
    
    def serve_json(self, data):
        """Serve JSON response with proper headers"""
        response = json.dumps(data, indent=2).encode('utf-8')
        
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(response)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Cache-Control', 'no-cache')
        self.end_headers()
        
        self.wfile.write(response)
    
    def serve_file(self, filename):
        """Serve a specific file with proper headers"""
        try:
            with open(filename, 'rb') as f:
                content = f.read()
            
            self.send_response(200)
            
            # Set content type based on file extension
            if filename.endswith('.html'):
                self.send_header('Content-Type', 'text/html')
                # PWA security headers
                self.send_header('Cross-Origin-Embedder-Policy', 'require-corp')
                self.send_header('Cross-Origin-Opener-Policy', 'same-origin')
            elif filename.endswith('.js'):
                self.send_header('Content-Type', 'application/javascript')
            elif filename.endswith('.css'):
                self.send_header('Content-Type', 'text/css')
            elif filename.endswith('.json'):
                self.send_header('Content-Type', 'application/json')
            
            self.send_header('Content-Length', str(len(content)))
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            
            self.wfile.write(content)
            
        except FileNotFoundError:
            self.send_error(404, f"File not found: {filename}")

def run_test_server(port=8080, open_browser=True):
    """Run the test server"""
    print(f"🚀 Starting M1K3 PWA Test Server on port {port}")
    print(f"📂 Serving from: {Path.cwd()}")
    
    try:
        with socketserver.TCPServer(("", port), M1K3PWAHandler) as httpd:
            server_url = f"http://localhost:{port}"
            print(f"🌐 Server running at: {server_url}")
            print("📱 Testing PWA features:")
            print("  ✓ Service Worker registration")
            print("  ✓ Device capability detection")  
            print("  ✓ Progressive model loading")
            print("  ✓ Mock API endpoints")
            print("\n🔧 Available endpoints:")
            print(f"  • {server_url}/ - Main PWA interface")
            print(f"  • {server_url}/api/models - Model listing")
            print(f"  • {server_url}/models/deployment-manifest.json - Deployment info")
            print("\n⌨️  Press Ctrl+C to stop the server")
            
            if open_browser:
                def open_browser_delayed():
                    time.sleep(1)
                    print(f"\n🌐 Opening browser to {server_url}")
                    webbrowser.open(server_url)
                
                threading.Thread(target=open_browser_delayed, daemon=True).start()
            
            httpd.serve_forever()
            
    except KeyboardInterrupt:
        print("\n🛑 Server stopped by user")
    except OSError as e:
        if e.errno == 48:  # Address already in use
            print(f"❌ Port {port} is already in use. Try a different port:")
            print(f"   python test_server.py --port {port + 1}")
        else:
            print(f"❌ Server error: {e}")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 PWA Test Server")
    parser.add_argument('--port', type=int, default=8080, help='Server port (default: 8080)')
    parser.add_argument('--no-browser', action='store_true', help='Don\'t open browser automatically')
    
    args = parser.parse_args()
    
    run_test_server(args.port, not args.no_browser)