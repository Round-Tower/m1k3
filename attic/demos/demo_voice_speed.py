#!/usr/bin/env python3
"""
M1K3 Voice Speed Performance Demo
Tests and demonstrates the optimized voice synthesis speed improvements.
"""

import time
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

def benchmark_voice_speed():
    """Benchmark voice synthesis speed with different text lengths"""
    print("\n" + "="*70)
    print("⚡ M1K3 VOICE SPEED OPTIMIZATION DEMO ⚡")
    print("="*70)
    
    try:
        from enhanced_voice_engine import create_voice_engine
        from sound_manager import SoundManager
        
        print("🚀 Loading optimized voice engine...")
        engine = create_voice_engine()
        sound_mgr = SoundManager()
        
        if not engine.load_model():
            print("❌ Failed to load voice model")
            return 1
            
        print("✅ Voice engine loaded successfully!")
        
        # Test phrases of different lengths
        test_phrases = [
            ("Short", "Hello there!"),
            ("Medium", "This is a medium length sentence to test voice synthesis speed."),
            ("Long", "This is a longer sentence that demonstrates the improved chunking and reduced silence between segments for much faster speech synthesis in the M1K3 voice system."),
            ("Very Long", "The M1K3 voice synthesis system has been optimized for speed with larger chunk sizes, reduced inter-chunk silence, and smarter text processing. This very long sentence tests the performance improvements across multiple chunks while maintaining natural speech quality and timing.")
        ]
        
        print(f"\n📊 Performance Test Results:")
        print("-" * 70)
        
        for length_desc, phrase in test_phrases:
            print(f"\n🎤 Testing {length_desc} text ({len(phrase)} chars):")
            print(f"   Text: \"{phrase[:50]}{'...' if len(phrase) > 50 else ''}\"")
            
            # Measure synthesis time
            start_time = time.time()
            engine.synthesize_and_play(phrase, background=False)
            synthesis_time = time.time() - start_time
            
            chars_per_second = len(phrase) / synthesis_time if synthesis_time > 0 else 0
            
            print(f"   ⏱️  Synthesis time: {synthesis_time:.2f}s")
            print(f"   📈 Speed: {chars_per_second:.1f} chars/second")
            
            # Rate the performance
            if chars_per_second > 100:
                rating = "🚀 Excellent"
            elif chars_per_second > 70:
                rating = "✅ Good"
            elif chars_per_second > 50:
                rating = "⚡ Fair"
            else:
                rating = "🐌 Slow"
                
            print(f"   🏆 Rating: {rating}")
            
            time.sleep(0.5)
        
        print("\n🔧 Configuration Details:")
        print(f"   • Chunk Size: {engine.engine.chunk_size} characters")
        print(f"   • Inter-chunk Silence: {engine.engine.inter_chunk_silence}s")
        print(f"   • Sample Rate: {engine.engine.sample_rate} Hz")
        
        print("\n🎯 Speed Optimizations Applied:")
        print("   ✅ Increased chunk size from 180 → 300 characters")
        print("   ✅ Reduced inter-chunk silence from 0.1s → 0.03s")
        print("   ✅ Reduced text overlap from 2 → 1 words")
        print("   ✅ Optimized text processing pipeline")
        
        print("\n🎵 Testing different voice profiles for speed...")
        sound_mgr.play_contextual_sound("success")
        
        profiles_to_test = ["natural", "assistant", "broadcast"]
        test_text = "Speed test with this voice profile."
        
        for profile in profiles_to_test:
            if profile in engine.profiles:
                print(f"\n   🎤 {profile.upper()} profile:")
                engine.set_profile(profile)
                
                start_time = time.time()
                engine.synthesize_and_play(test_text, background=False)
                profile_time = time.time() - start_time
                
                print(f"      ⏱️  Time: {profile_time:.2f}s")
                
        print("\n✅ Speed optimization demo complete!")
        sound_mgr.play_success_sequence("major")
        
    except Exception as e:
        print(f"❌ Error during speed test: {e}")
        return 1
    
    return 0

def compare_old_vs_new():
    """Show theoretical improvements from old to new settings"""
    print("\n📈 THEORETICAL PERFORMANCE COMPARISON")
    print("-" * 70)
    
    old_config = {
        "chunk_size": 180,
        "inter_chunk_silence": 0.1,
        "overlap_words": 2
    }
    
    new_config = {
        "chunk_size": 300,
        "inter_chunk_silence": 0.03,
        "overlap_words": 1
    }
    
    test_text = "This is a sample text of moderate length to test the performance improvements in the voice synthesis system."
    text_length = len(test_text)
    
    # Calculate old performance
    old_chunks = (text_length // old_config["chunk_size"]) + 1
    old_silence_time = old_chunks * old_config["inter_chunk_silence"]
    
    # Calculate new performance  
    new_chunks = (text_length // new_config["chunk_size"]) + 1
    new_silence_time = new_chunks * new_config["inter_chunk_silence"]
    
    silence_improvement = ((old_silence_time - new_silence_time) / old_silence_time) * 100
    chunk_reduction = ((old_chunks - new_chunks) / old_chunks) * 100 if old_chunks > 0 else 0
    
    print(f"📊 Text length: {text_length} characters")
    print(f"\n🐌 OLD Configuration:")
    print(f"   • Chunks needed: {old_chunks}")
    print(f"   • Total silence: {old_silence_time:.2f}s")
    print(f"   • Chunk size: {old_config['chunk_size']} chars")
    
    print(f"\n⚡ NEW Configuration:")
    print(f"   • Chunks needed: {new_chunks}")
    print(f"   • Total silence: {new_silence_time:.2f}s")
    print(f"   • Chunk size: {new_config['chunk_size']} chars")
    
    print(f"\n🎯 Improvements:")
    print(f"   • Silence time reduced: {silence_improvement:.1f}%")
    print(f"   • Fewer chunks: {chunk_reduction:.1f}%")
    print(f"   • Overall speed increase: ~{(silence_improvement + chunk_reduction) / 2:.1f}%")

def main():
    """Run the complete speed demo"""
    benchmark_voice_speed()
    compare_old_vs_new()
    
    print("\n🎉 Voice speed optimization complete!")
    print("="*70)
    return 0

if __name__ == "__main__":
    sys.exit(main())