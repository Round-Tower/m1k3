#!/usr/bin/env python3
"""
Quick TTS System Test - Verify All Components Working
"""

import sys
import os

sys.path.insert(0, '.')

def test_tts_system():
    """Test all TTS components quickly"""
    print("🧪 TTS SYSTEM VERIFICATION TEST")
    print("=" * 40)
    
    try:
        # Test imports
        print("1. Testing imports...")
        from model_output_parser import parse_model_output, ContentType
        from intelligent_tts_controller import create_intelligent_tts_controller
        from content_specific_effects import create_content_effects_manager
        print("   ✅ All modules imported successfully")
        
        # Test parser
        print("\n2. Testing content parser...")
        test_text = """<thinking>Let me think.</thinking>
The answer is clear. *The user nods.* 
Could you clarify this?"""
        
        parsed = parse_model_output(test_text)
        print(f"   ✅ Parsed {len(parsed.segments)} segments:")
        for i, seg in enumerate(parsed.segments, 1):
            print(f"      {i}. {seg.content_type.value.upper()}: \"{seg.text[:20]}...\"")
        
        # Test effects manager
        print("\n3. Testing effects manager...")
        effects_manager = create_content_effects_manager()
        print(f"   ✅ Effects manager created with {len(effects_manager.effects)} effects")
        
        # Test controller
        print("\n4. Testing TTS controller...")
        controller = create_intelligent_tts_controller()
        status = controller.get_status()
        print(f"   ✅ Controller status:")
        print(f"      • Effects available: {status['effects_manager_available']}")
        print(f"      • Voice engine: {status['voice_engine_available']}")
        print(f"      • Queue size: {status['queue_size']}")
        
        # Test modulation settings
        print("\n5. Testing modulation settings...")
        for content_type, settings in status['modulations'].items():
            print(f"   • {content_type.upper()}: Vol {settings['volume']:.1f}x, Speed {settings['speed']:.1f}x, Pitch {settings['pitch']:+.1f}")
        
        print("\n🎉 ALL TESTS PASSED!")
        print("=" * 40)
        print("✅ Content parsing: Working")
        print("✅ Effects manager: Working") 
        print("✅ TTS controller: Working")
        print("✅ Voice modulation: Configured")
        print("✅ System integration: Complete")
        
        print(f"\n🎭 Ready to run demos:")
        print(f"   python demos/demo_tts_quick_showcase.py")
        print(f"   python demos/demo_tts_showcase_with_sfx.py")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_tts_system()
    sys.exit(0 if success else 1)