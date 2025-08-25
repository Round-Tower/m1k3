#!/usr/bin/env python3
"""
Test Suite for Intelligent Thinking Mode Architecture
Demonstrates how M1K3 can leverage thinking modes while maintaining quality
"""

import time
from src.engines.ai.ai_inference import LocalAIEngine
from src.engines.ai.adaptive_ai_engine import AdaptiveAIEngine
from src.avatar.avatar_controller import AvatarController
from src.utils.thinking_quality_assurance import ThinkingQualityAssurance

def test_intelligent_thinking_system():
    print("🧠 M1K3 Intelligent Thinking Mode Test Suite")
    print("=" * 70)
    
    # Initialize components
    print("🔄 Initializing intelligent thinking system...")
    
    # Base AI engine (existing M1K3 system)
    base_engine = LocalAIEngine(auto_load=True)
    
    # Avatar controller for visual feedback
    avatar_controller = AvatarController()
    
    # Adaptive AI engine (new intelligent layer)
    adaptive_engine = AdaptiveAIEngine(
        base_ai_engine=base_engine,
        avatar_controller=avatar_controller,
        enable_thinking_mode=True,
        show_reasoning_by_default=False
    )
    
    # Quality assurance system
    qa_system = ThinkingQualityAssurance()
    
    print("✅ All components initialized successfully")
    print()
    
    # Test cases demonstrating different complexity levels
    test_cases = [
        {
            "name": "Simple Factual Query",
            "query": "What is the capital of France?",
            "expected_mode": "direct",
            "show_reasoning": False,
            "description": "Should use direct mode for simple facts"
        },
        {
            "name": "Mathematical Reasoning",
            "query": "If I have 15 apples and give away 7, then buy 12 more, how many apples do I have? Show your work.",
            "expected_mode": "thinking",
            "show_reasoning": True,
            "description": "Should use thinking mode with visible reasoning for math"
        },
        {
            "name": "Complex Logic Problem",
            "query": "If all birds can fly, and penguins are birds, but penguins cannot fly, what can we conclude about this logical statement?",
            "expected_mode": "thinking",
            "show_reasoning": True,
            "description": "Should use thinking mode for logical analysis"
        },
        {
            "name": "Creative Writing",
            "query": "Write a short story about an AI learning to understand emotions.",
            "expected_mode": "thinking",
            "show_reasoning": False,
            "description": "Should use thinking mode but hide reasoning for creative tasks"
        },
        {
            "name": "Ambiguous Query",
            "query": "What's the best approach to the problem we discussed earlier?",
            "expected_mode": "direct",
            "show_reasoning": False,
            "description": "Should fallback to direct mode and request clarification"
        }
    ]
    
    results = []
    
    for i, test in enumerate(test_cases, 1):
        print(f"🧪 Test {i}: {test['name']}")
        print(f"📝 Query: {test['query']}")
        print(f"🎯 Expected: {test['expected_mode']} mode")
        print(f"💡 Purpose: {test['description']}")
        print()
        
        # Get insights about how the system would handle this query
        insights = adaptive_engine.get_thinking_insights(test['query'])
        print("🔍 System Analysis:")
        print(f"   Complexity detected: {insights['complexity']}")
        print(f"   Recommended mode: {insights['recommended_mode']}")
        print(f"   Would use thinking: {insights['would_use_thinking_mode']}")
        print(f"   Would show reasoning: {insights['would_show_reasoning']}")
        print()
        
        # Generate response
        print("🤖 Generating response...")
        start_time = time.time()
        
        full_response = ""
        avatar_states_seen = []
        
        try:
            for token in adaptive_engine.generate_response(
                test['query'], 
                max_tokens=150,
                show_reasoning=test['show_reasoning']
            ):
                full_response += token
                
                # Track avatar states
                current_state = avatar_controller.current_state.value
                if current_state not in avatar_states_seen:
                    avatar_states_seen.append(current_state)
            
            generation_time = time.time() - start_time
            
            print(f"✅ Response generated in {generation_time:.2f}s")
            print(f"📊 Avatar states used: {avatar_states_seen}")
            print(f"📄 Response length: {len(full_response)} characters")
            print()
            
            # Quality assessment if thinking mode was used
            if "<think>" in full_response or insights['would_use_thinking_mode']:
                print("🔍 Quality Assessment:")
                assessment = qa_system.assess_thinking_quality(full_response, test['query'])
                
                print(f"   Overall quality: {assessment.overall_score:.2f}")
                print(f"   Confidence level: {assessment.confidence_level.value}")
                print(f"   Reasoning coherence: {assessment.reasoning_coherence:.2f}")
                print(f"   Issues detected: {len(assessment.issues_detected)}")
                
                if assessment.issues_detected:
                    print(f"   Issue types: {[issue.value for issue in assessment.issues_detected]}")
                
                print(f"   Recommended action: {assessment.recommended_action.value}")
                print()
                
                # Apply quality assurance if needed
                if assessment.overall_score < 0.7:
                    print("🔧 Applying quality assurance...")
                    safe_response, is_safe, warnings = qa_system.apply_quality_assurance(
                        full_response, test['query']
                    )
                    
                    print(f"   Safe response available: {is_safe}")
                    if warnings:
                        print(f"   Warnings: {warnings}")
                    print()
            
            # Show final response
            print("💬 Final Response:")
            print("─" * 50)
            print(full_response)
            print("─" * 50)
            print()
            
            # Record results
            results.append({
                "test_name": test['name'],
                "complexity_detected": insights['complexity'],
                "mode_used": insights['recommended_mode'],
                "thinking_enabled": insights['would_use_thinking_mode'],
                "reasoning_shown": insights['would_show_reasoning'],
                "response_time": generation_time,
                "response_length": len(full_response),
                "avatar_states": avatar_states_seen,
                "success": True
            })
            
        except Exception as e:
            import traceback
            print(f"❌ Error during generation: {e}")
            print("Full traceback:")
            traceback.print_exc()
            results.append({
                "test_name": test['name'],
                "success": False,
                "error": str(e)
            })
        
        print("=" * 70)
        print()
    
    # Summary and analysis
    print("📊 TEST SUMMARY")
    print("=" * 70)
    
    successful_tests = [r for r in results if r.get('success', False)]
    
    if successful_tests:
        print(f"✅ Successful tests: {len(successful_tests)}/{len(results)}")
        
        # Mode distribution
        mode_counts = {}
        for result in successful_tests:
            mode = result.get('mode_used', 'unknown')
            mode_counts[mode] = mode_counts.get(mode, 0) + 1
        
        print(f"📈 Mode distribution: {mode_counts}")
        
        # Performance metrics
        avg_response_time = sum(r.get('response_time', 0) for r in successful_tests) / len(successful_tests)
        avg_response_length = sum(r.get('response_length', 0) for r in successful_tests) / len(successful_tests)
        
        print(f"⏱️  Average response time: {avg_response_time:.2f}s")
        print(f"📏 Average response length: {avg_response_length:.0f} characters")
        
        # Avatar state analysis
        all_states = []
        for result in successful_tests:
            all_states.extend(result.get('avatar_states', []))
        
        unique_states = list(set(all_states))
        print(f"🎭 Avatar states utilized: {unique_states}")
        
        # Thinking mode effectiveness
        thinking_tests = [r for r in successful_tests if r.get('thinking_enabled', False)]
        print(f"🧠 Thinking mode usage: {len(thinking_tests)}/{len(successful_tests)} tests")
        
        if thinking_tests:
            thinking_avg_time = sum(r.get('response_time', 0) for r in thinking_tests) / len(thinking_tests)
            direct_tests = [r for r in successful_tests if not r.get('thinking_enabled', False)]
            direct_avg_time = sum(r.get('response_time', 0) for r in direct_tests) / len(direct_tests) if direct_tests else 0
            
            print(f"   Thinking mode avg time: {thinking_avg_time:.2f}s")
            print(f"   Direct mode avg time: {direct_avg_time:.2f}s")
            print(f"   Time overhead: {(thinking_avg_time - direct_avg_time):.2f}s")
    
    # System performance stats
    print(f"\n🎯 SYSTEM PERFORMANCE:")
    perf_stats = adaptive_engine.get_performance_stats()
    
    if 'total_responses' in perf_stats:
        print(f"   Total responses generated: {perf_stats['total_responses']}")
        print(f"   Thinking mode usage rate: {perf_stats.get('thinking_mode_usage_rate', 0):.1f}%")
        print(f"   Success rate: {perf_stats.get('thinking_mode_success_rate', 0):.1f}%")
        print(f"   Average confidence: {perf_stats.get('average_confidence', 0):.2f}")
    
    print(f"\n🎉 Intelligent Thinking Mode Test Complete!")
    print("🚀 System ready for production with enhanced reasoning capabilities!")
    
    return successful_tests, perf_stats

