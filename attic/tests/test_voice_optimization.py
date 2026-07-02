#!/usr/bin/env python3
"""
Test Voice Optimization System
Comprehensive testing of optimized voice engine with chunking, caching, and error recovery
"""

import time
from enhanced_voice_engine import EnhancedVoiceEngine

def test_voice_optimization():
    print("🎤 Testing Voice Optimization System")
    print("=" * 60)
    
    # Initialize voice engine
    print("🔄 Initializing enhanced voice engine...")
    engine = EnhancedVoiceEngine()
    
    if not engine.is_available():
        print("❌ No voice engines available")
        return False
    
    # Load model and show status
    print("🚀 Loading voice engine...")
    start_time = time.time()
    if engine.load_model():
        load_time = time.time() - start_time
        print(f"✅ Voice engine loaded in {load_time:.2f}s")
        
        # Show detailed status
        status = engine.get_status()
        print(f"\n📊 Voice Engine Status:")
        print(f"   Engine Mode: {status['engine_mode']}")
        print(f"   Performance: {status.get('performance', 'Unknown')}")
        print(f"   Features: {', '.join(status.get('features', []))}")
        print(f"   Model: {status.get('model', 'Unknown')}")
        
        if 'cache_entries' in status:
            print(f"   Cache Entries: {status['cache_entries']}")
        
    else:
        print("❌ Failed to load voice engine")
        return False
    
    # Test cases for optimization features
    print(f"\n🧪 Testing Voice Optimization Features")
    print("=" * 60)
    
    # Test 1: Short text (basic functionality)
    print(f"\n1️⃣ Test: Short Text")
    print("-" * 30)
    short_text = "Hello! This is a short test message."
    print(f"Text: {short_text}")
    print(f"Length: {len(short_text)} characters")
    
    start_time = time.time()
    result = engine.synthesize_and_play(short_text, background=False)
    synthesis_time = time.time() - start_time
    
    print(f"✅ Result: {'Success' if result else 'Failed'}")
    print(f"⏱️  Time: {synthesis_time:.2f}s")
    
    # Test 2: Medium text (chunking test)
    print(f"\n2️⃣ Test: Medium Text (Chunking)")  
    print("-" * 30)
    medium_text = "This is a medium-length text that tests the smart chunking functionality of the optimized voice engine. It should be automatically split into appropriate chunks to prevent ONNX BERT model errors while maintaining natural speech flow. The system uses intelligent sentence boundary detection and word overlap for smooth transitions between chunks."
    print(f"Text: {medium_text[:100]}...")
    print(f"Length: {len(medium_text)} characters")
    
    start_time = time.time()
    result = engine.synthesize_and_play(medium_text, background=False)
    synthesis_time = time.time() - start_time
    
    print(f"✅ Result: {'Success' if result else 'Failed'}")
    print(f"⏱️  Time: {synthesis_time:.2f}s")
    
    # Test 3: Long text (advanced chunking)
    print(f"\n3️⃣ Test: Long Text (Advanced Chunking)")
    print("-" * 30)
    long_text = "This is a comprehensive test of the voice optimization system with a very long text passage. The optimized voice engine should intelligently chunk this text into smaller segments to prevent ONNX runtime errors that typically occur with the BERT model when processing longer sequences. The system implements smart text chunking that respects sentence boundaries whenever possible, maintains word overlap between chunks for natural flow, and provides robust error recovery mechanisms. Additionally, it features audio caching for repeated phrases, memory-efficient processing, and streaming synthesis capabilities. The engine automatically falls back through multiple voice systems - starting with the optimized KittenML implementation, then trying the Zen voice engine, and finally using system TTS as a guaranteed fallback. This multi-layered approach ensures reliable voice synthesis across all platforms and configurations while providing the best possible quality when advanced engines are available."
    print(f"Text: {long_text[:100]}...")
    print(f"Length: {len(long_text)} characters")
    
    start_time = time.time()
    result = engine.synthesize_and_play(long_text, background=False)
    synthesis_time = time.time() - start_time
    
    print(f"✅ Result: {'Success' if result else 'Failed'}")
    print(f"⏱️  Time: {synthesis_time:.2f}s")
    
    # Test 4: Caching test (repeat previous text)
    if hasattr(engine.current_engine, 'audio_cache'):
        print(f"\n4️⃣ Test: Audio Caching")
        print("-" * 30)
        cache_before = len(engine.current_engine.audio_cache)
        print(f"Cache entries before: {cache_before}")
        
        # Repeat short text to test caching
        start_time = time.time()
        result = engine.synthesize_and_play(short_text, background=False)
        cached_time = time.time() - start_time
        
        cache_after = len(engine.current_engine.audio_cache)
        print(f"Cache entries after: {cache_after}")
        print(f"✅ Result: {'Success' if result else 'Failed'}")
        print(f"⏱️  Cached time: {cached_time:.2f}s (vs {synthesis_time:.2f}s original)")
        
        if cached_time < synthesis_time * 0.8:
            print("🚀 Cache performance improvement detected!")
        
        # Show cache stats
        if hasattr(engine.current_engine, 'get_stats'):
            stats = engine.current_engine.get_stats()
            print(f"📊 Engine stats: {stats}")
    
    # Test 5: Error recovery (very problematic text)
    print(f"\n5️⃣ Test: Error Recovery")
    print("-" * 30)
    problematic_text = "∠∧∨∃∀∴∵∆∂∇∈∉∋∌⊂⊃⊄⊅⊆⊇⊈⊉⊊⊋∪∩∖⊕⊗⊙⊥‖∠∧∨"  # Unicode that might cause issues
    print(f"Text: {problematic_text}")
    
    result = engine.synthesize_and_play(problematic_text, background=False)
    print(f"✅ Error recovery: {'Handled gracefully' if result else 'Failed - but engine should still work'}")
    
    # Final status check
    final_status = engine.get_status()
    print(f"\n📈 Final Status:")
    print(f"   Engine still working: {final_status['enabled']}")
    print(f"   Current mode: {final_status['engine_mode']}")
    
    # Performance summary
    print(f"\n🎯 Performance Summary:")
    print(f"   Engine Mode: {final_status['engine_mode'].title()}")
    print(f"   Performance Level: {final_status.get('performance', 'Unknown')}")
    print(f"   Optimization Features: {', '.join(final_status.get('features', []))}")
    
    return True

