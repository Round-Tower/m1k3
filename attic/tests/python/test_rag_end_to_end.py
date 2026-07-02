#!/usr/bin/env python3
"""
M1K3 RAG End-to-End Real-World Test
Tests the complete user journey from query to AI-enhanced response
"""

import subprocess
import time
import os
import json
import tempfile
from pathlib import Path

def test_real_world_scenarios():
    """Test RAG system with actual user scenarios"""
    
    print("🧪 M1K3 RAG End-to-End Real-World Test")
    print("=" * 60)
    
    # Check prerequisites
    kb_path = 'knowledge/comprehensive_knowledge_base.json'
    if not os.path.exists(kb_path):
        print("❌ Knowledge base not found. Run 'python generate_comprehensive_kb.py' first.")
        return False
    
    test_scenarios = [
        {
            "name": "WiFi Troubleshooting",
            "query": "My WiFi is slow and keeps disconnecting. How can I fix this?",
            "expected_keywords": ["wifi", "network", "router", "connection", "speed", "troubleshoot"],
            "category": "wifi_networking"
        },
        {
            "name": "iPhone Battery Issue", 
            "query": "My iPhone battery drains really fast. What should I do?",
            "expected_keywords": ["battery", "iPhone", "drain", "optimize", "settings", "power"],
            "category": "device_technology"
        },
        {
            "name": "Password Security",
            "query": "How do I create strong passwords and keep them secure?",
            "expected_keywords": ["password", "security", "strong", "secure", "manager", "authentication"],
            "category": "security_privacy"
        },
        {
            "name": "Math Problem",
            "query": "What is 15 multiplied by 23?",
            "expected_keywords": ["15", "23", "multiply", "345", "calculation"],
            "category": "mathematical_calculation"
        },
        {
            "name": "Study Techniques",
            "query": "What are the best study methods for learning programming?",
            "expected_keywords": ["study", "learning", "programming", "practice", "methods", "education"],
            "category": "educational_tutoring"
        }
    ]
    
    successful_tests = 0
    total_tests = len(test_scenarios)
    
    print(f"\n🎯 Testing {total_tests} real-world scenarios:\n")
    
    for i, scenario in enumerate(test_scenarios, 1):
        print(f"📋 Test {i}/{total_tests}: {scenario['name']}")
        print(f"   Query: \"{scenario['query']}\"")
        
        try:
            # Test with RAG-enhanced M1K3
            start_time = time.time()
            
            result = subprocess.run([
                'python', 'm1k3.py',
                '--query', scenario['query'],
                '--rag',
                '--no-voice'
            ], capture_output=True, text=True, timeout=120)
            
            response_time = time.time() - start_time
            
            # Check if command succeeded
            if result.returncode != 0:
                print(f"   ❌ Command failed (code {result.returncode})")
                print(f"      Error: {result.stderr[:200]}")
                continue
            
            # Analyze response
            output = result.stdout + result.stderr
            response_lower = output.lower()
            
            # Check for expected keywords
            keywords_found = sum(1 for keyword in scenario['expected_keywords'] 
                               if keyword.lower() in response_lower)
            keyword_coverage = keywords_found / len(scenario['expected_keywords'])
            
            # Check for RAG indicators
            rag_indicators = ["rag", "context", "knowledge", "retrieved"]
            rag_mentioned = any(indicator in response_lower for indicator in rag_indicators)
            
            # Evaluate response quality
            response_length = len(output)
            has_substantial_response = response_length > 200
            
            # Test results
            success_criteria = [
                ("Response time < 2 minutes", response_time < 120),
                ("Substantial response (>200 chars)", has_substantial_response),
                ("Relevant keywords found", keyword_coverage >= 0.3),
                ("No obvious errors", "error" not in response_lower or "successfully" in response_lower)
            ]
            
            passed_criteria = sum(1 for _, passed in success_criteria if passed)
            test_passed = passed_criteria >= 3  # At least 3/4 criteria
            
            if test_passed:
                print(f"   ✅ PASSED ({passed_criteria}/4 criteria)")
                successful_tests += 1
            else:
                print(f"   ❌ FAILED ({passed_criteria}/4 criteria)")
            
            print(f"      • Response time: {response_time:.1f}s")
            print(f"      • Response length: {response_length} chars")
            print(f"      • Keyword coverage: {keyword_coverage:.1%}")
            print(f"      • RAG system active: {'Yes' if rag_mentioned else 'Unknown'}")
            
            # Show criteria details
            for criterion, passed in success_criteria:
                status = "✅" if passed else "❌"
                print(f"      • {status} {criterion}")
                
        except subprocess.TimeoutExpired:
            print(f"   ❌ TIMEOUT (>2 minutes)")
        except Exception as e:
            print(f"   ❌ ERROR: {e}")
        
        print()  # Blank line between tests
    
    # Final results
    print("=" * 60)
    success_rate = successful_tests / total_tests
    
    if success_rate >= 0.8:
        print(f"🎉 EXCELLENT: {successful_tests}/{total_tests} tests passed ({success_rate:.1%})")
        print("✅ RAG system is performing excellently in real-world scenarios")
    elif success_rate >= 0.6:
        print(f"👍 GOOD: {successful_tests}/{total_tests} tests passed ({success_rate:.1%})")
        print("✅ RAG system is working well with minor issues")
    elif success_rate >= 0.4:
        print(f"⚠️  FAIR: {successful_tests}/{total_tests} tests passed ({success_rate:.1%})")
        print("🔧 RAG system needs some improvements")
    else:
        print(f"❌ POOR: {successful_tests}/{total_tests} tests passed ({success_rate:.1%})")
        print("🚨 RAG system has significant issues")
    
    print(f"\n📊 Test Summary:")
    print(f"   • Total scenarios tested: {total_tests}")
    print(f"   • Successful responses: {successful_tests}")
    print(f"   • Success rate: {success_rate:.1%}")
    print(f"   • Average categories covered: {len(set(s['category'] for s in test_scenarios))}")
    
    return success_rate >= 0.6


