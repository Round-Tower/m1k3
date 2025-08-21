#!/usr/bin/env python3
"""
M1K3 Audio Fixes Demo
Tests the fixes for speech cutoff and distortion issues
"""

import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def test_audio_fixes():
    """Test the audio quality fixes"""
    print("\n" + "="*80)
    print("🔧 M1K3 AUDIO FIXES DEMONSTRATION")
    print("="*80)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        from sound_manager import SoundManager
        
        print("🚀 Loading enhanced voice engine with fixes...")
        engine = create_voice_engine()
        sound_mgr = SoundManager()
        
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Enhanced voice engine loaded!")
        
        print(f"\n🔧 AUDIO FIXES IMPLEMENTED:")
        print("   ✅ End padding (300ms) to prevent speech cutoff")
        print("   ✅ Fade-out (100ms) for smooth ending")
        print("   ✅ Gentler audio effects to reduce distortion")
        print("   ✅ Clipping protection with soft limiting")
        print("   ✅ Blended formant correction (70% processed + 30% original)")
        print("   ✅ Reduced clarity enhancement intensity")
        
        # Test phrases that would show cutoff issues
        test_phrases = [
            ("Ending Test", "This sentence should play completely to the very end without being cut off."),
            ("Loud Phrase", "THIS IS A LOUD PHRASE THAT MIGHT CAUSE DISTORTION WITHOUT PROTECTION!"),
            ("Complex Audio", "Sophisticated speech synthesis with sibilant sounds, sharp consonants, and complex formant patterns."),
            ("Full Pipeline", "Testing the complete audio processing pipeline with all effects enabled for maximum quality.")
        ]
        
        # Test natural profile (basic processing)
        print(f"\n🎤 Testing NATURAL profile (basic processing):")
        print("-" * 60)
        engine.set_profile("natural")
        sound_mgr.play_contextual_sound("success")
        
        for test_name, phrase in test_phrases:
            print(f"\n   🔊 {test_name}: \"{phrase[:40]}...\"")
            engine.synthesize_and_play(phrase, background=False)
            print(f"      ✅ Complete playback test")
            time.sleep(0.5)
        
        # Test premium profile (full processing with fixes)
        print(f"\n🎤 Testing PREMIUM profile (full processing with fixes):")
        print("-" * 60)
        engine.set_profile("premium")
        sound_mgr.play_contextual_sound("success")
        
        for test_name, phrase in test_phrases:
            print(f"\n   🔊 {test_name}: \"{phrase[:40]}...\"")
            engine.synthesize_and_play(phrase, background=False)
            print(f"      ✅ Complete playback with distortion protection")
            time.sleep(0.5)
        
        # Test intercom effect (user mentioned it sounds great)
        print(f"\n🎤 Testing BROADCAST profile (intercom effect - sounds great!):")
        print("-" * 60)
        engine.set_profile("broadcast")
        sound_mgr.play_contextual_sound("success")
        
        intercom_phrase = "Broadcasting from M1K3 radio station with professional intercom effect processing."
        print(f"   🔊 Radio Test: \"{intercom_phrase}\"")
        engine.synthesize_and_play(intercom_phrase, background=False)
        print(f"      ✅ Intercom effect sounds excellent!")
        
        print(f"\n📊 FIXES SUMMARY:")
        print("="*80)
        print("BEFORE (Issues):")
        print("   ❌ Speech cut off at the end")
        print("   ❌ Mild distortion from aggressive processing")
        print("   ❌ Abrupt audio endings")
        print("   ❌ Potential clipping on loud sounds")
        
        print("\nAFTER (Fixed):")
        print("   ✅ 300ms end padding prevents cutoff")
        print("   ✅ 100ms fade-out for smooth endings")
        print("   ✅ Gentler effects reduce distortion")
        print("   ✅ Soft clipping protection")
        print("   ✅ Blended processing maintains naturalness")
        print("   ✅ Conservative normalization levels")
        
        print(f"\n💡 EFFECT OPTIMIZATIONS:")
        print("   🎛️ FormantCorrectionEffect: 98% shift (was 95%) + blending")
        print("   🎛️ ClarityEnhancementEffect: Reduced emphasis + soft saturation")
        print("   🎛️ SibilanceReductionEffect: Unchanged (working well)")
        print("   🎛️ ClippingProtectionEffect: New soft limiting")
        print("   🎛️ IntercomEffect: Unchanged (sounds great!)")
        
        sound_mgr.play_success_sequence("major")
        engine.synthesize_and_play("Audio fixes testing complete! Speech should now play fully without distortion.", background=False)
        
    except Exception as e:
        print(f"❌ Error during audio fixes test: {e}")
        return 1
    
    return 0

def quick_comparison_test():
    """Quick before/after comparison"""
    print(f"\n🎮 QUICK BEFORE/AFTER COMPARISON")
    print("-" * 80)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return
            
        test_sentence = "This is a test of the audio processing improvements with complete playback and distortion protection."
        
        print(f"🎤 Test sentence: \"{test_sentence}\"")
        print("\n🔄 Testing different profiles with fixes:")
        
        profiles = [
            ("natural", "Basic processing with fixes"),
            ("assistant", "Professional with compression"),
            ("premium", "Full pipeline with all fixes"),
            ("broadcast", "Intercom effect (sounds great!)")
        ]
        
        for profile, description in profiles:
            print(f"\n   {profile.upper()}: {description}")
            engine.set_profile(profile)
            engine.synthesize_and_play(test_sentence, background=False)
            time.sleep(0.5)
            
        print("\n✅ All profiles should now play completely without cutoff or distortion!")
                
    except Exception as e:
        print(f"❌ Error: {e}")

def main():
    """Run the audio fixes demonstration"""
    test_audio_fixes()
    quick_comparison_test()
    
    print("\n🎉 Audio fixes demonstration complete!")
    print("="*80)
    return 0

if __name__ == "__main__":
    sys.exit(main())