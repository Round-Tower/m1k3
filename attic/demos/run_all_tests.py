#!/usr/bin/env python3
"""
M1K3 Test Runner with Comprehensive Reporting
Runs all existing tests and generates detailed reports
"""

import sys
import os
import subprocess
import json
import time
import html
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from datetime import datetime

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

class M1K3TestRunner:
    """Comprehensive test runner for all M1K3 tests"""
    
    def __init__(self):
        self.repo_root = Path(__file__).parent.parent
        self.reports_dir = Path(__file__).parent / "reports"
        self.reports_dir.mkdir(exist_ok=True)
        
        self.test_results = {}
        self.total_start_time = time.time()
        
        print("🧪 M1K3 COMPREHENSIVE TEST RUNNER")
        print("=" * 60)
        print(f"Repository: {self.repo_root}")
        print(f"Reports: {self.reports_dir}")
        print()
    
    def run_all_tests(self):
        """Execute all tests and generate reports"""
        
        # Discover all test files
        test_files = self._discover_test_files()
        
        print(f"🔍 Discovered {len(test_files)} test files")
        print(f"📊 Running comprehensive test suite...")
        print()
        
        # Run each test file
        for test_file in test_files:
            self._run_test_file(test_file)
        
        # Generate reports
        self._generate_comprehensive_report()
        self._generate_html_report()
        self._generate_refactoring_checklist()
        
        # Show summary
        self._show_final_summary()
    
    def _discover_test_files(self) -> List[Path]:
        """Discover all test files in the repository"""
        test_files = []
        
        # Main test files (test_*.py)
        for test_file in self.repo_root.glob("test_*.py"):
            if test_file.name not in ['test_performance_optimization.py']:  # Skip our new ones for now
                test_files.append(test_file)
        
        # Tests directory
        tests_dir = self.repo_root / "tests"
        if tests_dir.exists():
            for test_file in tests_dir.glob("**/*.py"):
                if test_file.name.startswith("test_") or test_file.name.startswith("demo_"):
                    test_files.append(test_file)
        
        # Demo tests in demos directory
        demos_dir = self.repo_root / "demos"
        if demos_dir.exists():
            for test_file in demos_dir.glob("test_*.py"):
                test_files.append(test_file)
        
        # Sort by name for consistent ordering
        test_files.sort(key=lambda p: p.name)
        
        return test_files
    
    def _run_test_file(self, test_file: Path):
        """Run a single test file and capture results"""
        print(f"🧪 Running: {test_file.name}")
        
        start_time = time.time()
        
        try:
            # Run the test file
            result = subprocess.run([
                sys.executable, str(test_file)
            ], 
            capture_output=True, 
            text=True,
            timeout=300,  # 5 minute timeout per test
            cwd=self.repo_root
            )
            
            duration = time.time() - start_time
            
            # Parse results
            success = result.returncode == 0
            stdout = result.stdout
            stderr = result.stderr
            
            # Extract test metrics if available
            metrics = self._extract_test_metrics(stdout, stderr)
            
            self.test_results[test_file.name] = {
                'file_path': str(test_file),
                'success': success,
                'duration': duration,
                'return_code': result.returncode,
                'stdout': stdout,
                'stderr': stderr,
                'metrics': metrics,
                'timestamp': time.time()
            }
            
            status = "✅ PASS" if success else "❌ FAIL"
            print(f"  {status} ({duration:.1f}s) {metrics.get('summary', '')}")
            
            if not success and stderr:
                # Show first few lines of error
                error_lines = stderr.split('\n')[:3]
                for line in error_lines:
                    if line.strip():
                        print(f"    💥 {line.strip()}")
            
        except subprocess.TimeoutExpired:
            duration = time.time() - start_time
            self.test_results[test_file.name] = {
                'file_path': str(test_file),
                'success': False,
                'duration': duration,
                'return_code': -1,
                'stdout': "",
                'stderr': "Test timed out after 300 seconds",
                'metrics': {'error': 'timeout'},
                'timestamp': time.time()
            }
            print(f"  ⏱️ TIMEOUT ({duration:.1f}s)")
            
        except Exception as e:
            duration = time.time() - start_time
            self.test_results[test_file.name] = {
                'file_path': str(test_file),
                'success': False,
                'duration': duration,
                'return_code': -1,
                'stdout': "",
                'stderr': str(e),
                'metrics': {'error': str(e)},
                'timestamp': time.time()
            }
            print(f"  💥 ERROR ({duration:.1f}s): {e}")
    
    def _extract_test_metrics(self, stdout: str, stderr: str) -> Dict:
        """Extract metrics from test output"""
        metrics = {}
        
        # Look for common patterns in test output
        combined_output = stdout + stderr
        lines = combined_output.split('\n')
        
        for line in lines:
            line = line.strip()
            
            # Success/failure patterns
            if 'passed' in line.lower() and 'failed' in line.lower():
                metrics['summary'] = line
            
            # Performance patterns
            if 'time:' in line.lower() or 'duration:' in line.lower():
                metrics['performance'] = line
            
            # Memory patterns
            if 'memory' in line.lower() and ('mb' in line.lower() or 'gb' in line.lower()):
                metrics['memory'] = line
            
            # Component patterns
            if 'available' in line.lower() or 'not available' in line.lower():
                if 'component' not in metrics:
                    metrics['components'] = []
                metrics['components'].append(line)
            
            # Error patterns
            if 'error:' in line.lower() or 'failed:' in line.lower():
                if 'errors' not in metrics:
                    metrics['errors'] = []
                metrics['errors'].append(line)
        
        return metrics
    
    def _generate_comprehensive_report(self):
        """Generate detailed JSON report"""
        total_duration = time.time() - self.total_start_time
        
        # Calculate summary statistics
        total_tests = len(self.test_results)
        passed_tests = sum(1 for r in self.test_results.values() if r['success'])
        failed_tests = total_tests - passed_tests
        success_rate = (passed_tests / total_tests * 100) if total_tests > 0 else 0
        
        # Categorize tests
        categories = {
            'ai_inference': [],
            'voice_synthesis': [],
            'speech_recognition': [],
            'avatar_system': [],
            'rag_system': [],
            'performance': [],
            'integration': [],
            'other': []
        }
        
        for test_name, result in self.test_results.items():
            # Categorize based on filename
            name_lower = test_name.lower()
            if any(word in name_lower for word in ['ai', 'inference', 'model', 'llm']):
                categories['ai_inference'].append((test_name, result))
            elif any(word in name_lower for word in ['voice', 'tts', 'speech']):
                if 'stt' in name_lower or 'recognition' in name_lower:
                    categories['speech_recognition'].append((test_name, result))
                else:
                    categories['voice_synthesis'].append((test_name, result))
            elif any(word in name_lower for word in ['avatar', 'emotion']):
                categories['avatar_system'].append((test_name, result))
            elif any(word in name_lower for word in ['rag', 'knowledge']):
                categories['rag_system'].append((test_name, result))
            elif any(word in name_lower for word in ['performance', 'speed', 'benchmark']):
                categories['performance'].append((test_name, result))
            elif any(word in name_lower for word in ['integration', 'full', 'comprehensive']):
                categories['integration'].append((test_name, result))
            else:
                categories['other'].append((test_name, result))
        
        # Generate comprehensive report
        report = {
            'timestamp': datetime.now().isoformat(),
            'total_duration': total_duration,
            'summary': {
                'total_tests': total_tests,
                'passed_tests': passed_tests,
                'failed_tests': failed_tests,
                'success_rate': success_rate
            },
            'categories': {},
            'detailed_results': self.test_results,
            'system_info': self._get_system_info(),
            'recommendations': self._generate_recommendations()
        }
        
        # Add category summaries
        for category, tests in categories.items():
            if tests:
                cat_passed = sum(1 for _, result in tests if result['success'])
                cat_total = len(tests)
                cat_rate = (cat_passed / cat_total * 100) if cat_total > 0 else 0
                
                report['categories'][category] = {
                    'total': cat_total,
                    'passed': cat_passed,
                    'success_rate': cat_rate,
                    'tests': [name for name, _ in tests]
                }
        
        # Save report
        report_file = self.reports_dir / "comprehensive_test_report.json"
        with open(report_file, 'w') as f:
            json.dump(report, f, indent=2, default=str)
        
        print(f"📄 Comprehensive report saved: {report_file}")
        return report
    
    def _generate_html_report(self):
        """Generate HTML visualization report"""
        report_data = json.load(open(self.reports_dir / "comprehensive_test_report.json"))
        
        html_content = f"""
<!DOCTYPE html>
<html>
<head>
    <title>M1K3 Test Report - {datetime.now().strftime('%Y-%m-%d %H:%M')}</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; background: #f5f5f5; }}
        .container {{ max-width: 1200px; margin: 0 auto; background: white; padding: 40px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }}
        h1 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }}
        h2 {{ color: #34495e; margin-top: 30px; }}
        .summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0; }}
        .metric {{ background: #ecf0f1; padding: 20px; border-radius: 8px; text-align: center; }}
        .metric-value {{ font-size: 2em; font-weight: bold; color: #2980b9; }}
        .metric-label {{ color: #7f8c8d; font-weight: 500; }}
        .success {{ color: #27ae60; }}
        .failure {{ color: #e74c3c; }}
        .category {{ margin: 20px 0; padding: 20px; background: #f8f9fa; border-left: 4px solid #3498db; }}
        .test-item {{ margin: 10px 0; padding: 10px; background: white; border-radius: 5px; }}
        .test-passed {{ border-left: 4px solid #27ae60; }}
        .test-failed {{ border-left: 4px solid #e74c3c; }}
        .progress-bar {{ width: 100%; height: 20px; background: #ecf0f1; border-radius: 10px; overflow: hidden; }}
        .progress-fill {{ height: 100%; background: linear-gradient(90deg, #27ae60, #2ecc71); transition: width 0.3s; }}
        .recommendation {{ background: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; border-radius: 5px; margin: 10px 0; }}
        pre {{ background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 5px; overflow-x: auto; }}
        .timestamp {{ color: #7f8c8d; font-size: 0.9em; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🧪 M1K3 Test Report</h1>
        <div class="timestamp">Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</div>
        
        <div class="summary">
            <div class="metric">
                <div class="metric-value">{report_data['summary']['total_tests']}</div>
                <div class="metric-label">Total Tests</div>
            </div>
            <div class="metric">
                <div class="metric-value success">{report_data['summary']['passed_tests']}</div>
                <div class="metric-label">Passed</div>
            </div>
            <div class="metric">
                <div class="metric-value failure">{report_data['summary']['failed_tests']}</div>
                <div class="metric-label">Failed</div>
            </div>
            <div class="metric">
                <div class="metric-value">{report_data['summary']['success_rate']:.1f}%</div>
                <div class="metric-label">Success Rate</div>
            </div>
            <div class="metric">
                <div class="metric-value">{report_data['total_duration']:.1f}s</div>
                <div class="metric-label">Total Time</div>
            </div>
        </div>
        
        <div class="progress-bar">
            <div class="progress-fill" style="width: {report_data['summary']['success_rate']:.1f}%"></div>
        </div>
        
        <h2>📊 Test Categories</h2>
"""
        
        # Add category sections
        for category, data in report_data['categories'].items():
            category_title = category.replace('_', ' ').title()
            html_content += f"""
        <div class="category">
            <h3>{category_title}</h3>
            <div class="progress-bar">
                <div class="progress-fill" style="width: {data['success_rate']:.1f}%"></div>
            </div>
            <div style="margin-top: 10px;">
                <strong>{data['passed']}/{data['total']} passed ({data['success_rate']:.1f}%)</strong>
            </div>
            <div style="margin-top: 15px;">
"""
            
            # Add individual tests
            for test_name in data['tests']:
                result = report_data['detailed_results'][test_name]
                status_class = "test-passed" if result['success'] else "test-failed"
                status_icon = "✅" if result['success'] else "❌"
                
                html_content += f"""
                <div class="test-item {status_class}">
                    <strong>{status_icon} {test_name}</strong>
                    <div style="color: #7f8c8d; font-size: 0.9em;">
                        Duration: {result['duration']:.1f}s
"""
                
                if result.get('metrics', {}).get('summary'):
                    html_content += f" | {html.escape(result['metrics']['summary'])}"
                
                html_content += """
                    </div>
                </div>
"""
            
            html_content += """
            </div>
        </div>
"""
        
        # Add recommendations
        html_content += """
        <h2>🔧 Recommendations</h2>
"""
        
        for rec in report_data['recommendations']:
            html_content += f"""
        <div class="recommendation">
            <strong>{html.escape(rec['priority'])}</strong>: {html.escape(rec['message'])}
        </div>
"""
        
        # Add system info
        html_content += f"""
        <h2>💻 System Information</h2>
        <pre>{html.escape(json.dumps(report_data['system_info'], indent=2))}</pre>
        
        <div class="timestamp" style="margin-top: 30px; text-align: center;">
            Generated by M1K3 Test Runner | {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
        </div>
    </div>
</body>
</html>
"""
        
        # Save HTML report
        html_file = self.reports_dir / "test_report.html"
        with open(html_file, 'w') as f:
            f.write(html_content)
        
        print(f"🌐 HTML report saved: {html_file}")
    
    def _generate_refactoring_checklist(self):
        """Generate refactoring checklist based on test results"""
        
        # Analyze test results for refactoring insights
        high_risk_areas = []
        safe_areas = []
        needs_attention = []
        
        for test_name, result in self.test_results.items():
            if not result['success']:
                # Categorize failures
                if any(word in test_name.lower() for word in ['critical', 'core', 'main', 'integration']):
                    high_risk_areas.append({
                        'test': test_name,
                        'reason': 'Critical system failure',
                        'error': result.get('stderr', '')[:200] + '...' if result.get('stderr') else 'Unknown error'
                    })
                else:
                    needs_attention.append({
                        'test': test_name,
                        'reason': 'Component failure',
                        'error': result.get('stderr', '')[:200] + '...' if result.get('stderr') else 'Unknown error'
                    })
            else:
                safe_areas.append(test_name)
        
        # Generate checklist
        checklist = f"""# M1K3 Refactoring Checklist
Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## 📊 Test Results Summary
- Total Tests: {len(self.test_results)}
- Passed: {sum(1 for r in self.test_results.values() if r['success'])}
- Failed: {sum(1 for r in self.test_results.values() if not r['success'])}
- Success Rate: {(sum(1 for r in self.test_results.values() if r['success']) / len(self.test_results) * 100):.1f}%

## 🚨 High Risk Areas (Fix Before Refactoring)
"""
        
        if high_risk_areas:
            for item in high_risk_areas:
                checklist += f"""
### ❌ {item['test']}
- **Risk Level**: HIGH
- **Reason**: {item['reason']}
- **Error**: {item['error']}
- **Action**: Fix before any refactoring
"""
        else:
            checklist += "\n✅ No high-risk failures detected\n"
        
        checklist += f"""
## ⚠️ Areas Needing Attention
"""
        
        if needs_attention:
            for item in needs_attention:
                checklist += f"""
### ⚠️ {item['test']}
- **Risk Level**: MEDIUM
- **Reason**: {item['reason']}
- **Error**: {item['error']}
- **Action**: Review and fix if needed
"""
        else:
            checklist += "\n✅ No medium-risk issues detected\n"
        
        checklist += f"""
## ✅ Safe for Refactoring ({len(safe_areas)} components)
These components have passing tests and are safe to refactor:
"""
        
        for area in safe_areas[:10]:  # Show first 10
            checklist += f"- {area}\n"
        
        if len(safe_areas) > 10:
            checklist += f"- ... and {len(safe_areas) - 10} more\n"
        
        # Add refactoring recommendations
        checklist += f"""
## 🔧 Refactoring Recommendations

### Priority 1: Critical Systems
{f"- Fix {len(high_risk_areas)} high-risk failures first" if high_risk_areas else "- ✅ All critical systems passing"}

### Priority 2: Component Stability
{f"- Address {len(needs_attention)} component issues" if needs_attention else "- ✅ All components stable"}

### Priority 3: Safe Refactoring
- {len(safe_areas)} components ready for refactoring
- Focus on areas with good test coverage
- Maintain backward compatibility

### Agent Review Recommendations
1. **Start with passing tests** - These indicate stable, well-tested code
2. **Identify patterns** - Look for common failure modes across tests
3. **Prioritize by impact** - Focus on core systems first
4. **Maintain test coverage** - Ensure refactoring doesn't break existing functionality

## 📋 Next Steps
1. Review this checklist with the development team
2. Fix high-risk issues before refactoring
3. Use passing tests as refactoring confidence indicators
4. Re-run test suite after each refactoring phase
5. Monitor for regressions during refactoring

---
Generated by M1K3 Test Runner | Ready for Agent Analysis
"""
        
        # Save checklist
        checklist_file = self.reports_dir / "refactoring_checklist.md"
        with open(checklist_file, 'w') as f:
            f.write(checklist)
        
        print(f"📋 Refactoring checklist saved: {checklist_file}")
    
    def _get_system_info(self) -> Dict:
        """Get system information for the report"""
        import platform
        import sys
        
        return {
            'platform': platform.platform(),
            'python_version': sys.version,
            'architecture': platform.architecture(),
            'processor': platform.processor(),
            'timestamp': datetime.now().isoformat(),
            'repository_path': str(self.repo_root)
        }
    
    def _generate_recommendations(self) -> List[Dict]:
        """Generate recommendations based on test results"""
        recommendations = []
        
        failed_count = sum(1 for r in self.test_results.values() if not r['success'])
        total_count = len(self.test_results)
        success_rate = ((total_count - failed_count) / total_count * 100) if total_count > 0 else 0
        
        if success_rate < 50:
            recommendations.append({
                'priority': 'CRITICAL',
                'message': f'Low success rate ({success_rate:.1f}%). System needs significant fixes before refactoring.'
            })
        elif success_rate < 75:
            recommendations.append({
                'priority': 'HIGH',
                'message': f'Moderate success rate ({success_rate:.1f}%). Address failing tests before major refactoring.'
            })
        else:
            recommendations.append({
                'priority': 'INFO',
                'message': f'Good success rate ({success_rate:.1f}%). System is ready for careful refactoring.'
            })
        
        # Check for timeout issues
        timeouts = sum(1 for r in self.test_results.values() if 'timeout' in r.get('stderr', '').lower())
        if timeouts > 0:
            recommendations.append({
                'priority': 'MEDIUM',
                'message': f'{timeouts} tests timed out. Consider optimizing slow components.'
            })
        
        # Check for import errors
        import_errors = sum(1 for r in self.test_results.values() 
                          if 'import' in r.get('stderr', '').lower() and not r['success'])
        if import_errors > 0:
            recommendations.append({
                'priority': 'HIGH',
                'message': f'{import_errors} tests have import errors. Fix dependencies before refactoring.'
            })
        
        return recommendations
    
    def _show_final_summary(self):
        """Show final test run summary"""
        total_duration = time.time() - self.total_start_time
        total_tests = len(self.test_results)
        passed_tests = sum(1 for r in self.test_results.values() if r['success'])
        failed_tests = total_tests - passed_tests
        success_rate = (passed_tests / total_tests * 100) if total_tests > 0 else 0
        
        print(f"\n🏁 TEST RUNNER COMPLETE")
        print("=" * 60)
        print(f"Total Time: {total_duration:.1f} seconds")
        print(f"Tests Run: {total_tests}")
        print(f"Passed: {passed_tests} ✅")
        print(f"Failed: {failed_tests} ❌")
        print(f"Success Rate: {success_rate:.1f}%")
        
        # Show status
        if success_rate >= 75:
            print(f"\n🎉 EXCELLENT - System ready for refactoring!")
        elif success_rate >= 50:
            print(f"\n⚠️ GOOD - Address failing tests, then refactor")
        else:
            print(f"\n🚨 ATTENTION NEEDED - Fix critical issues first")
        
        # Show report locations
        print(f"\n📊 Generated Reports:")
        print(f"  • JSON Report: {self.reports_dir / 'comprehensive_test_report.json'}")
        print(f"  • HTML Report: {self.reports_dir / 'test_report.html'}")
        print(f"  • Refactoring Checklist: {self.reports_dir / 'refactoring_checklist.md'}")
        
        print(f"\n🤖 Ready for Agent Analysis!")
        print("The comprehensive reports provide everything needed for refactoring.")


def main():
    """Run the comprehensive test suite"""
    try:
        runner = M1K3TestRunner()
        runner.run_all_tests()
        return True
    except KeyboardInterrupt:
        print("\n⚠️ Test runner interrupted by user")
        return False
    except Exception as e:
        print(f"\n💥 Test runner failed: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)