def test_rag_vs_no_rag_comparison():
    """Compare responses with and without RAG enhancement"""
    
    print("\n🔄 RAG Enhancement Comparison Test")
    print("=" * 60)
    
    test_query = "How do I optimize my iPhone battery life?"
    
    print(f"Query: \"{test_query}\"")
    print("\n📊 Testing both modes:")
    
    results = {}
    
    # Test without RAG
    print("   🔹 Testing WITHOUT RAG...")
    try:
        result_no_rag = subprocess.run([
            'python', 'm1k3.py',
            '--query', test_query,
            '--no-voice'
        ], capture_output=True, text=True, timeout=90)
        
        if result_no_rag.returncode == 0:
            output_no_rag = result_no_rag.stdout + result_no_rag.stderr
            results['no_rag'] = {
                'length': len(output_no_rag),
                'content': output_no_rag,
                'success': True
            }
            print(f"      ✅ Response length: {len(output_no_rag)} chars")
        else:
            results['no_rag'] = {'success': False, 'error': result_no_rag.stderr}
            print(f"      ❌ Failed: {result_no_rag.stderr[:100]}")
            
    except subprocess.TimeoutExpired:
        results['no_rag'] = {'success': False, 'error': 'Timeout'}
        print("      ❌ Timeout")
    except Exception as e:
        results['no_rag'] = {'success': False, 'error': str(e)}
        print(f"      ❌ Error: {e}")
    
    # Test with RAG
    print("   🔹 Testing WITH RAG...")
    try:
        result_rag = subprocess.run([
            'python', 'm1k3.py',
            '--query', test_query,
            '--rag',
            '--no-voice'
        ], capture_output=True, text=True, timeout=90)
        
        if result_rag.returncode == 0:
            output_rag = result_rag.stdout + result_rag.stderr
            results['rag'] = {
                'length': len(output_rag),
                'content': output_rag,
                'success': True
            }
            print(f"      ✅ Response length: {len(output_rag)} chars")
        else:
            results['rag'] = {'success': False, 'error': result_rag.stderr}
            print(f"      ❌ Failed: {result_rag.stderr[:100]}")
            
    except subprocess.TimeoutExpired:
        results['rag'] = {'success': False, 'error': 'Timeout'}
        print("      ❌ Timeout")
    except Exception as e:
        results['rag'] = {'success': False, 'error': str(e)}
        print(f"      ❌ Error: {e}")
    
    # Compare results
    print("\n📈 Comparison Results:")
    
    if results.get('no_rag', {}).get('success') and results.get('rag', {}).get('success'):
        no_rag_len = results['no_rag']['length']
        rag_len = results['rag']['length']
        
        # Check for RAG-specific content
        rag_content = results['rag']['content'].lower()
        rag_indicators = sum(1 for indicator in ['context', 'knowledge', 'retrieved', 'documents'] 
                           if indicator in rag_content)
        
        # Analysis
        print(f"   📏 Response length comparison:")
        print(f"      • Without RAG: {no_rag_len} characters")
        print(f"      • With RAG: {rag_len} characters")
        print(f"      • Difference: {rag_len - no_rag_len:+d} characters")
        
        print(f"   🧠 RAG enhancement indicators: {rag_indicators}/4")
        
        if rag_len > no_rag_len * 1.2 and rag_indicators >= 2:
            print("   ✅ RAG appears to be providing enhanced responses")
            return True
        elif rag_indicators >= 1:
            print("   ⚠️  RAG is active but enhancement unclear")
            return True
        else:
            print("   ❌ RAG enhancement not clearly evident")
            return False
    else:
        print("   ❌ Could not complete comparison (one or both tests failed)")
        return False


