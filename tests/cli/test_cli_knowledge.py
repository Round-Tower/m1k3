#!/usr/bin/env python3
"""
CLI Knowledge Testing Suite
Tests M1K3 CLI against various knowledge domains with transparency analysis
"""

import subprocess
import time
import json
import sys
from datetime import datetime

def run_cli_test(query, timeout=30):
    """Run a single CLI test with transparency logging"""
    print(f"\n{'='*60}")
    print(f"🧪 Testing: {query}")
    print(f"{'='*60}")
    
    try:
        # Run CLI with debug transparency
        result = subprocess.run([
            "python", "cli.py", 
            "--transparency", "debug", 
            "--no-voice", 
            "--query", query
        ], capture_output=True, text=True, timeout=timeout)
        
        print("📤 QUERY:", query)
        print("📥 OUTPUT:")
        print(result.stdout)
        
        if result.stderr:
            print("⚠️ ERRORS:")
            print(result.stderr)
        
        print(f"🏁 Exit Code: {result.returncode}")
        
        return {
            "query": query,
            "stdout": result.stdout,
            "stderr": result.stderr,
            "returncode": result.returncode,
            "timestamp": datetime.now().isoformat()
        }
        
    except subprocess.TimeoutExpired:
        print(f"⏰ Test timed out after {timeout}s")
        return {
            "query": query,
            "error": "timeout",
            "timestamp": datetime.now().isoformat()
        }
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return {
            "query": query,
            "error": str(e),
            "timestamp": datetime.now().isoformat()
        }

def main():
    """Run comprehensive CLI knowledge tests"""
    print("🧪 M1K3 CLI Knowledge Testing Suite")
    print("Testing various knowledge domains with debug transparency")
    print(f"Started at: {datetime.now()}")
    
    # Test queries across different domains
    test_queries = [
        # Trivia Questions
        ("What is the capital of France?", "geography_trivia"),
        ("Who wrote Romeo and Juliet?", "literature_trivia"),
        ("What year did World War II end?", "history_trivia"),
        
        # HTML/Web Development
        ("Write a basic HTML page with a title and paragraph", "html_coding"),
        ("Explain the difference between div and span tags", "html_concepts"),
        ("How do you make text bold in HTML?", "html_formatting"),
        
        # Python Programming
        ("Write a Python function to check if a number is prime", "python_coding"),
        ("What is the difference between list and tuple in Python?", "python_concepts"),
        ("How do you read a file in Python?", "python_file_operations"),
        
        # Terminal Commands
        ("How do I list all files in a directory including hidden ones?", "terminal_commands"),
        ("What command shows disk usage in Linux?", "linux_commands"),
        ("How do you search for text in files using grep?", "grep_usage"),
        
        # Pop Culture References
        ("Who played Iron Man in the Marvel movies?", "pop_culture_movies"),
        ("What is the name of Harry Potter's owl?", "pop_culture_books"),
        ("Which band sang Bohemian Rhapsody?", "pop_culture_music"),
        
        # Mathematical Problems
        ("What is 15 * 23?", "basic_math"),
        ("Calculate the area of a circle with radius 5", "geometry_math"),
        
        # Current Events (Testing Knowledge Cutoff)
        ("Who is the current president of the United States?", "current_events"),
        ("What happened in 2024?", "recent_events")
    ]
    
    results = []
    
    for i, (query, category) in enumerate(test_queries, 1):
        print(f"\n🔢 Test {i}/{len(test_queries)} - Category: {category}")
        
        result = run_cli_test(query)
        result["category"] = category
        result["test_number"] = i
        results.append(result)
        
        # Small delay between tests
        time.sleep(2)
    
    # Save results to file
    results_file = f"cli_knowledge_test_results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(results_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\n📊 Testing Complete!")
    print(f"Results saved to: {results_file}")
    
    # Analyze results
    analyze_results(results)
    
    return 0

def analyze_results(results):
    """Analyze test results for patterns and issues"""
    print(f"\n📈 ANALYSIS SUMMARY")
    print("="*50)
    
    total_tests = len(results)
    successful_tests = len([r for r in results if r.get("returncode") == 0])
    failed_tests = len([r for r in results if r.get("returncode") != 0])
    timeout_tests = len([r for r in results if r.get("error") == "timeout"])
    
    print(f"📊 Total Tests: {total_tests}")
    print(f"✅ Successful: {successful_tests}")
    print(f"❌ Failed: {failed_tests}")
    print(f"⏰ Timeouts: {timeout_tests}")
    print(f"📈 Success Rate: {successful_tests/total_tests*100:.1f}%")
    
    # Analyze by category
    categories = {}
    for result in results:
        category = result.get("category", "unknown")
        if category not in categories:
            categories[category] = {"total": 0, "success": 0}
        categories[category]["total"] += 1
        if result.get("returncode") == 0:
            categories[category]["success"] += 1
    
    print(f"\n📋 Results by Category:")
    for category, stats in categories.items():
        success_rate = stats["success"] / stats["total"] * 100
        print(f"   {category}: {stats['success']}/{stats['total']} ({success_rate:.1f}%)")
    
    # Look for common issues
    print(f"\n🔍 Common Issues Detected:")
    
    error_patterns = {}
    for result in results:
        if result.get("stderr"):
            stderr = result["stderr"]
            if "timeout" in stderr.lower():
                error_patterns["timeout"] = error_patterns.get("timeout", 0) + 1
            if "error" in stderr.lower():
                error_patterns["general_error"] = error_patterns.get("general_error", 0) + 1
            if "model" in stderr.lower():
                error_patterns["model_issues"] = error_patterns.get("model_issues", 0) + 1
            if "transparency" in stderr.lower():
                error_patterns["transparency_issues"] = error_patterns.get("transparency_issues", 0) + 1
    
    if error_patterns:
        for pattern, count in error_patterns.items():
            print(f"   {pattern}: {count} occurrences")
    else:
        print("   No major error patterns detected ✅")
    
    # Check transparency output quality
    transparency_features = {
        "processing_start": 0,
        "parameter_display": 0,
        "progress_bars": 0,
        "quality_analysis": 0,
        "backend_logging": 0
    }
    
    for result in results:
        stdout = result.get("stdout", "")
        if "🔍 Starting processing" in stdout:
            transparency_features["processing_start"] += 1
        if "📋 Parameters:" in stdout:
            transparency_features["parameter_display"] += 1
        if "⚡ Generating:" in stdout:
            transparency_features["progress_bars"] += 1
        if "📊 Response Quality Analysis:" in stdout:
            transparency_features["quality_analysis"] += 1
        if "[backend_selection]" in stdout:
            transparency_features["backend_logging"] += 1
    
    print(f"\n🔍 Transparency Features Working:")
    for feature, count in transparency_features.items():
        percentage = count / total_tests * 100
        print(f"   {feature}: {count}/{total_tests} ({percentage:.1f}%)")

if __name__ == "__main__":
    sys.exit(main())