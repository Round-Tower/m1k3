#!/usr/bin/env python3
"""
Test Temperature Fix
Quick test to verify our temperature fix works
"""

import subprocess
import time

def test_temperature_fix():
    """Test several queries that previously timed out"""
    
    test_queries = [
        "What is 2 + 2?",
        "What is 5 * 6?", 
        "What year did World War II end?",
        "Who wrote Romeo and Juliet?"
    ]
    
    print("🧪 Testing Temperature Fix")
    print("="*50)
    
    results = []
    
    for i, query in enumerate(test_queries, 1):
        print(f"\n🔢 Test {i}/4: '{query}'")
        
        start_time = time.time()
        
        try:
            # Run with timeout to catch hangs
            result = subprocess.run([
                "python", "cli.py", 
                "--transparency", "basic",  # Use basic to reduce output 
                "--no-voice", 
                "--query", query
            ], capture_output=True, text=True, timeout=20)
            
            duration = time.time() - start_time
            
            if result.returncode == 0:
                print(f"✅ SUCCESS in {duration:.1f}s")
                
                # Check if we got a direct answer
                output = result.stdout
                if query == "What is 2 + 2?" and "4" in output:
                    print("   🎯 Got correct answer: 4")
                elif query == "What is 5 * 6?" and "30" in output:
                    print("   🎯 Got correct answer: 30")
                elif "Shakespeare" in output:
                    print("   🎯 Got relevant answer about Shakespeare")
                elif "1945" in output:
                    print("   🎯 Got correct year: 1945")
                else:
                    print("   ⚠️  Response may not contain direct answer")
                
                results.append(("PASS", duration))
            else:
                print(f"❌ FAILED (exit code {result.returncode})")
                results.append(("FAIL", duration))
                
        except subprocess.TimeoutExpired:
            duration = time.time() - start_time
            print(f"⏰ TIMEOUT after {duration:.1f}s")
            results.append(("TIMEOUT", duration))
        except Exception as e:
            print(f"💥 ERROR: {e}")
            results.append(("ERROR", 0))
    
    # Summary
    print(f"\n📊 RESULTS SUMMARY")
    print("="*30)
    
    passes = sum(1 for r in results if r[0] == "PASS")
    timeouts = sum(1 for r in results if r[0] == "TIMEOUT")
    fails = sum(1 for r in results if r[0] in ["FAIL", "ERROR"])
    
    print(f"✅ Passed: {passes}/4 ({passes/4*100:.1f}%)")
    print(f"⏰ Timeouts: {timeouts}/4 ({timeouts/4*100:.1f}%)")
    print(f"❌ Failed: {fails}/4 ({fails/4*100:.1f}%)")
    
    if timeouts == 0:
        print(f"\n🎉 SUCCESS: Temperature fix eliminated timeouts!")
    elif timeouts < 4:
        print(f"\n🔄 PROGRESS: Reduced timeouts from 4 to {timeouts}")
    else:
        print(f"\n⚠️  Still have timeout issues to investigate")
        
    avg_time = sum(r[1] for r in results if r[0] == "PASS") / max(passes, 1)
    print(f"⏱️  Average response time: {avg_time:.1f}s")
    
    return passes, timeouts, fails

if __name__ == "__main__":
    test_temperature_fix()