def test_web_interface_accessibility():
    """Test that web interfaces are accessible"""
    
    print("\n🌐 Web Interface Accessibility Test")
    print("=" * 40)
    
    web_interfaces = [
        {
            'file': 'rag_knowledge_viewer.html',
            'name': 'Knowledge Viewer',
            'required_elements': ['search', 'filter', 'document', 'category']
        },
        {
            'file': 'rag_admin.html', 
            'name': 'Admin Panel',
            'required_elements': ['generate', 'admin', 'management', 'synthetic']
        }
    ]
    
    all_interfaces_ok = True
    
    for interface in web_interfaces:
        print(f"🔍 Testing {interface['name']}...")
        
        if not os.path.exists(interface['file']):
            print(f"   ❌ File not found: {interface['file']}")
            all_interfaces_ok = False
            continue
        
        try:
            with open(interface['file'], 'r') as f:
                content = f.read().lower()
            
            # Check file size
            file_size = len(content)
            if file_size < 1000:
                print(f"   ⚠️  Very small file ({file_size} chars)")
            
            # Check for required elements
            elements_found = sum(1 for element in interface['required_elements'] 
                               if element in content)
            element_coverage = elements_found / len(interface['required_elements'])
            
            # Check for basic HTML structure
            has_html = '<html' in content and '</html>' in content
            has_head = '<head' in content and '</head>' in content
            has_body = '<body' in content and '</body>' in content
            basic_structure = has_html and has_head and has_body
            
            if basic_structure and element_coverage >= 0.5:
                print(f"   ✅ Interface OK ({elements_found}/{len(interface['required_elements'])} elements)")
            else:
                print(f"   ❌ Interface issues ({elements_found}/{len(interface['required_elements'])} elements)")
                all_interfaces_ok = False
                
        except Exception as e:
            print(f"   ❌ Error reading file: {e}")
            all_interfaces_ok = False
    
    return all_interfaces_ok


if __name__ == "__main__":
    print("🚀 Starting M1K3 RAG End-to-End Testing")
    print("This will test the complete RAG system with real queries\n")
    
    # Run all tests
    test_results = {
        'real_world': test_real_world_scenarios(),
        'rag_comparison': test_rag_vs_no_rag_comparison(), 
        'web_interfaces': test_web_interface_accessibility()
    }
    
    # Final assessment
    print("\n" + "🎯 FINAL ASSESSMENT" + "="*44)
    
    passed_tests = sum(1 for result in test_results.values() if result)
    total_tests = len(test_results)
    
    for test_name, result in test_results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"   {status} {test_name.replace('_', ' ').title()}")
    
    overall_success = passed_tests / total_tests
    
    print(f"\n📊 Overall Score: {passed_tests}/{total_tests} ({overall_success:.1%})")
    
    if overall_success >= 0.8:
        print("🎉 EXCELLENT: RAG system is production-ready!")
        print("✅ All major functionality working correctly")
    elif overall_success >= 0.6:
        print("👍 GOOD: RAG system is functional with minor issues")
        print("✅ Ready for use with some monitoring")
    else:
        print("⚠️  NEEDS WORK: RAG system has significant issues")
        print("🔧 Address failing tests before production use")
    
    print(f"\n💡 Usage: python m1k3.py --rag --query \"Your question here\"")
    print(f"🌐 Web UI: Open rag_knowledge_viewer.html in browser")