#!/usr/bin/env python3
"""
Automatic TTS Demo - Non-interactive showcase of content-type voice modulation
Automatically demonstrates all features without user input
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

def demo_content_types():
    """Automatically demonstrate different content types"""
    print("\n🎭 Content Type Voice Demonstration")
    print("=" * 50)
    
    if not COMPONENTS_AVAILABLE:
        print("❌ Components not available - running in simulation mode")
        return
    
    # Initialize voice engine
    print("🎤 Initializing voice engine...")
    voice_engine = create_voice_engine()
    if not voice_engine.load_model():
        print("❌ Failed to load voice model")
        return
    
    print("✅ Voice engine loaded successfully")
    
    # Define test content for each type
    demo_content = {
        "BASELINE": {
            "description": "Current baseline voice (no modulation)",
            "texts": [
                "This is the baseline voice quality with no modulation applied.",
                "All content currently sounds the same regardless of type."
            ]
        },
        "THINKING": {
            "description": "Should be softer, more contemplative (not yet implemented)",
            "texts": [
                "Let me think about this problem carefully. I need to consider all the implications.",
                "Hmm, this is a complex issue. There are several factors to weigh here."
            ]
        },
        "NARRATION": {
            "description": "Should be expressive and warm (not yet implemented)",
            "texts": [
                "The user pauses thoughtfully, considering the options before them.",
                "A moment of silence fills the room as they contemplate their decision."
            ]
        },
        "ANSWER": {
            "description": "Should be clear and confident (not yet implemented)",
            "texts": [
                "Based on my analysis, the optimal approach is to implement a modular design.",
                "Here's what I recommend: start with solid foundations and build incrementally."
            ]
        },
        "CLARIFICATION": {
            "description": "Should have rising intonation for questions (not yet implemented)",
            "texts": [
                "Could you please clarify what specific aspect you're most interested in?",
                "What would you like to know more about - the technical details or the overview?"
            ]
        }
    }
    
    total_samples = sum(len(content["texts"]) for content in demo_content.values())
    current_sample = 0
    
    for content_type, info in demo_content.items():
        print(f"\n🎯 {content_type} Content Type")
        print(f"📝 {info['description']}")
        print("-" * 40)
        
        for i, text in enumerate(info["texts"], 1):
            current_sample += 1
            print(f"\n[{current_sample}/{total_samples}] Sample {i}: '{text}'")
            print("▶️  Playing audio...")
            
            try:
                voice_engine.synthesize_and_play(text, background=False)
                print("✅ Audio playback complete")
                
                if content_type == "BASELINE":
                    print("   🎧 Note: This is your baseline reference")
                else:
                    print(f"   🚧 Modulation not yet implemented - sounds like baseline")
                
            except Exception as e:
                print(f"❌ Audio playback failed: {e}")
            
            # Pause between samples
            time.sleep(1.5)
    
    print(f"\n🎯 Demo Summary:")
    print("✅ You heard the baseline voice quality")
    print("🚧 Voice modulation for different content types is not yet implemented")
    print("🎯 Next: We'll implement audio effects for each content type")

def demo_mixed_content_parsing():
    """Demonstrate parsing mixed content"""
    print("\n🎪 Mixed Content Parsing Demo")
    print("=" * 40)
    
    mixed_sample = """<thinking>
This is an interesting question. Let me consider the best way to explain this concept.
</thinking>

The key principle here is modularity. *The assistant gestures to emphasize the point.* 

By designing modular components, you gain flexibility and maintainability. 

But could you tell me more about your specific use case?"""
    
    print("📝 Sample mixed content:")
    print(f"   {repr(mixed_sample)}")
    
    print(f"\n🔍 Parsing content into segments...")
    parsed = parse_model_output(mixed_sample)
    
    print(f"📊 Found {len(parsed.segments)} content segments:")
    for i, segment in enumerate(parsed.segments, 1):
        print(f"   {i}. {segment.content_type.value.upper()}: '{segment.text[:50]}...'")
        print(f"      Confidence: {segment.confidence:.2f}")
    
    print(f"\n🎤 In the future, each segment would be spoken with:")
    segment_styles = {
        ContentType.THINKING: "Softer, contemplative voice",
        ContentType.NARRATION: "Expressive, warm storytelling voice", 
        ContentType.ANSWER: "Clear, confident delivery",
        ContentType.CLARIFICATION: "Rising intonation for questions"
    }
    
    for segment in parsed.segments:
        style = segment_styles.get(segment.content_type, "Standard voice")
        print(f"   • {segment.content_type.value}: {style}")
    
    print(f"\nℹ️  Clarification needed: {parsed.needs_clarification}")
    print(f"ℹ️  Has content: {parsed.has_content}")

def demo_speech_cutoff_test():
    """Test the known speech cutoff issue"""
    print("\n🔬 Speech Cutoff Issue Test")
    print("=" * 35)
    
    if not COMPONENTS_AVAILABLE:
        print("❌ Voice engine not available for cutoff test")
        return
    
    voice_engine = create_voice_engine()
    if not voice_engine.load_model():
        print("❌ Failed to load voice model")
        return
    
    cutoff_test_sentences = [
        "This sentence should be heard completely to the very end.",
        "Listen carefully to make sure the final words are not truncated.",
        "The speech synthesis should preserve every syllable until completion.",
        "Testing if the documented cutoff bug affects these sample sentences."
    ]
    
    print("⚠️  Testing for the documented speech cutoff issue")
    print("🎧 Listen carefully to the end of each sentence")
    
    for i, sentence in enumerate(cutoff_test_sentences, 1):
        print(f"\n[{i}/{len(cutoff_test_sentences)}] Cutoff Test: '{sentence}'")
        print("▶️  Playing - listen for complete ending...")
        
        try:
            voice_engine.synthesize_and_play(sentence, background=False)
            print("✅ Playback complete")
        except Exception as e:
            print(f"❌ Playback failed: {e}")
        
        time.sleep(1)
    
    print(f"\n📋 What to listen for:")
    print("✅ Complete sentences with all final words audible")
    print("❌ Sentences that cut off before the last word(s)")
    print("🎯 This will help us verify when the cutoff bug is fixed")

def main():
    """Run all demos automatically"""
    print("🎪 Automatic TTS Enhancement Demo")
    print("=" * 50)
    print("This demo showcases the current TTS system and planned enhancements")
    
    # Demo 1: Content type voices (current vs planned)
    demo_content_types()
    
    print(f"\n" + "="*60)
    input("Press Enter to continue to mixed content parsing demo...")
    
    # Demo 2: Mixed content parsing
    demo_mixed_content_parsing()
    
    print(f"\n" + "="*60)
    input("Press Enter to continue to speech cutoff test...")
    
    # Demo 3: Speech cutoff issue test
    demo_speech_cutoff_test()
    
    print(f"\n🎯 Demo Complete!")
    print("=" * 20)
    print("✅ You heard the current baseline TTS quality")
    print("🔍 You saw how content parsing works")
    print("🔬 You tested for the speech cutoff issue")
    print("🚀 Next steps: Implement voice modulation for each content type!")

if __name__ == "__main__":
    main()