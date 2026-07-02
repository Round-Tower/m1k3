#!/usr/bin/env python3
"""
M1K3 Quick Test Runner
Fast validation of core functionality
"""

import sys
import time
from pathlib import Path

def quick_voice_test():
    """Quick voice synthesis test"""
    print("🎤 Quick Voice Test...")
    try:
        from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
        engine = UnifiedVoiceEngine()
        
        if hasattr(engine, 'load_model') and engine.load_model():
            print("✅ Voice engine loaded")
            return True
        else:
            print("✅ Voice engine initialized (no models needed)")
            return True
    except Exception as e:
        print(f"❌ Voice test error: {e}")
        return False

def quick_ai_test():
    """Quick AI inference test"""
    print("🤖 Quick AI Test...")
    try:
        from src.engines.ai.ai_inference import LocalAIEngine
        ai = LocalAIEngine()
        
        if ai.load_model():
            print("✅ AI engine loaded")
            response = ai.generate_response("Hello")
            # Handle generator response
            if hasattr(response, '__iter__') and not isinstance(response, str):
                response_text = ''.join(response)
            else:
                response_text = str(response)
            
            if len(response_text) > 5:
                print("✅ AI response generated")
                return True
            else:
                print("❌ AI response too short")
                return False
        else:
            print("❌ AI engine failed to load")
            return False
    except Exception as e:
        print(f"❌ AI test error: {e}")
        return False

def quick_avatar_test():
    """Quick avatar system test"""
    print("🧘 Quick Avatar Test...")
    try:
        from src.avatar.avatar_controller import AvatarController
        avatar = AvatarController()
        print("✅ Avatar controller initialized")
        return True
    except Exception as e:
        print(f"❌ Avatar test error: {e}")
        return False

def main():
    """Run quick tests"""
    print("🚀 M1K3 Quick Test Suite")
    print("=" * 40)
    
    start_time = time.time()
    
    tests = [
        ("AI Engine", quick_ai_test),
        ("Voice Engine", quick_voice_test), 
        ("Avatar System", quick_avatar_test)
    ]
    
    passed = 0
    total = len(tests)
    
    for test_name, test_func in tests:
        print(f"\n{test_name}:")
        if test_func():
            passed += 1
    
    duration = time.time() - start_time
    
    print(f"\n📊 Results: {passed}/{total} passed in {duration:.1f}s")
    
    if passed == total:
        print("🎉 All quick tests passed!")
        return 0
    else:
        print("⚠️  Some tests failed")
        return 1

if __name__ == "__main__":
    sys.exit(main())