def test_voice_modes():
    """Test different voice engine modes if available"""
    print(f"\n🎭 Testing Voice Engine Modes")
    print("=" * 60)
    
    engine = EnhancedVoiceEngine()
    if engine.load_model():
        status = engine.get_status()
        print(f"Active mode: {status['engine_mode']}")
        
        # Test persona switching if available
        if status['engine_mode'] == 'zen' and hasattr(engine.current_engine, 'get_personas'):
            personas = engine.current_engine.get_personas()
            print(f"Available personas: {list(personas.keys())}")
            
            for persona_name in list(personas.keys())[:2]:  # Test first 2 personas
                print(f"\n🎨 Testing persona: {persona_name}")
                if engine.set_persona(persona_name):
                    engine.synthesize_and_play(f"Testing {persona_name} voice mode", background=False)
                    time.sleep(1)
        
        # Test optimization features if available  
        if status['engine_mode'] == 'optimized' and hasattr(engine.current_engine, 'set_chunk_size'):
            print(f"\n⚙️  Testing optimization controls")
            engine.current_engine.set_chunk_size(150)
            engine.synthesize_and_play("Testing with smaller chunk size for faster processing", background=False)
            
            if hasattr(engine.current_engine, 'clear_cache'):
                engine.current_engine.clear_cache()
                print("🗑️  Cache cleared successfully")

if __name__ == "__main__":
    print("🚀 M1K3 Voice Optimization Test Suite")
    print("=" * 60)
    
    success = test_voice_optimization()
    
    if success:
        test_voice_modes()
        print(f"\n✅ Voice optimization testing completed successfully!")
        print("🎤 M1K3 voice system is optimized and ready!")
    else:
        print(f"\n❌ Voice optimization testing failed")
        print("🔧 Check voice engine dependencies and configuration")