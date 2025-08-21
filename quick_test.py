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
        from enhanced_voice_engine import create_voice_engine
        engine = create_voice_engine()
        
        if engine.load_model():
            print("✅ Voice engine loaded")
            # Quick synthesis test
            engine.synthesize_and_play("Quick test complete.", background=False)
            return True
        else:
            print("❌ Voice engine failed to load")
            return False
    except Exception as e:
        print(f"❌ Voice test error: {e}")
        return False

def quick_ai_test():
    """Quick AI inference test"""
    print("🤖 Quick AI Test...")
    try:
        from ai_inference import LocalAIEngine
        ai = LocalAIEngine()
        
        if ai.load_model():
            print("✅ AI engine loaded")
            response = ai.generate_response("Hello")
            if len(response) > 5:
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
        from avatar_controller import AvatarController
        avatar = AvatarController()
        
        # Test emotion setting
        avatar.update_emotion("happy", 75)
        avatar.update_state("thinking")
        print("✅ Avatar system working")
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