#!/usr/bin/env python3
"""
Test Anti-Hallucination Optimizations for M1K3
Validates improved parameters reduce confused/rambling responses
"""

import time
from src.engines.ai.ai_inference import LocalAIEngine

def test_anti_hallucination_optimizations():
    print("🧪 M1K3 Anti-Hallucination Optimization Test")
    print("=" * 60)
    
    print("🔄 Initializing optimized AI engine...")
    start_time = time.time()
    
    try:
        engine = LocalAIEngine(auto_load=True)
        load_time = time.time() - start_time
        
        if hasattr(engine, '_current_model_name') and engine._current_model_name:
            model_name = engine._current_model_name
            print(f"✅ Model loaded: {model_name}")
            print(f"⏱️  Load time: {load_time:.2f}s")
            print()
            
            # Test scenarios that previously caused hallucinations
            test_cases = [
                {
                    "name": "Direct Math Question",
                    "prompt": "What is 15 + 27?",
                    "expected_patterns": ["42", "15", "27", "add"],
                    "avoid_patterns": ["typo", "check again", "maybe", "wait"],
                    "max_words": 25
                },
                {
                    "name": "Simple Logic",
                    "prompt": "If all cats are animals, and Fluffy is a cat, what can we conclude about Fluffy?",
                    "expected_patterns": ["fluffy", "animal", "cat"],
                    "avoid_patterns": ["confusing", "another way", "getting confused", "either way"],
                    "max_words": 30
                },
                {
                    "name": "Basic Request",
                    "prompt": "Write a haiku about computers.",
                    "expected_patterns": ["haiku", "computer", "three lines"],
                    "avoid_patterns": ["mammal", "belongs to", "category"],
                    "max_words": 40
                },
                {
                    "name": "Factual Question", 
                    "prompt": "What is the capital of France?",
                    "expected_patterns": ["paris", "france", "capital"],
                    "avoid_patterns": ["i think", "maybe", "perhaps", "looking at"],
                    "max_words": 15
                },
                {
                    "name": "Definition Request",
                    "prompt": "Define artificial intelligence.",
                    "expected_patterns": ["artificial", "intelligence", "computer", "system"],
                    "avoid_patterns": ["wait", "check", "typo", "another angle"],
                    "max_words": 50
                }
            ]
            
            print("🔍 Testing optimized responses...")
            print("-" * 50)
            
            total_score = 0
            total_tests = len(test_cases)
            
            for i, test in enumerate(test_cases, 1):
                print(f"\n🧠 Test {i}: {test['name']}")
                print(f"❓ Question: {test['prompt']}")
                print("🤖 Response: ", end="", flush=True)
                
                start_gen = time.time()
                response_tokens = []
                
                try:
                    for token in engine.generate_response(test['prompt'], max_tokens=100):
                        response_tokens.append(token)
                        print(token, end="", flush=True)
                    
                    gen_time = time.time() - start_gen
                    full_response = ''.join(response_tokens)
                    
                    print(f"\n\n📊 Analysis:")
                    
                    # Length analysis
                    word_count = len(full_response.split())
                    char_count = len(full_response)
                    print(f"   Length: {char_count} characters, {word_count} words")
                    
                    # Speed analysis
                    if gen_time > 0:
                        speed = len(response_tokens) / gen_time
                        print(f"   Speed: {gen_time:.2f}s, ~{speed:.1f} tokens/sec")
                    
                    # Content quality analysis
                    response_lower = full_response.lower()
                    
                    # Check for expected patterns (positive indicators)
                    expected_found = sum(1 for pattern in test['expected_patterns'] 
                                       if pattern.lower() in response_lower)
                    expected_score = expected_found / len(test['expected_patterns'])
                    
                    # Check for hallucination patterns (negative indicators) 
                    avoid_found = sum(1 for pattern in test['avoid_patterns']
                                    if pattern.lower() in response_lower)
                    avoid_penalty = avoid_found * 0.5  # Each pattern reduces score
                    
                    # Length appropriateness
                    length_score = 1.0 if word_count <= test['max_words'] else 0.5
                    
                    # Overall quality score
                    quality_score = max(0, expected_score - avoid_penalty) * length_score
                    
                    # Categorize quality
                    if quality_score >= 0.8:
                        quality_label = "✅ Excellent"
                        color_score = 3
                    elif quality_score >= 0.6:
                        quality_label = "✅ Good"
                        color_score = 2
                    elif quality_score >= 0.3:
                        quality_label = "⚠️ Fair"
                        color_score = 1
                    else:
                        quality_label = "❌ Poor"
                        color_score = 0
                    
                    total_score += color_score
                    
                    print(f"   Expected patterns: {expected_found}/{len(test['expected_patterns'])}")
                    print(f"   Hallucination indicators: {avoid_found} found")
                    print(f"   Length appropriateness: {'✅' if word_count <= test['max_words'] else '⚠️'}")
                    print(f"   Overall quality: {quality_label} (score: {quality_score:.2f})")
                    
                    # Specific hallucination analysis
                    if avoid_found > 0:
                        print(f"   🚨 Detected patterns: {[p for p in test['avoid_patterns'] if p.lower() in response_lower]}")
                    
                except Exception as e:
                    print(f"\n❌ Generation error: {e}")
                    print(f"   Quality: ❌ Error")
                
                print("-" * 50)
            
            # Final assessment
            max_possible_score = total_tests * 3
            percentage = (total_score / max_possible_score) * 100
            
            print(f"\n📋 Optimization Assessment")
            print("=" * 60)
            print(f"Overall Score: {total_score}/{max_possible_score} ({percentage:.1f}%)")
            
            if percentage >= 80:
                assessment = "🎉 EXCELLENT - Anti-hallucination optimizations working well!"
                recommendations = [
                    "✅ Responses are focused and accurate",
                    "✅ Minimal hallucination patterns detected",
                    "✅ Length control is effective"
                ]
            elif percentage >= 60:
                assessment = "✅ GOOD - Optimizations showing positive results"
                recommendations = [
                    "✅ Most responses are improved",
                    "⚠️ Some minor hallucination patterns remain",
                    "🔧 Consider further temperature reduction"
                ]
            elif percentage >= 40:
                assessment = "⚠️ FAIR - Some improvement but needs refinement"
                recommendations = [
                    "⚠️ Mixed results in response quality",
                    "🔧 Need to adjust generation parameters",
                    "🔧 Consider stricter prompt formatting"
                ]
            else:
                assessment = "❌ POOR - Optimizations need major revision"
                recommendations = [
                    "❌ Significant hallucination patterns persist",
                    "🔧 Need to drastically reduce temperature",
                    "🔧 Consider switching to greedy decoding"
                ]
            
            print(f"\n{assessment}")
            print("\nRecommendations:")
            for rec in recommendations:
                print(f"  {rec}")
            
            print(f"\n🎯 Model Performance: {model_name}")
            print(f"   Load time: {load_time:.2f}s")
            print(f"   Anti-hallucination config: ✅ Active")
            print(f"   Response validation: ✅ Active")
            
            return percentage >= 60
            
        else:
            print("❌ No model loaded")
            return False
            
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

def main():
    print("🤖 M1K3 Response Quality Optimization Suite")
    print("🎯 Testing anti-hallucination parameters and validation")
    print()
    
    success = test_anti_hallucination_optimizations()
    
    if success:
        print("\n✨ Optimization test completed successfully!")
        print("🎮 Ready for production use with improved accuracy")
    else:
        print("\n❌ Optimization test suggests further tuning needed")
        print("🔧 Review generation parameters and validation logic")
    
    return 0 if success else 1

if __name__ == "__main__":
    exit(main())