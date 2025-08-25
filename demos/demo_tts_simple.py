#!/usr/bin/env python3
"""
Simple TTS Demo - Demonstrates parsing and current voice quality
Shows what we have now and what we're building toward
"""

import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

try:
    from enhanced_voice_engine import create_voice_engine
    from src.utils.model_output_parser import parse_model_output, ContentType
    COMPONENTS_AVAILABLE = True
except ImportError as e:
    print(f"❌ Required components not available: {e}")
    COMPONENTS_AVAILABLE = False

def main():
    """Run complete demo"""
    print("🎪 TTS Enhancement Demo - Current State + Parsing")
    print("=" * 60)
    
    if not COMPONENTS_AVAILABLE:
        print("❌ Components not available")
        return
    
    # Initialize voice engine
    print("🎤 Initializing voice engine...")
    voice_engine = create_voice_engine()
    if not voice_engine.load_model():
        print("❌ Failed to load voice model")
        return
    print("✅ Voice engine ready")
    
    # Demo 1: Show parsing works
    print(f"\n🔍 DEMO 1: Content Parsing")
    print("-" * 30)
    
    mixed_sample = """<thinking>
Let me think about this carefully. This is a complex problem.
</thinking>

The answer is to use a modular approach. *The user nods in understanding.* 

But could you clarify which aspect interests you most?"""
    
    print("📝 Sample text with mixed content:")
    print(f"   {repr(mixed_sample)}")
    
    parsed = parse_model_output(mixed_sample)
    print(f"\n📊 Parser found {len(parsed.segments)} segments:")
    for i, segment in enumerate(parsed.segments, 1):
        print(f"   {i}. {segment.content_type.value.upper()}: '{segment.text[:40]}...'")
    
    # Demo 2: Play samples showing current state
    print(f"\n🎵 DEMO 2: Current Voice Quality (All Sound Same)")
    print("-" * 50)
    
    samples = [
        ("THINKING", "Let me think about this complex problem step by step."),
        ("ANSWER", "Based on analysis, I recommend implementing a modular system."),
        ("NARRATION", "The user pauses thoughtfully, considering their options."),
        ("CLARIFICATION", "Could you clarify which specific aspect interests you?")
    ]
    
    for i, (content_type, text) in enumerate(samples, 1):
        print(f"\n[{i}/4] {content_type}: '{text}'")
        print("▶️  Playing (currently all sound identical)...")
        
        try:
            voice_engine.synthesize_and_play(text, background=False)
            print("✅ Playback complete")
        except Exception as e:
            print(f"❌ Failed: {e}")
        
        time.sleep(1)
    
    # Demo 3: Show what we're building toward
    print(f"\n🚀 DEMO 3: What We're Building")
    print("-" * 35)
    print("🎯 Future enhancements (not yet implemented):")
    print("   • THINKING: Softer, slower, contemplative tone")
    print("   • ANSWER: Clear, confident, authoritative delivery")  
    print("   • NARRATION: Warm, expressive storytelling voice")
    print("   • CLARIFICATION: Rising intonation for questions")
    
    # Demo 4: Test cutoff issue
    print(f"\n🔬 DEMO 4: Speech Cutoff Test")
    print("-" * 30)
    print("🎧 Listen for complete sentence endings:")
    
    cutoff_tests = [
        "This sentence tests for proper completion and ending preservation.",
        "The final words should be clearly audible without any truncation.",
        "Quality speech synthesis maintains all syllables to the very end."
    ]
    
    for i, sentence in enumerate(cutoff_tests, 1):
        print(f"\n[{i}/3] Cutoff test: '{sentence}'")
        print("▶️  Listen for complete ending...")
        
        try:
            voice_engine.synthesize_and_play(sentence, background=False)
            print("✅ Complete")
        except Exception as e:
            print(f"❌ Failed: {e}")
        
        time.sleep(1)
    
    print(f"\n🎯 Demo Summary:")
    print("=" * 20)
    print("✅ Content parsing works - identifies different types")
    print("🎧 Current TTS: All content sounds the same (baseline)")
    print("🚧 Speech cutoff issue: May affect sentence endings")  
    print("🚀 Next steps: Implement voice modulation per content type")
    print("\n💡 Ready to start implementing the audio effects!")

if __name__ == "__main__":
    main()