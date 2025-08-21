#!/usr/bin/env python3
"""
M1K3 Comprehensive Test Suite Runner
Executes all tests and generates detailed reports
"""

import os
import sys
import time
import subprocess
import json
from pathlib import Path
from typing import Dict, List, Any, Tuple
from datetime import datetime

class TestSuiteRunner:
    def __init__(self):
        self.project_root = Path(__file__).parent
        self.tests_dir = self.project_root / "tests"
        self.demos_dir = self.project_root / "demos"
        self.results = {
            "summary": {},
            "tests": [],
            "demos": [],
            "system_info": {},
            "timestamp": datetime.now().isoformat()
        }
        
    def get_system_info(self) -> Dict[str, Any]:
        """Collect system information for the report"""
        import platform
        try:
            import psutil
            cpu_count = psutil.cpu_count()
            memory = psutil.virtual_memory().total // (1024**3)  # GB
        except ImportError:
            cpu_count = os.cpu_count()
            memory = "Unknown"
            
        return {
            "platform": platform.system(),
            "architecture": platform.machine(),
            "python_version": platform.python_version(),
            "cpu_cores": cpu_count,
            "memory_gb": memory,
            "working_directory": str(self.project_root)
        }
    
    def discover_test_files(self) -> Tuple[List[Path], List[Path]]:
        """Discover all test and demo files"""
        test_files = []
        demo_files = []
        
        # Find test files
        if self.tests_dir.exists():
            for test_file in self.tests_dir.glob("test_*.py"):
                if test_file.is_file():
                    test_files.append(test_file)
        
        # Find demo files  
        if self.demos_dir.exists():
            for demo_file in self.demos_dir.glob("demo_*.py"):
                if demo_file.is_file():
                    demo_files.append(demo_file)
        
        return sorted(test_files), sorted(demo_files)
    
    def run_test_file(self, test_path: Path, category: str = "test") -> Dict[str, Any]:
        """Run a single test/demo file and collect results"""
        print(f"🔧 Running {category}: {test_path.name}")
        
        start_time = time.time()
        result = {
            "name": test_path.name,
            "path": str(test_path.relative_to(self.project_root)),
            "category": category,
            "start_time": start_time,
            "status": "unknown",
            "duration": 0,
            "output": "",
            "error": "",
            "exit_code": -1
        }
        
        try:
            # Run the test/demo file
            process = subprocess.run(
                [sys.executable, str(test_path)],
                capture_output=True,
                text=True,
                timeout=300,  # 5 minute timeout
                cwd=self.project_root
            )
            
            result["exit_code"] = process.returncode
            result["output"] = process.stdout
            result["error"] = process.stderr
            result["duration"] = time.time() - start_time
            
            # Determine status
            if process.returncode == 0:
                result["status"] = "passed"
                print(f"   ✅ {test_path.name} - PASSED ({result['duration']:.1f}s)")
            else:
                result["status"] = "failed"
                print(f"   ❌ {test_path.name} - FAILED ({result['duration']:.1f}s)")
                
        except subprocess.TimeoutExpired:
            result["status"] = "timeout"
            result["error"] = "Test timed out after 5 minutes"
            result["duration"] = time.time() - start_time
            print(f"   ⏰ {test_path.name} - TIMEOUT ({result['duration']:.1f}s)")
            
        except Exception as e:
            result["status"] = "error"
            result["error"] = str(e)
            result["duration"] = time.time() - start_time
            print(f"   💥 {test_path.name} - ERROR: {e}")
        
        return result
    
    def analyze_results(self):
        """Analyze test results and generate summary"""
        all_results = self.results["tests"] + self.results["demos"]
        
        total_tests = len(all_results)
        passed = len([r for r in all_results if r["status"] == "passed"])
        failed = len([r for r in all_results if r["status"] == "failed"]) 
        errors = len([r for r in all_results if r["status"] == "error"])
        timeouts = len([r for r in all_results if r["status"] == "timeout"])
        
        total_duration = sum(r["duration"] for r in all_results)
        
        self.results["summary"] = {
            "total_tests": total_tests,
            "passed": passed,
            "failed": failed,
            "errors": errors,
            "timeouts": timeouts,
            "pass_rate": (passed / total_tests * 100) if total_tests > 0 else 0,
            "total_duration": total_duration,
            "average_duration": total_duration / total_tests if total_tests > 0 else 0
        }
    
    def generate_html_report(self) -> str:
        """Generate a comprehensive HTML test report"""
        html_template = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>M1K3 Test Suite Report</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; }
        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 10px; margin-bottom: 20px; }
        .header h1 { margin: 0; font-size: 2.5em; }
        .header .subtitle { opacity: 0.9; margin-top: 10px; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 30px; }
        .metric-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); text-align: center; }
        .metric-value { font-size: 2.5em; font-weight: bold; margin-bottom: 5px; }
        .metric-label { color: #666; font-size: 0.9em; }
        .passed .metric-value { color: #27ae60; }
        .failed .metric-value { color: #e74c3c; }
        .neutral .metric-value { color: #3498db; }
        .section { background: white; margin-bottom: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .section-header { padding: 20px; border-bottom: 1px solid #eee; font-size: 1.3em; font-weight: bold; }
        .test-item { padding: 15px 20px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: between; align-items: center; }
        .test-item:last-child { border-bottom: none; }
        .test-name { font-weight: 500; flex-grow: 1; }
        .test-status { padding: 4px 8px; border-radius: 4px; font-size: 0.85em; font-weight: bold; }
        .status-passed { background: #d5edda; color: #155724; }
        .status-failed { background: #f8d7da; color: #721c24; }
        .status-error { background: #fff3cd; color: #856404; }
        .status-timeout { background: #d1ecf1; color: #0c5460; }
        .test-duration { color: #666; font-size: 0.9em; margin-left: 10px; }
        .system-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 10px; padding: 20px; }
        .system-item { display: flex; justify-content: between; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
        .system-label { font-weight: 500; }
        .system-value { color: #666; }
        .details { margin-top: 10px; padding: 10px; background: #f8f9fa; border-radius: 4px; font-family: monospace; font-size: 0.8em; max-height: 200px; overflow-y: auto; }
        .collapsible { cursor: pointer; user-select: none; }
        .collapsible:hover { background: #f8f9fa; }
        .hidden { display: none; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🤖 M1K3 Test Suite Report</h1>
            <div class="subtitle">Generated on {timestamp} | Total Runtime: {total_duration:.1f}s</div>
        </div>
        
        <div class="summary">
            <div class="metric-card neutral">
                <div class="metric-value">{total_tests}</div>
                <div class="metric-label">Total Tests</div>
            </div>
            <div class="metric-card passed">
                <div class="metric-value">{passed}</div>
                <div class="metric-label">Passed</div>
            </div>
            <div class="metric-card failed">
                <div class="metric-value">{failed}</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric-card neutral">
                <div class="metric-value">{pass_rate:.1f}%</div>
                <div class="metric-label">Pass Rate</div>
            </div>
        </div>
        
        {test_sections}
        
        <div class="section">
            <div class="section-header">🖥️ System Information</div>
            <div class="system-info">
                {system_info}
            </div>
        </div>
    </div>
    
    <script>
        document.querySelectorAll('.collapsible').forEach(item => {
            item.addEventListener('click', () => {
                const details = item.nextElementSibling;
                if (details) {
                    details.classList.toggle('hidden');
                }
            });
        });
    </script>
</body>
</html>
"""
        
        # Generate test sections
        test_sections = []
        
        for category, items in [("Tests", self.results["tests"]), ("Demos", self.results["demos"])]:
            if not items:
                continue
                
            section_html = f'<div class="section"><div class="section-header">🧪 {category} ({len(items)} items)</div>'
            
            for item in items:
                status_class = f"status-{item['status']}"
                details_id = f"details-{item['name'].replace('.', '-')}"
                
                section_html += f"""
                <div class="test-item collapsible">
                    <div class="test-name">{item['name']}</div>
                    <div class="test-duration">{item['duration']:.1f}s</div>
                    <div class="test-status {status_class}">{item['status'].upper()}</div>
                </div>
                <div class="details hidden">
                    <strong>Path:</strong> {item['path']}<br>
                    <strong>Exit Code:</strong> {item['exit_code']}<br>
                    {f'<strong>Output:</strong><pre>{item["output"][:1000]}{"..." if len(item["output"]) > 1000 else ""}</pre>' if item['output'] else ''}
                    {f'<strong>Error:</strong><pre>{item["error"][:1000]}{"..." if len(item["error"]) > 1000 else ""}</pre>' if item['error'] else ''}
                </div>
                """
            
            section_html += '</div>'
            test_sections.append(section_html)
        
        # Generate system info
        system_info_html = ""
        for key, value in self.results["system_info"].items():
            system_info_html += f"""
            <div class="system-item">
                <div class="system-label">{key.replace('_', ' ').title()}:</div>
                <div class="system-value">{value}</div>
            </div>
            """
        
        return html_template.format(
            timestamp=self.results["timestamp"],
            total_duration=self.results["summary"]["total_duration"],
            total_tests=self.results["summary"]["total_tests"],
            passed=self.results["summary"]["passed"],
            failed=self.results["summary"]["failed"],
            pass_rate=self.results["summary"]["pass_rate"],
            test_sections="".join(test_sections),
            system_info=system_info_html
        )
    
    def run_full_suite(self) -> Dict[str, Any]:
        """Run the complete test suite"""
        print("🚀 M1K3 Comprehensive Test Suite")
        print("="*80)
        
        # Collect system information
        self.results["system_info"] = self.get_system_info()
        print(f"🖥️  System: {self.results['system_info']['platform']} {self.results['system_info']['architecture']}")
        print(f"🐍 Python: {self.results['system_info']['python_version']}")
        
        # Discover test files
        test_files, demo_files = self.discover_test_files()
        print(f"📊 Found {len(test_files)} tests and {len(demo_files)} demos")
        print("")
        
        # Run tests
        if test_files:
            print("🧪 RUNNING TESTS:")
            print("-" * 40)
            for test_file in test_files:
                result = self.run_test_file(test_file, "test")
                self.results["tests"].append(result)
        
        print("")
        
        # Run demos (as integration tests)
        if demo_files:
            print("🎮 RUNNING DEMOS:")
            print("-" * 40)
            for demo_file in demo_files:
                result = self.run_test_file(demo_file, "demo")
                self.results["demos"].append(result)
        
        # Analyze results
        self.analyze_results()
        
        # Print summary
        print("")
        print("📊 TEST SUITE SUMMARY:")
        print("="*80)
        summary = self.results["summary"]
        print(f"Total Tests: {summary['total_tests']}")
        print(f"Passed: {summary['passed']} ✅")
        print(f"Failed: {summary['failed']} ❌") 
        print(f"Errors: {summary['errors']} 💥")
        print(f"Timeouts: {summary['timeouts']} ⏰")
        print(f"Pass Rate: {summary['pass_rate']:.1f}%")
        print(f"Total Duration: {summary['total_duration']:.1f}s")
        print(f"Average Duration: {summary['average_duration']:.1f}s")
        
        return self.results

def main():
    """Main test suite runner"""
    runner = TestSuiteRunner()
    results = runner.run_full_suite()
    
    # Generate reports
    print("\n📋 GENERATING REPORTS:")
    print("-" * 40)
    
    # JSON report
    json_report_path = runner.project_root / "test_report.json"
    with open(json_report_path, "w") as f:
        json.dump(results, f, indent=2, default=str)
    print(f"✅ JSON Report: {json_report_path}")
    
    # HTML report
    html_report_path = runner.project_root / "test_report.html"
    html_content = runner.generate_html_report()
    with open(html_report_path, "w") as f:
        f.write(html_content)
    print(f"✅ HTML Report: {html_report_path}")
    
    # Final status
    summary = results["summary"]
    if summary["failed"] == 0 and summary["errors"] == 0:
        print("\n🎉 ALL TESTS PASSED!")
        return 0
    else:
        print(f"\n⚠️  {summary['failed'] + summary['errors']} TESTS FAILED")
        return 1

if __name__ == "__main__":
    sys.exit(main())