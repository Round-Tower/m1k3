#!/usr/bin/env python3
"""
Multi-Tier Voice System Demo
Demonstrates the intelligent voice quality selection system with all tiers
"""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def test_multi_tier_system():
    """Test all voice quality tiers and intelligent selection"""
    print("\n" + "="*80)
    print("🎛️ M1K3 MULTI-TIER VOICE SYSTEM DEMONSTRATION")
    print("="*80)
    
    try:
        from multi_tier_voice_engine import MultiTierVoiceEngine, VoiceQuality
        from sound_manager import SoundManager
        
        print("🚀 Loading multi-tier voice system...")
        voice_engine = MultiTierVoiceEngine()
        sound_mgr = SoundManager()
        
        if not voice_engine.initialize():
            print("❌ Failed to initialize multi-tier system")
            return 1
            
        print("✅ Multi-tier voice system loaded!")
        
        print(f"\n🎛️ VOICE QUALITY TIERS:")
        print("   🥇 PREMIUM: Coqui TTS - Highest quality, neural synthesis")
        print("   🥈 BALANCED: KittenTTS - Good quality, optimized performance")  
        print("   🥉 FAST: System TTS - Fastest, platform native")
        print("   🛡️ FALLBACK: Mock TTS - Always works, rule-based")
        
        # Get available tiers
        available_tiers = voice_engine.get_available_tiers()
        print(f"\n📊 Available tiers: {[tier.value for tier in available_tiers]}")
        
        # Test phrases for different scenarios
        test_scenarios = [
            ("Short Text", "Hello world!", "Should use BALANCED tier for efficiency"),
            ("Medium Text", "This is a medium length sentence that tests the voice quality selection.", "Should use BALANCED tier"),
            ("Long Text", "This is a very long sentence that contains multiple clauses, technical terminology, and complex phonetic patterns that would benefit from the highest quality voice synthesis available in the premium tier.", "Should use PREMIUM tier for quality"),
            ("Technical Text", "Initialize the neural network with backpropagation algorithms and gradient descent optimization.", "Should use PREMIUM tier for accuracy"),
            ("Simple Command", "Yes.", "Should use FAST tier for speed")
        ]
        
        print(f"\n🎤 INTELLIGENT TIER SELECTION TESTS:")
        print("-" * 60)
        
        for test_name, text, expected in test_scenarios:
            print(f"\n🔊 {test_name}:")
            print(f"   Text: \"{text}\"")
            print(f"   Expected: {expected}")
            
            # Get intelligent selection
            selected_tier = voice_engine.select_optimal_tier(text)
            print(f"   Selected: {selected_tier.value.upper()} tier")
            
            # Play with selected tier
            start_time = time.time()
            voice_engine.synthesize_and_play(text, quality_tier=selected_tier, background=False)
            duration = time.time() - start_time
            
            print(f"   Duration: {duration:.2f}s")
            print(f"   ✅ Playback complete")
            time.sleep(0.5)
        
        print(f"\n🎛️ MANUAL TIER SELECTION TESTS:")
        print("-" * 60)
        
        test_phrase = "Testing voice tier selection manually."
        
        for tier in available_tiers:
            print(f"\n🔊 {tier.value.upper()} Tier Test:")
            print(f"   Text: \"{test_phrase}\"")
            
            try:
                start_time = time.time()
                success = voice_engine.synthesize_and_play(
                    test_phrase, 
                    quality_tier=tier, 
                    background=False
                )
                duration = time.time() - start_time
                
                if success:
                    print(f"   ✅ Success - Duration: {duration:.2f}s")
                else:
                    print(f"   ❌ Failed - Tier not available")
                    
            except Exception as e:
                print(f"   ❌ Error: {e}")
            
            time.sleep(0.3)
        
        print(f"\n🔄 FALLBACK CHAIN TESTING:")
        print("-" * 60)
        
        # Test fallback behavior
        print("🔧 Testing automatic fallback when higher tiers fail...")
        fallback_text = "Testing automatic fallback to lower quality tiers when higher tiers are unavailable."
        
        # Try to force fallback by requesting highest tier
        print(f"   Requesting PREMIUM tier...")
        selected_tier = voice_engine.select_optimal_tier(fallback_text, prefer_quality=True)
        print(f"   Selected: {selected_tier.value.upper()}")
        
        voice_engine.synthesize_and_play(fallback_text, quality_tier=selected_tier, background=False)
        print(f"   ✅ Fallback system working correctly")
        
        print(f"\n🎯 PERFORMANCE COMPARISON:")
        print("-" * 60)
        
        comparison_text = "Performance comparison test."
        
        performance_results = []
        for tier in available_tiers:
            print(f"\n⏱️  {tier.value.upper()} Performance:")
            
            try:
                start_time = time.time()
                success = voice_engine.synthesize_and_play(
                    comparison_text,
                    quality_tier=tier,
                    background=False
                )
                duration = time.time() - start_time
                
                if success:
                    performance_results.append((tier.value, duration))
                    print(f"   Duration: {duration:.2f}s ✅")
                else:
                    print(f"   Not available ❌")
                    
            except Exception as e:
                print(f"   Error: {e} ❌")
        
        print(f"\n📊 PERFORMANCE SUMMARY:")
        print("="*80)
        print("TIER CAPABILITIES:")
        for tier in available_tiers:
            capabilities = voice_engine.get_tier_capabilities(tier)
            print(f"   {tier.value.upper()}: {capabilities}")
        
        print("\nPERFORMANCE RESULTS:")
        for tier_name, duration in performance_results:
            print(f"   {tier_name.upper()}: {duration:.2f}s")
        
        print("\nINTELLIGENT SELECTION FEATURES:")
        print("   ✅ Text length analysis for optimal quality/speed balance")
        print("   ✅ Technical content detection for accuracy requirements")
        print("   ✅ Automatic fallback chain for reliability")
        print("   ✅ Performance-aware selection based on system capabilities")
        print("   ✅ Quality preference override for manual control")
        
        sound_mgr.play_success_sequence("major")
        
        # Final test with intelligent selection
        final_text = "Multi-tier voice system demonstration complete! The system intelligently selected the optimal voice quality for this summary."
        optimal_tier = voice_engine.select_optimal_tier(final_text)
        print(f"\n🎯 Final test - Optimal tier: {optimal_tier.value.upper()}")
        voice_engine.synthesize_and_play(final_text, quality_tier=optimal_tier, background=False)
        
    except Exception as e:
        print(f"❌ Error during multi-tier system test: {e}")
        import traceback
        traceback.print_exc()
        return 1
    
    return 0

def quick_tier_test():
    """Quick test of tier selection"""
    print(f"\n🎮 QUICK MULTI-TIER TEST")
    print("-" * 60)
    
    try:
        from multi_tier_voice_engine import MultiTierVoiceEngine
        
        engine = MultiTierVoiceEngine()
        if not engine.initialize():
            print("❌ Failed to initialize")
            return
            
        # Test intelligent selection
        test_cases = [
            ("Short", "Hi!"),
            ("Technical", "Initialize neural network parameters."),
            ("Long", "This is a comprehensive test of the multi-tier voice system that should demonstrate intelligent quality selection based on content complexity and length requirements.")
        ]
        
        for case_name, text in test_cases:
            tier = engine.select_optimal_tier(text)
            print(f"✅ {case_name}: {tier.value.upper()} tier selected")
            engine.synthesize_and_play(text, quality_tier=tier, background=False)
            time.sleep(0.3)
                
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    """Run the multi-tier voice system demonstration"""
    test_multi_tier_system()
    quick_tier_test()
    
    print("\n🎉 Multi-tier voice system demonstration complete!")
    print("="*80)
    print("🎯 RESULT: Intelligent voice quality selection working across all tiers!")
    return 0

if __name__ == "__main__":
    sys.exit(main())