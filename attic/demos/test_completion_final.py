#!/usr/bin/env python3
"""
Final Speech Completion Test
Test the most challenging endings to verify complete playback
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def test_final_completion():
    print("🎯 Final Speech Completion Test")
    print("="*50)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        
        engine = create_voice_engine()
        if not engine.load_model():
            print("❌ Failed to load voice engine")
            return 1
            
        print("✅ Engine loaded with smart fade-out fix")
        
        # Test the most problematic endings
        challenging_endings = [
            "This ends with a hard consonant: BLOCKED.",
            "Testing sibilant sound endings: PROCESS.",  
            "Question ending completeness test?",
            "Final word should be heard: COMPLETE.",
            "Multiple endings: one, two, three, DONE!"
        ]
        
        for i, test_phrase in enumerate(challenging_endings, 1):
            print(f"\n🔊 Test {i}: \"{test_phrase}\"")
            engine.synthesize_and_play(test_phrase, background=False)
            print(f"   ✅ Should hear complete ending!")
            
        print("\n🎯 RESULT: All endings should now play completely!")
        return 0
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(test_final_completion())