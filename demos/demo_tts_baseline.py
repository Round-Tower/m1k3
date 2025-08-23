#!/usr/bin/env python3
"""
TTS Baseline Demo - Listen to current voice quality
This shows us what we're starting with before implementing content-type modulation
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from enhanced_voice_engine import create_voice_engine
    VOICE_ENGINE_AVAILABLE = True
except ImportError:
    VOICE_ENGINE_AVAILABLE = False
    print("❌ Voice engine not available")

def demo_baseline_tts():
    """Demo the current baseline TTS quality"""
    if not VOICE_ENGINE_AVAILABLE:
        print("❌ Voice engine not available for demo")
        return
    
    print("🎤 TTS Baseline Demo - Current Voice Quality")
    print("=" * 50)
    
    # Create voice engine
    engine = create_voice_engine()
    if not engine.load_model():
        print("❌ Failed to load voice model")
        return
    
    print("✅ Voice engine loaded successfully")
    
    # Test different types of content with current system
    test_cases = [
        {
            "name": "Baseline Reference",
            "text": "This is our baseline reference for voice quality. All future enhancements will be compared to this.",
            "description": "Standard voice with current settings"
        },
        {
            "name": "Thinking-Style Content", 
            "text": "Let me think about this carefully. I need to consider all the implications and weigh the different options before providing a response.",
            "description": "Content that should sound contemplative (currently sounds same as baseline)"
        },
        {
            "name": "Answer-Style Content",
            "text": "Based on my analysis, the best approach is to implement a modular system that provides flexibility while maintaining performance and reliability.",
            "description": "Content that should sound confident and authoritative (currently sounds same as baseline)"
        },
        {
            "name": "Narration-Style Content",
            "text": "The user pauses thoughtfully, considering the various options available to them as they prepare to make an important decision about their project.",
            "description": "Content that should sound warm and expressive (currently sounds same as baseline)"  
        },
        {
            "name": "Clarification-Style Content",
            "text": "Could you please help me understand what specific aspect you're most interested in? Are you looking for technical details or a general overview?",
            "description": "Content that should have rising intonation for questions (currently sounds same as baseline)"
        },
        {
            "name": "Cutoff Test",
            "text": "This sentence is specifically designed to test the speech cutoff issue that has been documented in the bugs file.",
            "description": "Listen carefully to the end - is the sentence complete or cut off?"
        }
    ]
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\n[{i}/{len(test_cases)}] {test_case['name']}")
        print(f"📝 {test_case['description']}")
        print(f"🔤 Text: '{test_case['text']}'")
        print("▶️  Playing audio...")
        
        try:
            # Play audio and wait for completion
            engine.synthesize_and_play(test_case['text'], background=False)
            
            # Wait a moment between samples
            time.sleep(1.5)
            
            print("✅ Playback complete")
            
        except Exception as e:
            print(f"❌ Playback failed: {e}")
    
    print(f"\n🎯 Demo Summary:")
    print("- You should have heard 6 different text samples")  
    print("- All should sound the same (no content-type modulation yet)")
    print("- Listen for the speech cutoff issue in the last sample")
    print("- Note the baseline quality for comparison with future improvements")
    
    print(f"\n📝 Observations to note:")
    print("1. Overall voice quality and clarity")
    print("2. Any cutoff issues at sentence endings") 
    print("3. Consistency across different content types")
    print("4. Any audio artifacts or issues")
    
    print(f"\n🚀 Next steps:")
    print("- Implement model output parser")
    print("- Add content-type specific voice modulation") 
    print("- Create audio effects for different content types")
    print("- Fix speech cutoff issue")

if __name__ == "__main__":
    demo_baseline_tts()