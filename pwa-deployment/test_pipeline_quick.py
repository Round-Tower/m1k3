#!/usr/bin/env python3
"""
M1K3 PWA Quick Pipeline Test
Fast validation of core pipeline components without Docker
"""

import subprocess
import requests
import json
import time
import sys
from pathlib import Path

def test_core_pipeline():
    """Test core pipeline without Docker dependencies"""
    base_dir = Path.cwd()
    results = {"tests": [], "summary": {}}
    
    print("🚀 M1K3 PWA Quick Pipeline Test")
    print("=" * 50)
    
    # Test 1: File Structure
    print("🔍 Testing file structure...")
    required_files = [
        "README.md", "DEPLOYMENT.md", "frontend/index.html", 
        "frontend/manifest.json", "frontend/sw.js", "backend/requirements.txt"
    ]
    
    missing = [f for f in required_files if not (base_dir / f).exists()]
    if missing:
        print(f"❌ Missing files: {missing}")
        results["tests"].append({"name": "File Structure", "status": "FAIL"})
    else:
        print("✅ All required files present")
        results["tests"].append({"name": "File Structure", "status": "PASS"})
    
    # Test 2: PWA Manifest
    print("🔍 Testing PWA manifest...")
    try:
        with open(base_dir / "frontend/manifest.json") as f:
            manifest = json.load(f)
        
        required_fields = ["name", "icons", "start_url"]
        if all(field in manifest for field in required_fields):
            print("✅ PWA manifest valid")
            results["tests"].append({"name": "PWA Manifest", "status": "PASS"})
        else:
            print("❌ PWA manifest missing required fields")
            results["tests"].append({"name": "PWA Manifest", "status": "FAIL"})
    except Exception as e:
        print(f"❌ PWA manifest error: {e}")
        results["tests"].append({"name": "PWA Manifest", "status": "ERROR"})
    
    # Test 3: Start Test Server
    print("🔍 Testing server startup...")
    server_process = None
    try:
        server_process = subprocess.Popen(
            ["python", "test_server.py", "--port", "9093", "--no-browser"],
            cwd=base_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        
        # Wait for server
        time.sleep(3)
        
        response = requests.get("http://localhost:9093", timeout=5)
        if response.status_code == 200:
            print("✅ Test server running")
            results["tests"].append({"name": "Test Server", "status": "PASS"})
            
            # Test API endpoint
            api_response = requests.get("http://localhost:9093/api/models", timeout=5)
            if api_response.status_code == 200:
                print("✅ API endpoints working")
                results["tests"].append({"name": "API Endpoints", "status": "PASS"})
            else:
                print("❌ API endpoints failed")
                results["tests"].append({"name": "API Endpoints", "status": "FAIL"})
                
        else:
            print("❌ Test server not responding")
            results["tests"].append({"name": "Test Server", "status": "FAIL"})
            
    except Exception as e:
        print(f"❌ Server test error: {e}")
        results["tests"].append({"name": "Test Server", "status": "ERROR"})
    finally:
        if server_process:
            server_process.terminate()
    
    # Test 4: JavaScript Structure
    print("🔍 Testing JavaScript structure...")
    try:
        js_files = ["src/app.js", "src/device-detector.js", "src/model-loader.js"]
        js_valid = True
        
        for js_file in js_files:
            file_path = base_dir / "frontend" / js_file
            if not file_path.exists():
                js_valid = False
                break
            
            with open(file_path) as f:
                content = f.read()
                # Basic validation - check for class definitions
                if "class " not in content and "function " not in content:
                    js_valid = False
                    break
        
        if js_valid:
            print("✅ JavaScript structure valid")
            results["tests"].append({"name": "JavaScript Structure", "status": "PASS"})
        else:
            print("❌ JavaScript structure issues")
            results["tests"].append({"name": "JavaScript Structure", "status": "FAIL"})
            
    except Exception as e:
        print(f"❌ JavaScript test error: {e}")
        results["tests"].append({"name": "JavaScript Structure", "status": "ERROR"})
    
    # Calculate summary
    total_tests = len(results["tests"])
    passed_tests = len([t for t in results["tests"] if t["status"] == "PASS"])
    success_rate = (passed_tests / total_tests) * 100 if total_tests > 0 else 0
    
    results["summary"] = {
        "total": total_tests,
        "passed": passed_tests,
        "failed": total_tests - passed_tests,
        "success_rate": success_rate
    }
    
    # Print summary
    print("=" * 50)
    print("📊 QUICK TEST SUMMARY")
    print("=" * 50)
    print(f"Total Tests: {total_tests}")
    print(f"Passed: {passed_tests}")
    print(f"Failed: {total_tests - passed_tests}")
    print(f"Success Rate: {success_rate:.1f}%")
    
    if success_rate >= 80:
        print("\n🎉 Core pipeline is functional!")
        print("💡 Ready for development and testing")
    else:
        print("\n⚠️ Some core components need attention")
        print("🔧 Check failed tests above")
    
    print("\n🚀 Next Steps:")
    print("  • Run full integration tests: python test_pwa_integration.py")
    print("  • Test Docker build: docker-compose up --build")
    print("  • Start development: python test_server.py")
    
    return success_rate >= 80

if __name__ == "__main__":
    success = test_core_pipeline()
    sys.exit(0 if success else 1)