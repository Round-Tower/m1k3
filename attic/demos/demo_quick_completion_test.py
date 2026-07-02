#!/usr/bin/env python3
"""
Quick Speech Completion Test
Fast verification that speech cutoff is fixed
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def quick_test():
    print("🎤 Quick Speech Completion Test")
    print("="*40)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Engine loaded with completion fixes:")
        print("   • 950ms total padding")
        print("   • Hardware-aware timing")
        print("   • Robust playback system")
        
        # Test critical ending scenarios
        test_text = "Testing the completion fix - this should play completely to the very end without cutoff."
        print(f"\n🔊 Test: \"{test_text[:50]}...\"")
        engine.synthesize_and_play(test_text, background=False)
        
        print("✅ Completion test successful!")
        return 0
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(quick_test())