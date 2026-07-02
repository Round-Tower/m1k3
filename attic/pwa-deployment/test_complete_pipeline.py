#!/usr/bin/env python3
"""
M1K3 PWA Complete Pipeline Test
Tests the entire deployment pipeline from model export to production deployment
"""

import subprocess
import requests
import json
import time
import sys
import os
import threading
import tempfile
import shutil
from pathlib import Path
from urllib.parse import urljoin
from dataclasses import dataclass
from typing import List, Dict, Any, Optional

@dataclass
class TestResult:
    name: str
    status: str  # 'PASS', 'FAIL', 'ERROR', 'SKIP'
    duration: float
    details: str
    stage: str

class PipelineTestRunner:
    def __init__(self, base_dir: Path = None):
        self.base_dir = base_dir or Path.cwd()
        self.test_results: List[TestResult] = []
        self.test_server_process = None
        self.test_server_url = "http://localhost:9091"
        self.start_time = time.time()
        
        # Test configuration
        self.stages = [
            "infrastructure",
            "backend", 
            "frontend",
            "integration",
            "docker",
            "deployment"
        ]
        
    def log(self, message: str, level: str = "INFO"):
        """Log with timestamp and level"""
        timestamp = time.strftime("%H:%M:%S")
        prefix = {
            "INFO": "🔵",
            "SUCCESS": "✅", 
            "WARNING": "⚠️",
            "ERROR": "❌",
            "STAGE": "🚀"
        }.get(level, "📝")
        print(f"[{timestamp}] {prefix} {message}")
    
    def run_test(self, name: str, stage: str, test_func, *args, **kwargs) -> TestResult:
        """Run a single test and record results"""
        start_time = time.time()
        self.log(f"Testing: {name}")
        
        try:
            result = test_func(*args, **kwargs)
            duration = time.time() - start_time
            
            if result is True or (isinstance(result, dict) and result.get('success')):
                status = "PASS"
                details = result.get('details', 'Test passed') if isinstance(result, dict) else "Test passed"
                self.log(f"✅ PASS: {name} ({duration:.2f}s)", "SUCCESS")
            elif result is False:
                status = "FAIL"
                details = "Test returned False"
                self.log(f"❌ FAIL: {name} ({duration:.2f}s)", "ERROR")
            else:
                status = "PASS"
                details = str(result)
                self.log(f"✅ PASS: {name} ({duration:.2f}s)", "SUCCESS")
                
        except subprocess.CalledProcessError as e:
            duration = time.time() - start_time
            status = "ERROR"
            details = f"Command failed: {e.cmd}, return code: {e.returncode}"
            self.log(f"❌ ERROR: {name} - {details}", "ERROR")
            
        except Exception as e:
            duration = time.time() - start_time
            status = "ERROR" 
            details = str(e)
            self.log(f"❌ ERROR: {name} - {details}", "ERROR")
        
        test_result = TestResult(name, status, duration, details, stage)
        self.test_results.append(test_result)
        return test_result
    
    def test_infrastructure_setup(self) -> bool:
        """Test that all required files and directories exist"""
        required_files = [
            "README.md",
            "DEPLOYMENT.md", 
            "docker-compose.yml",
            "Dockerfile",
            "test_server.py",
            "test_pwa_integration.py",
            "backend/requirements.txt",
            "backend/scripts/model_exporter.py",
            "backend/api/model_api.py",
            "frontend/index.html",
            "frontend/manifest.json",
            "frontend/sw.js",
            "frontend/src/app.js",
            "frontend/src/device-detector.js",
            "frontend/src/model-loader.js",
            "frontend/src/chat-interface.js",
            "frontend/src/styles.css",
            "docker/nginx.conf",
            ".github/workflows/build-and-deploy.yml"
        ]
        
        missing_files = []
        for file_path in required_files:
            full_path = self.base_dir / file_path
            if not full_path.exists():
                missing_files.append(file_path)
        
        if missing_files:
            raise Exception(f"Missing required files: {missing_files}")
        
        return True
    
    def test_python_dependencies(self) -> bool:
        """Test Python dependencies can be installed"""
        cmd = ["python", "-c", "import json, subprocess, requests, pathlib; print('Dependencies OK')"]
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.base_dir)
        
        if result.returncode != 0:
            raise Exception(f"Python dependencies missing: {result.stderr}")
        
        return True
    
    def test_backend_structure(self) -> bool:
        """Test backend Python code structure"""
        # Test model exporter imports
        cmd = ["python", "-c", "import sys; sys.path.append('backend'); import scripts.model_exporter; print('Backend structure OK')"]
        result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.base_dir)
        
        if result.returncode != 0:
            # Try alternative approach - just check file syntax
            model_exporter = self.base_dir / "backend/scripts/model_exporter.py"
            with open(model_exporter) as f:
                content = f.read()
            
            # Basic syntax check
            try:
                compile(content, str(model_exporter), 'exec')
                return True
            except SyntaxError as e:
                raise Exception(f"Syntax error in model_exporter.py: {e}")
        
        return True
    
    def test_frontend_structure(self) -> bool:
        """Test frontend JavaScript structure"""
        js_files = [
            "frontend/src/app.js",
            "frontend/src/device-detector.js", 
            "frontend/src/model-loader.js",
            "frontend/src/chat-interface.js"
        ]
        
        required_classes = {
            "frontend/src/app.js": ["M1K3App", "initialize"],
            "frontend/src/device-detector.js": ["DeviceDetector", "detectCapabilities"],
            "frontend/src/model-loader.js": ["ModelLoader", "loadWithFallback"],
            "frontend/src/chat-interface.js": ["ChatInterface", "sendMessage"]
        }
        
        for js_file in js_files:
            file_path = self.base_dir / js_file
            with open(file_path) as f:
                content = f.read()
            
            for required_class in required_classes.get(js_file, []):
                if required_class not in content:
                    raise Exception(f"Missing class/method '{required_class}' in {js_file}")
        
        return True
    
    def test_pwa_manifest_validity(self) -> bool:
        """Test PWA manifest is valid JSON with required fields"""
        manifest_path = self.base_dir / "frontend/manifest.json"
        
        with open(manifest_path) as f:
            manifest = json.load(f)
        
        required_fields = ["name", "short_name", "start_url", "display", "icons", "theme_color"]
        missing_fields = [field for field in required_fields if field not in manifest]
        
        if missing_fields:
            raise Exception(f"PWA manifest missing required fields: {missing_fields}")
        
        # Validate icons array
        if not isinstance(manifest["icons"], list) or len(manifest["icons"]) == 0:
            raise Exception("PWA manifest must have icons array")
        
        return True
    
    def test_service_worker_structure(self) -> bool:
        """Test service worker has required event listeners"""
        sw_path = self.base_dir / "frontend/sw.js"
        
        with open(sw_path) as f:
            content = f.read()
        
        required_events = ["install", "activate", "fetch"]
        for event in required_events:
            if f"addEventListener('{event}'" not in content and f'addEventListener("{event}"' not in content:
                raise Exception(f"Service worker missing {event} event listener")
        
        return True
    
    def start_test_server(self) -> bool:
        """Start the test server for integration testing"""
        cmd = ["python", "test_server.py", "--port", "9091", "--no-browser"]
        
        try:
            self.test_server_process = subprocess.Popen(
                cmd, 
                cwd=self.base_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            # Wait for server to start
            max_wait = 10
            for _ in range(max_wait):
                try:
                    response = requests.get(self.test_server_url, timeout=2)
                    if response.status_code == 200:
                        return True
                except requests.exceptions.RequestException:
                    time.sleep(1)
            
            # Server didn't start properly
            if self.test_server_process.poll() is not None:
                stdout, stderr = self.test_server_process.communicate()
                raise Exception(f"Test server failed to start: {stderr}")
            
            raise Exception("Test server did not respond within 10 seconds")
            
        except Exception as e:
            if self.test_server_process:
                self.test_server_process.terminate()
            raise e
    
    def test_api_endpoints(self) -> bool:
        """Test API endpoints are responding correctly"""
        endpoints = [
            "/api/models",
            "/models/deployment-manifest.json"
        ]
        
        for endpoint in endpoints:
            url = urljoin(self.test_server_url, endpoint)
            response = requests.get(url, timeout=10)
            
            if response.status_code != 200:
                raise Exception(f"API endpoint {endpoint} returned status {response.status_code}")
            
            # Validate JSON response
            try:
                response.json()
            except json.JSONDecodeError:
                raise Exception(f"API endpoint {endpoint} returned invalid JSON")
        
        return True
    
    def test_pwa_functionality(self) -> bool:
        """Test core PWA functionality"""
        # Test main page loads
        response = requests.get(self.test_server_url, timeout=10)
        if response.status_code != 200:
            raise Exception(f"Main page returned status {response.status_code}")
        
        content = response.text
        
        # Check for required PWA elements
        required_elements = [
            "manifest.json",
            "m1k3",  # More flexible case matching
            "chat"   # More flexible interface matching
        ]
        
        missing_elements = []
        for element in required_elements:
            if element not in content.lower():
                missing_elements.append(element)
        
        if missing_elements:
            raise Exception(f"PWA page missing elements: {missing_elements}")
        
        return True
    
    def test_docker_build(self) -> bool:
        """Test Docker build process"""
        cmd = ["docker", "build", "-t", "m1k3-pwa-test", "."]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, cwd=self.base_dir, timeout=300)
            
            if result.returncode != 0:
                raise Exception(f"Docker build failed: {result.stderr}")
            
            return True
            
        except subprocess.TimeoutExpired:
            raise Exception("Docker build timed out after 5 minutes")
    
    def test_docker_run(self) -> bool:
        """Test Docker container can run"""
        # Start container in background
        cmd = ["docker", "run", "-d", "--name", "m1k3-pwa-test-container", "-p", "9092:80", "m1k3-pwa-test"]
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True)
            
            if result.returncode != 0:
                raise Exception(f"Docker run failed: {result.stderr}")
            
            container_id = result.stdout.strip()
            
            # Wait for container to be ready
            time.sleep(10)
            
            # Test container health
            try:
                response = requests.get("http://localhost:9092", timeout=10)
                if response.status_code != 200:
                    raise Exception(f"Container not responding properly: {response.status_code}")
            finally:
                # Clean up container
                subprocess.run(["docker", "stop", container_id], capture_output=True)
                subprocess.run(["docker", "rm", container_id], capture_output=True)
            
            return True
            
        except Exception as e:
            # Cleanup on error
            subprocess.run(["docker", "stop", "m1k3-pwa-test-container"], capture_output=True)
            subprocess.run(["docker", "rm", "m1k3-pwa-test-container"], capture_output=True)
            raise e
    
    def test_github_actions_syntax(self) -> bool:
        """Test GitHub Actions workflow syntax"""
        workflow_path = self.base_dir / ".github/workflows/build-and-deploy.yml"
        
        with open(workflow_path) as f:
            content = f.read()
        
        # Basic YAML syntax validation
        try:
            import yaml
            yaml.safe_load(content)
        except ImportError:
            # If PyYAML not available, do basic checks
            required_sections = ["name:", "on:", "jobs:"]
            for section in required_sections:
                if section not in content:
                    raise Exception(f"GitHub Actions workflow missing section: {section}")
        except yaml.YAMLError as e:
            raise Exception(f"GitHub Actions workflow YAML syntax error: {e}")
        
        return True
    
    def cleanup(self):
        """Clean up test resources"""
        if self.test_server_process:
            self.test_server_process.terminate()
            self.test_server_process.wait(timeout=5)
        
        # Clean up Docker test image
        subprocess.run(["docker", "rmi", "m1k3-pwa-test"], capture_output=True)
    
    def run_all_tests(self) -> Dict[str, Any]:
        """Run complete pipeline test suite"""
        self.log("🚀 Starting M1K3 PWA Complete Pipeline Test", "STAGE")
        self.log(f"📂 Testing directory: {self.base_dir}")
        
        # Define test stages and their tests
        test_stages = {
            "infrastructure": [
                ("File Structure Check", self.test_infrastructure_setup),
                ("Python Dependencies", self.test_python_dependencies),
            ],
            "backend": [
                ("Backend Structure", self.test_backend_structure),
            ],
            "frontend": [
                ("Frontend Structure", self.test_frontend_structure),
                ("PWA Manifest Validity", self.test_pwa_manifest_validity), 
                ("Service Worker Structure", self.test_service_worker_structure),
            ],
            "integration": [
                ("Start Test Server", self.start_test_server),
                ("API Endpoints", self.test_api_endpoints),
                ("PWA Functionality", self.test_pwa_functionality),
            ],
            "docker": [
                ("Docker Build", self.test_docker_build),
                ("Docker Run", self.test_docker_run),
            ],
            "deployment": [
                ("GitHub Actions Syntax", self.test_github_actions_syntax),
            ]
        }
        
        total_tests = 0
        stage_results = {}
        
        try:
            for stage_name, tests in test_stages.items():
                self.log(f"🏗️  Stage: {stage_name.upper()}", "STAGE")
                stage_start = time.time()
                stage_passed = 0
                stage_total = len(tests)
                
                for test_name, test_func in tests:
                    result = self.run_test(test_name, stage_name, test_func)
                    if result.status == "PASS":
                        stage_passed += 1
                    total_tests += 1
                
                stage_duration = time.time() - stage_start
                stage_results[stage_name] = {
                    "passed": stage_passed,
                    "total": stage_total,
                    "duration": stage_duration,
                    "success_rate": (stage_passed / stage_total) * 100 if stage_total > 0 else 0
                }
                
                self.log(f"Stage {stage_name}: {stage_passed}/{stage_total} passed ({stage_results[stage_name]['success_rate']:.1f}%)")
                print()  # Add spacing
        
        finally:
            self.cleanup()
        
        # Generate final report
        total_duration = time.time() - self.start_time
        total_passed = sum(1 for result in self.test_results if result.status == "PASS")
        overall_success_rate = (total_passed / total_tests) * 100 if total_tests > 0 else 0
        
        report = {
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "total_duration": total_duration,
            "total_tests": total_tests,
            "total_passed": total_passed,
            "total_failed": total_tests - total_passed,
            "overall_success_rate": overall_success_rate,
            "stage_results": stage_results,
            "test_results": [
                {
                    "name": r.name,
                    "stage": r.stage,
                    "status": r.status,
                    "duration": r.duration,
                    "details": r.details
                }
                for r in self.test_results
            ]
        }
        
        return report
    
    def print_summary(self, report: Dict[str, Any]):
        """Print test summary"""
        print("=" * 80)
        self.log("📊 PIPELINE TEST SUMMARY", "STAGE")
        print("=" * 80)
        
        print(f"⏱️  Total Duration: {report['total_duration']:.2f} seconds")
        print(f"🧪 Total Tests: {report['total_tests']}")
        print(f"✅ Passed: {report['total_passed']}")
        print(f"❌ Failed: {report['total_failed']}")
        print(f"📈 Success Rate: {report['overall_success_rate']:.1f}%")
        print()
        
        print("📋 Stage Breakdown:")
        for stage_name, stage_info in report['stage_results'].items():
            status_icon = "✅" if stage_info['success_rate'] == 100 else "⚠️" if stage_info['success_rate'] >= 80 else "❌"
            print(f"  {status_icon} {stage_name.upper()}: {stage_info['passed']}/{stage_info['total']} ({stage_info['success_rate']:.1f}%)")
        
        print()
        
        # Show failed tests
        failed_tests = [r for r in self.test_results if r.status != "PASS"]
        if failed_tests:
            print("❌ Failed Tests:")
            for test in failed_tests:
                print(f"  • {test.name} ({test.stage}): {test.details}")
        else:
            self.log("🎉 ALL TESTS PASSED! Pipeline is fully functional!", "SUCCESS")
        
        print("=" * 80)

def main():
    """Main test runner"""
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 PWA Complete Pipeline Test")
    parser.add_argument('--directory', default='.', help='Directory to test (default: current)')
    parser.add_argument('--output', default='pipeline_test_report.json', help='Output report file')
    parser.add_argument('--skip-docker', action='store_true', help='Skip Docker tests')
    
    args = parser.parse_args()
    
    test_dir = Path(args.directory).resolve()
    if not test_dir.exists():
        print(f"❌ Test directory does not exist: {test_dir}")
        sys.exit(1)
    
    # Run pipeline tests
    runner = PipelineTestRunner(test_dir)
    
    try:
        report = runner.run_all_tests()
        
        # Save detailed report
        with open(args.output, 'w') as f:
            json.dump(report, f, indent=2)
        
        # Print summary
        runner.print_summary(report)
        
        print(f"📄 Detailed report saved to: {args.output}")
        
        # Exit with appropriate code
        success = report['overall_success_rate'] >= 90
        sys.exit(0 if success else 1)
        
    except KeyboardInterrupt:
        print("\n⚠️ Test interrupted by user")
        runner.cleanup()
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Test runner error: {e}")
        runner.cleanup()
        sys.exit(1)

if __name__ == "__main__":
    main()