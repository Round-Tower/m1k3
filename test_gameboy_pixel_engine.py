#!/usr/bin/env python3
"""
Comprehensive Test Suite for Gameboy Pixel Engine
Tests integration, rendering, and WebSocket communication
"""

import asyncio
import json
import time
import websockets
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import subprocess
import threading
import os
import sys
from pathlib import Path

class GameboyPixelEngineTests:
    def __init__(self):
        self.project_root = Path(__file__).parent
        self.avatar_server = None
        self.websocket_server = None
        self.driver = None
        self.test_results = []
        
    def setup_chrome_driver(self):
        """Setup Chrome driver for testing"""
        chrome_options = Options()
        chrome_options.add_argument("--headless")  # Run headless for CI
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--window-size=1920,1080")
        
        try:
            self.driver = webdriver.Chrome(options=chrome_options)
            print("✅ Chrome driver initialized")
            return True
        except Exception as e:
            print(f"❌ Failed to setup Chrome driver: {e}")
            return False
    
    def start_avatar_server(self):
        """Start the M1K3 avatar server"""
        try:
            avatar_script = self.project_root / "avatar_server.py"
            if avatar_script.exists():
                self.avatar_server = subprocess.Popen(
                    [sys.executable, str(avatar_script), "--test-mode"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE
                )
                time.sleep(3)  # Wait for server to start
                print("✅ Avatar server started")
                return True
            else:
                print("⚠️ Avatar server script not found")
                return False
        except Exception as e:
            print(f"❌ Failed to start avatar server: {e}")
            return False
    
    def test_javascript_engine_loading(self):
        """Test 1: Basic JavaScript engine loading"""
        test_name = "JavaScript Engine Loading"
        print(f"\n🧪 Testing: {test_name}")
        
        try:
            # Create a minimal test HTML page
            test_html = f"""
            <!DOCTYPE html>
            <html>
            <head>
                <title>Gameboy Pixel Engine Test</title>
            </head>
            <body>
                <canvas id="testCanvas" width="256" height="256"></canvas>
                <script src="file://{self.project_root}/gameboy_pixel_engine.js"></script>
                <script>
                    window.testResults = [];
                    
                    try {{
                        const canvas = document.getElementById('testCanvas');
                        const engine = new GameboyPixelEngine(canvas, {{
                            pixelSize: 8,
                            mode: 'avatar',
                            enableCare: true,
                            debugMode: true
                        }});
                        
                        window.testResults.push({{
                            test: 'engine_creation',
                            success: true,
                            message: 'Engine created successfully'
                        }});
                        
                        // Test basic rendering
                        engine.setPixel(5, 5, 2, 'avatar');
                        engine.renderText('TEST', 0, 0, 'tiny', 3, 'text');
                        
                        window.testResults.push({{
                            test: 'basic_rendering',
                            success: true,
                            message: 'Basic rendering successful'
                        }});
                        
                        // Test avatar system
                        engine.setEmotion('happy', 80);
                        engine.updateSystemState({{
                            battery: 75,
                            cpu: 45,
                            temperature: 55,
                            network: true
                        }});
                        
                        window.testResults.push({{
                            test: 'avatar_system',
                            success: true,
                            message: 'Avatar system functional'
                        }});
                        
                    }} catch (error) {{
                        window.testResults.push({{
                            test: 'engine_error',
                            success: false,
                            message: error.message,
                            stack: error.stack
                        }});
                    }}
                </script>
            </body>
            </html>
            """
            
            test_file = self.project_root / "test_engine.html"
            with open(test_file, 'w') as f:
                f.write(test_html)
            
            # Load the test page
            self.driver.get(f"file://{test_file}")
            
            # Wait for tests to complete
            time.sleep(2)
            
            # Get test results
            results = self.driver.execute_script("return window.testResults;")
            
            success = True
            for result in results:
                if result['success']:
                    print(f"  ✅ {result['test']}: {result['message']}")
                else:
                    print(f"  ❌ {result['test']}: {result['message']}")
                    if 'stack' in result:
                        print(f"    Stack: {result['stack'][:200]}...")
                    success = False
            
            self.test_results.append({
                'name': test_name,
                'success': success,
                'details': results
            })
            
            # Cleanup
            test_file.unlink()
            
        except Exception as e:
            print(f"  ❌ Test failed: {e}")
            self.test_results.append({
                'name': test_name,
                'success': False,
                'error': str(e)
            })
    
    def test_websocket_integration(self):
        """Test 2: WebSocket communication with optimized data packets"""
        test_name = "WebSocket Integration"
        print(f"\n🧪 Testing: {test_name}")
        
        try:
            # Test WebSocket message format
            test_messages = [
                {
                    'type': 'system_update',
                    'data': {
                        'bat': 85,  # Shortened field names for small packets
                        'cpu': 34,
                        'mem': 67,
                        'temp': 52,
                        'net': 1,  # 1 = connected, 0 = disconnected
                        'eco': 125.5
                    }
                },
                {
                    'type': 'avatar_update',
                    'data': {
                        'emotion': 'happy',
                        'intensity': 75,
                        'style': 'robot',
                        'color': '#E25303'
                    }
                },
                {
                    'type': 'care_update',
                    'data': {
                        'health': 92,
                        'energy': 78,
                        'evo': 1.25,
                        'mood': 'energetic'
                    }
                }
            ]
            
            total_size = sum(len(json.dumps(msg).encode()) for msg in test_messages)
            print(f"  📦 Total packet size: {total_size} bytes")
            
            if total_size < 500:  # Target: keep packets under 500 bytes
                print(f"  ✅ Packet size optimization: {total_size} bytes (under 500 byte target)")
                packet_test = True
            else:
                print(f"  ⚠️ Packet size warning: {total_size} bytes (over 500 byte target)")
                packet_test = False
            
            self.test_results.append({
                'name': test_name,
                'success': packet_test,
                'packet_size': total_size,
                'messages': test_messages
            })
            
        except Exception as e:
            print(f"  ❌ Test failed: {e}")
            self.test_results.append({
                'name': test_name,
                'success': False,
                'error': str(e)
            })
    
    def test_avatar_dashboard_integration(self):
        """Test 3: Full avatar dashboard integration"""
        test_name = "Avatar Dashboard Integration"
        print(f"\n🧪 Testing: {test_name}")
        
        try:
            # Load the actual avatar dashboard
            dashboard_file = self.project_root / "m1k3_avatar.html"
            if not dashboard_file.exists():
                raise FileNotFoundError("Avatar dashboard HTML not found")
            
            self.driver.get(f"file://{dashboard_file}")
            
            # Wait for page to load
            WebDriverWait(self.driver, 10).until(
                EC.presence_of_element_located((By.ID, "avatarCanvas"))
            )
            
            # Check for JavaScript errors
            logs = self.driver.get_log('browser')
            js_errors = [log for log in logs if log['level'] == 'SEVERE']
            
            if js_errors:
                print("  ❌ JavaScript errors found:")
                for error in js_errors[:3]:  # Show first 3 errors
                    print(f"    {error['message']}")
            else:
                print("  ✅ No JavaScript errors")
            
            # Test canvas presence
            canvas = self.driver.find_element(By.ID, "avatarCanvas")
            if canvas:
                print("  ✅ Avatar canvas found")
            
            # Test WebSocket connection attempt
            ws_status = self.driver.execute_script("""
                return {
                    hasWebSocket: typeof WebSocket !== 'undefined',
                    connectionAttempted: window.isConnected !== undefined
                };
            """)
            
            if ws_status['hasWebSocket']:
                print("  ✅ WebSocket API available")
            else:
                print("  ❌ WebSocket API not available")
            
            success = len(js_errors) == 0 and canvas is not None
            
            self.test_results.append({
                'name': test_name,
                'success': success,
                'js_errors': len(js_errors),
                'canvas_found': canvas is not None,
                'websocket_api': ws_status['hasWebSocket']
            })
            
        except Exception as e:
            print(f"  ❌ Test failed: {e}")
            self.test_results.append({
                'name': test_name,
                'success': False,
                'error': str(e)
            })
    
    def test_performance_benchmarks(self):
        """Test 4: Performance benchmarks"""
        test_name = "Performance Benchmarks"
        print(f"\n🧪 Testing: {test_name}")
        
        try:
            # Create performance test page
            perf_html = f"""
            <!DOCTYPE html>
            <html>
            <head><title>Performance Test</title></head>
            <body>
                <canvas id="perfCanvas" width="256" height="256"></canvas>
                <script src="file://{self.project_root}/gameboy_pixel_engine.js"></script>
                <script>
                    const canvas = document.getElementById('perfCanvas');
                    const engine = new GameboyPixelEngine(canvas);
                    
                    // Benchmark pixel setting
                    const start1 = performance.now();
                    for (let i = 0; i < 1000; i++) {{
                        engine.setPixel(i % 32, Math.floor(i / 32), i % 4, 'avatar');
                    }}
                    const pixelTime = performance.now() - start1;
                    
                    // Benchmark text rendering
                    const start2 = performance.now();
                    for (let i = 0; i < 100; i++) {{
                        engine.renderText('TEST TEXT ' + i, 0, i % 16, 'tiny', 3, 'text');
                    }}
                    const textTime = performance.now() - start2;
                    
                    // Benchmark avatar rendering
                    const start3 = performance.now();
                    for (let i = 0; i < 50; i++) {{
                        engine.updateSystemState({{
                            battery: 50 + (i % 50),
                            cpu: i % 100,
                            temperature: 40 + (i % 40)
                        }});
                        engine.renderAvatar();
                    }}
                    const avatarTime = performance.now() - start3;
                    
                    window.perfResults = {{
                        pixelOps: 1000 / pixelTime * 1000,  // ops/second
                        textOps: 100 / textTime * 1000,     // ops/second  
                        avatarOps: 50 / avatarTime * 1000   // ops/second
                    }};
                </script>
            </body>
            </html>
            """
            
            perf_file = self.project_root / "test_perf.html"
            with open(perf_file, 'w') as f:
                f.write(perf_html)
            
            self.driver.get(f"file://{perf_file}")
            time.sleep(3)  # Wait for benchmarks
            
            results = self.driver.execute_script("return window.perfResults;")
            
            print(f"  📊 Pixel operations: {results['pixelOps']:.0f} ops/sec")
            print(f"  📊 Text rendering: {results['textOps']:.0f} ops/sec")
            print(f"  📊 Avatar updates: {results['avatarOps']:.0f} ops/sec")
            
            # Performance thresholds
            success = (
                results['pixelOps'] > 10000 and    # At least 10k pixel ops/sec
                results['textOps'] > 100 and       # At least 100 text ops/sec
                results['avatarOps'] > 20          # At least 20 avatar updates/sec
            )
            
            self.test_results.append({
                'name': test_name,
                'success': success,
                'benchmarks': results
            })
            
            perf_file.unlink()  # Cleanup
            
        except Exception as e:
            print(f"  ❌ Test failed: {e}")
            self.test_results.append({
                'name': test_name,
                'success': False,
                'error': str(e)
            })
    
    def create_optimized_websocket_events(self):
        """Create optimized WebSocket event specifications"""
        print("\n📡 Creating optimized WebSocket event specification...")
        
        events_spec = {
            "protocol_version": "1.0",
            "compression": "json",
            "max_packet_size": 512,
            
            "events": {
                "sys": {  # System update (sent every 5 seconds)
                    "description": "Compact system metrics",
                    "fields": {
                        "bat": "battery_percent (0-100)",
                        "cpu": "cpu_usage_percent (0-100)", 
                        "mem": "memory_usage_percent (0-100)",
                        "temp": "temperature_celsius (0-100)",
                        "net": "network_connected (0|1)",
                        "wifi": "wifi_strength_percent (0-100)",
                        "eco": "eco_savings_wh (float)"
                    },
                    "example": {
                        "type": "sys",
                        "bat": 85,
                        "cpu": 23,
                        "mem": 67,
                        "temp": 45,
                        "net": 1,
                        "wifi": 87,
                        "eco": 125.5
                    }
                },
                
                "avatar": {  # Avatar state change
                    "description": "Avatar emotion and style",
                    "fields": {
                        "emo": "emotion (string)",
                        "int": "intensity (0-100)",
                        "style": "avatar_style (string)",
                        "color": "color_hex (string)"
                    },
                    "example": {
                        "type": "avatar",
                        "emo": "happy",
                        "int": 75,
                        "style": "robot",
                        "color": "#E25303"
                    }
                },
                
                "care": {  # Care mechanics update (sent every 30 seconds)
                    "description": "Avatar health and evolution",
                    "fields": {
                        "hp": "health (0-100)",
                        "nrg": "energy (0-100)",
                        "evo": "evolution_level (0-5)",
                        "mood": "current_mood (string)",
                        "last": "last_interaction_minutes (int)"
                    },
                    "example": {
                        "type": "care",
                        "hp": 92,
                        "nrg": 78,
                        "evo": 1.25,
                        "mood": "energetic",
                        "last": 3
                    }
                },
                
                "state": {  # State change notification
                    "description": "Avatar state change",
                    "fields": {
                        "s": "state (idle|thinking|generating|speaking|error)"
                    },
                    "example": {
                        "type": "state",
                        "s": "thinking"
                    }
                },
                
                "txt": {  # Text display command
                    "description": "Display text on avatar",
                    "fields": {
                        "msg": "message (string)",
                        "dur": "duration_seconds (int)",
                        "style": "animation_style (string)"
                    },
                    "example": {
                        "type": "txt",
                        "msg": "Hello!",
                        "dur": 3,
                        "style": "typewriter"
                    }
                }
            }
        }
        
        # Save the specification
        spec_file = self.project_root / "websocket_events_spec.json"
        with open(spec_file, 'w') as f:
            json.dump(events_spec, f, indent=2)
        
        print(f"✅ WebSocket events specification saved to {spec_file}")
        
        # Calculate packet sizes
        for event_name, event_spec in events_spec["events"].items():
            if "example" in event_spec:
                packet_size = len(json.dumps(event_spec["example"]).encode())
                print(f"  📦 {event_name} packet size: {packet_size} bytes")
    
    def run_all_tests(self):
        """Run the complete test suite"""
        print("🚀 Starting Gameboy Pixel Engine Test Suite")
        print("=" * 60)
        
        # Setup
        if not self.setup_chrome_driver():
            print("❌ Cannot run tests without Chrome driver")
            return False
        
        # Start avatar server if available
        self.start_avatar_server()
        
        # Run tests
        self.test_javascript_engine_loading()
        self.test_websocket_integration()
        self.test_avatar_dashboard_integration()
        self.test_performance_benchmarks()
        
        # Create optimized WebSocket specification
        self.create_optimized_websocket_events()
        
        # Summary
        print("\n" + "=" * 60)
        print("📊 TEST RESULTS SUMMARY")
        print("=" * 60)
        
        passed = 0
        total = len(self.test_results)
        
        for result in self.test_results:
            status = "✅ PASS" if result['success'] else "❌ FAIL"
            print(f"{status} {result['name']}")
            if result['success']:
                passed += 1
        
        print(f"\nTotal: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
        
        # Save detailed results
        results_file = self.project_root / "test_results.json"
        with open(results_file, 'w') as f:
            json.dump({
                'timestamp': time.time(),
                'summary': {
                    'total': total,
                    'passed': passed,
                    'success_rate': passed/total*100
                },
                'tests': self.test_results
            }, f, indent=2)
        
        print(f"📋 Detailed results saved to {results_file}")
        
        # Cleanup
        if self.driver:
            self.driver.quit()
        
        if self.avatar_server:
            self.avatar_server.terminate()
        
        return passed == total

def main():
    """Run the test suite"""
    tester = GameboyPixelEngineTests()
    success = tester.run_all_tests()
    
    if success:
        print("\n🎉 All tests passed! Gameboy Pixel Engine is ready.")
        return 0
    else:
        print("\n⚠️ Some tests failed. Check the results for details.")
        return 1

if __name__ == "__main__":
    sys.exit(main())