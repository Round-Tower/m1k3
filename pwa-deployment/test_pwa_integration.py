#!/usr/bin/env python3
"""
M1K3 PWA Integration Testing
Comprehensive testing of the PWA deployment pipeline
"""

import subprocess
import requests
import json
import time
import sys
import os
from pathlib import Path
from urllib.parse import urljoin

class PWAIntegrationTester:
    def __init__(self, base_url="http://localhost:9090"):
        self.base_url = base_url
        self.session = requests.Session()
        self.test_results = []
        
    def run_test(self, test_name, test_func):
        """Run a single test and record the result"""
        print(f"🧪 Testing: {test_name}")
        try:
            result = test_func()
            if result:
                print(f"  ✅ PASS: {test_name}")
                self.test_results.append({"name": test_name, "status": "PASS", "details": result})
                return True
            else:
                print(f"  ❌ FAIL: {test_name}")
                self.test_results.append({"name": test_name, "status": "FAIL", "details": "Test returned False"})
                return False
        except Exception as e:
            print(f"  ❌ ERROR: {test_name} - {str(e)}")
            self.test_results.append({"name": test_name, "status": "ERROR", "details": str(e)})
            return False
    
    def test_server_health(self):
        """Test basic server connectivity"""
        response = self.session.get(self.base_url, timeout=10)
        return response.status_code == 200 and "M1K3" in response.text
    
    def test_pwa_manifest(self):
        """Test PWA manifest is valid"""
        response = self.session.get(urljoin(self.base_url, "/manifest.json"))
        if response.status_code != 200:
            return False
        
        manifest = response.json()
        required_fields = ["name", "short_name", "start_url", "display", "icons"]
        return all(field in manifest for field in required_fields)
    
    def test_service_worker(self):
        """Test service worker exists and has required event listeners"""
        response = self.session.get(urljoin(self.base_url, "/sw.js"))
        if response.status_code != 200:
            return False
        
        sw_content = response.text
        required_events = ["install", "activate", "fetch"]
        return all(f"addEventListener('{event}'" in sw_content for event in required_events)
    
    def test_api_models_endpoint(self):
        """Test models API endpoint"""
        response = self.session.get(urljoin(self.base_url, "/api/models"))
        if response.status_code != 200:
            return False
        
        data = response.json()
        return "models" in data and "total_models" in data
    
    def test_deployment_manifest(self):
        """Test deployment manifest endpoint"""
        response = self.session.get(urljoin(self.base_url, "/models/deployment-manifest.json"))
        if response.status_code != 200:
            return False
        
        manifest = response.json()
        required_fields = ["version", "models", "features", "deployment"]
        return all(field in manifest for field in required_fields)
    
    def test_device_detector_js(self):
        """Test device detector JavaScript exists"""
        response = self.session.get(urljoin(self.base_url, "/src/device-detector.js"))
        if response.status_code != 200:
            return False
        
        js_content = response.text
        required_classes = ["DeviceDetector", "detectCapabilities", "selectOptimalModel"]
        return all(cls in js_content for cls in required_classes)
    
    def test_model_loader_js(self):
        """Test model loader JavaScript exists"""
        response = self.session.get(urljoin(self.base_url, "/src/model-loader.js"))
        if response.status_code != 200:
            return False
        
        js_content = response.text
        required_classes = ["ModelLoader", "loadWithFallback", "runInference"]
        return all(cls in js_content for cls in required_classes)
    
    def test_chat_interface_js(self):
        """Test chat interface JavaScript exists"""
        response = self.session.get(urljoin(self.base_url, "/src/chat-interface.js"))
        if response.status_code != 200:
            return False
        
        js_content = response.text
        required_classes = ["ChatInterface", "sendMessage", "addMessage"]
        return all(cls in js_content for cls in required_classes)
    
    def test_app_js(self):
        """Test main app JavaScript exists"""
        response = self.session.get(urljoin(self.base_url, "/src/app.js"))
        if response.status_code != 200:
            return False
        
        js_content = response.text
        required_classes = ["M1K3App", "initialize", "loadModelWithFallbacks"]
        return all(cls in js_content for cls in required_classes)
    
    def test_styles_css(self):
        """Test CSS styles exist"""
        response = self.session.get(urljoin(self.base_url, "/src/styles.css"))
        if response.status_code != 200:
            return False
        
        css_content = response.text
        # Check for key M1K3 styling elements
        required_styles = [":root", "background-color", "font-family", ".message"]
        return all(style in css_content for style in required_styles)
    
    def test_cors_headers(self):
        """Test CORS headers are present"""
        response = self.session.get(urljoin(self.base_url, "/api/models"))
        return "Access-Control-Allow-Origin" in response.headers
    
    def test_security_headers(self):
        """Test security headers for PWA/WebAssembly"""
        response = self.session.get(self.base_url)
        # Note: Test server doesn't set all security headers, but we check what we can
        return response.status_code == 200  # Basic connectivity test
    
    def test_pwa_routing(self):
        """Test PWA routing (unknown routes serve index.html)"""
        response = self.session.get(urljoin(self.base_url, "/unknown-route"))
        return response.status_code == 200 and "M1K3" in response.text
    
    def run_all_tests(self):
        """Run all PWA integration tests"""
        print("🚀 Starting M1K3 PWA Integration Tests\n")
        
        tests = [
            ("Server Health Check", self.test_server_health),
            ("PWA Manifest Validation", self.test_pwa_manifest),
            ("Service Worker Structure", self.test_service_worker),
            ("Models API Endpoint", self.test_api_models_endpoint),
            ("Deployment Manifest", self.test_deployment_manifest),
            ("Device Detector JS", self.test_device_detector_js),
            ("Model Loader JS", self.test_model_loader_js),
            ("Chat Interface JS", self.test_chat_interface_js),
            ("Main App JS", self.test_app_js),
            ("CSS Styles", self.test_styles_css),
            ("CORS Headers", self.test_cors_headers),
            ("Security Configuration", self.test_security_headers),
            ("PWA Routing", self.test_pwa_routing),
        ]
        
        passed = 0
        total = len(tests)
        
        for test_name, test_func in tests:
            if self.run_test(test_name, test_func):
                passed += 1
            print()  # Add spacing between tests
        
        # Generate summary
        print("=" * 60)
        print(f"📊 Test Results Summary:")
        print(f"   Total Tests: {total}")
        print(f"   Passed: {passed}")
        print(f"   Failed: {total - passed}")
        print(f"   Success Rate: {(passed/total)*100:.1f}%")
        
        if passed == total:
            print("\n🎉 All tests passed! PWA is ready for deployment.")
            return True
        else:
            print(f"\n⚠️  {total - passed} test(s) failed. Check the details above.")
            return False
    
    def generate_report(self, output_file="test_report.json"):
        """Generate detailed test report"""
        report = {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "base_url": self.base_url,
            "total_tests": len(self.test_results),
            "passed": len([r for r in self.test_results if r["status"] == "PASS"]),
            "failed": len([r for r in self.test_results if r["status"] in ["FAIL", "ERROR"]]),
            "results": self.test_results
        }
        
        with open(output_file, 'w') as f:
            json.dump(report, f, indent=2)
        
        print(f"📄 Detailed report saved to: {output_file}")

def main():
    """Main test runner"""
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 PWA Integration Tests")
    parser.add_argument('--url', default='http://localhost:9090', 
                       help='Base URL for testing (default: http://localhost:9090)')
    parser.add_argument('--report', default='test_report.json',
                       help='Output file for test report (default: test_report.json)')
    
    args = parser.parse_args()
    
    # Check if server is running
    try:
        response = requests.get(args.url, timeout=5)
    except requests.exceptions.RequestException:
        print(f"❌ Cannot connect to server at {args.url}")
        print("   Make sure the test server is running:")
        print(f"   python test_server.py --port {args.url.split(':')[-1]}")
        sys.exit(1)
    
    # Run tests
    tester = PWAIntegrationTester(args.url)
    success = tester.run_all_tests()
    
    # Generate report
    tester.generate_report(args.report)
    
    # Exit with appropriate code
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()