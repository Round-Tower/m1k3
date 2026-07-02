#!/usr/bin/env python3
"""
Quick Audio Test
Tests the audio fixes quickly
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def quick_test():
    print("🔧 Quick Audio Fixes Test")
    print("="*40)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Engine loaded with fixes:")
        print("   • 300ms end padding")
        print("   • 100ms fade-out") 
        print("   • Gentler effects")
        print("   • Clipping protection")
        
        # Quick test
        engine.set_profile("premium")
        test_text = "Testing audio fixes - this should play completely without cutoff or distortion."
        print(f"\n🔊 Test: \"{test_text}\"")
        engine.synthesize_and_play(test_text, background=False)
        
        print("✅ Test complete!")
        return 0
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(quick_test())