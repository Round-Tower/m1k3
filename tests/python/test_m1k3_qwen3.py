#!/usr/bin/env python3
"""
Test M1K3 with Qwen3 for enhanced reasoning
"""

import subprocess
import time

def test_m1k3_reasoning():
    print("🧪 Testing M1K3 with Qwen3-0.6B Enhanced Reasoning")
    print("=" * 60)
    
    # Test questions to verify reasoning
    test_questions = [
        "What is 25 + 17?",
        "If I have 3 apples and buy 5 more, how many do I have?",
        "What comes next in this sequence: 2, 4, 6, 8, ?",
    ]
    
    for i, question in enumerate(test_questions, 1):
        print(f"\n🧠 Test {i}: {question}")
        print("🤖 M1K3 Response:")
        
        # Use subprocess to interact with M1K3
        try:
            process = subprocess.run([
                'python', 'm1k3.py', '--no-voice', '--no-avatar'
            ], input=f"{question}\nquit\n", 
            text=True, capture_output=True, timeout=30)
            
            # Extract response (skip startup messages)
            output_lines = process.stdout.split('\n')
            response_started = False
            response_lines = []
            
            for line in output_lines:
                if '💤 >' in line:
                    response_started = True
                    continue
                elif response_started and line.strip():
                    if 'quit' in line.lower() or '>' in line:
                        break
                    response_lines.append(line.strip())
            
            response = ' '.join(response_lines)
            print(f"   {response}")
            
            # Basic quality check
            if len(response) > 10:
                print("   ✅ Good response length")
            else:
                print("   ⚠️ Short response")
                
        except Exception as e:
            print(f"   ❌ Error: {e}")
        
        print("-" * 40)

if __name__ == "__main__":
    test_m1k3_reasoning()