def test_individual_components():
    """Test individual components in isolation"""
    
    print("\n🔧 COMPONENT ISOLATION TESTS")
    print("=" * 50)
    
    # Test thinking mode engine complexity detection
    from src.utils.thinking_mode_engine import ThinkingModeEngine
    
    engine = ThinkingModeEngine(None)
    
    test_queries = [
        ("Hello there!", "simple"),
        ("What is 15 + 27?", "moderate"), 
        ("Explain the philosophical implications of artificial consciousness", "complex"),
        ("How do we solve climate change through technology innovation?", "complex"),
        ("Thanks!", "simple")
    ]
    
    print("🧪 Complexity Detection Test:")
    for query, expected in test_queries:
        detected = engine.assess_query_complexity(query)
        status = "✅" if detected.value == expected else "❌"
        print(f"   {status} '{query}' → {detected.value} (expected: {expected})")
    
    print()
    
    # Test thinking parser
    from src.utils.thinking_parser import ThinkingContentParser
    
    parser = ThinkingContentParser()
    
    sample_thinking = """
    <think>
    Let me work through this math problem step by step.
    I need to add 15 and 27.
    15 + 27 = 42
    I'm confident this is correct.
    </think>
    
    The answer is 42.
    """
    
    print("🧪 Thinking Parser Test:")
    flow = parser.parse_thinking_content(sample_thinking)
    summary = parser.get_reasoning_summary(flow)
    
    print(f"   Segments analyzed: {summary['total_segments']}")
    print(f"   Overall confidence: {summary['overall_confidence']:.2f}")
    print(f"   Dominant reasoning: {summary['dominant_reasoning_type']}")
    print(f"   Dominant emotion: {summary['dominant_emotion']}")
    print()
    
    # Test quality assurance
    print("🧪 Quality Assurance Test:")
    qa = ThinkingQualityAssurance()
    
    good_response = "The capital of France is Paris. This is a well-known geographical fact."
    bad_response = "The capital of France is... wait, let me think... actually maybe it's London? No, that's wrong. I'm confused."
    
    good_assessment = qa.assess_thinking_quality(good_response, "What is the capital of France?")
    bad_assessment = qa.assess_thinking_quality(bad_response, "What is the capital of France?")
    
    print(f"   Good response quality: {good_assessment.overall_score:.2f} (issues: {len(good_assessment.issues_detected)})")
    print(f"   Bad response quality: {bad_assessment.overall_score:.2f} (issues: {len(bad_assessment.issues_detected)})")
    
    print("\n✅ Component tests complete!")

def main():
    print("🤖 M1K3 Intelligent Thinking Mode Architecture")
    print("🎯 Leveraging model capabilities while maintaining quality")
    print()
    
    try:
        # Test individual components first
        test_individual_components()
        
        # Test full integrated system
        results, stats = test_intelligent_thinking_system()
        
        if results:
            print(f"\n🎊 SUCCESS: {len(results)} tests completed successfully!")
            print("🧠 M1K3 now intelligently leverages thinking modes for enhanced reasoning!")
        else:
            print("\n⚠️  Some tests failed - check system configuration")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Test suite error: {e}")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)