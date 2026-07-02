#!/usr/bin/env python3
"""
Comprehensive STT System Diagnostics
Tests all engines individually and provides detailed feedback
"""
import sys
sys.path.insert(0, 'src')

from src.engines.stt.stt_manager import STTManager

def test_individual_engines():
    """Test each STT engine individually"""
    print("🔍 STT System Diagnostics")
    print("=" * 60)
    
    # Initialize STT Manager
    stt_manager = STTManager()
    
    if not stt_manager.engines:
        print("❌ No STT engines available")
        return False
    
    print(f"📊 Found {len(stt_manager.engines)} STT engines:")
    for name in stt_manager.engines.keys():
        print(f"  - {name}")
    
    print(f"\n🎯 Current primary engine: {stt_manager.current_engine_name}")
    
    # Test each engine individually
    all_results = {}
    
    for engine_name, engine in stt_manager.engines.items():
        print(f"\n{'=' * 40}")
        print(f"🧪 Testing: {engine_name}")
        print(f"{'=' * 40}")
        
        try:
            if not engine.is_available():
                print(f"❌ Engine {engine_name} not available")
                all_results[engine_name] = {"available": False, "error": "Not available"}
                continue
            
            print(f"✅ Engine {engine_name} is available")
            print(f"📢 SPEAK NOW - testing {engine_name} for 10 seconds...")
            
            result = engine.listen_once(timeout=10.0, phrase_timeout=3.0)
            
            if result:
                print(f"🎉 SUCCESS with {engine_name}!")
                print(f"   Text: '{result.text}'")
                print(f"   Confidence: {result.confidence:.2f}")
                print(f"   Duration: {result.duration:.2f}s")
                print(f"   Engine: {result.engine}")
                all_results[engine_name] = {
                    "available": True,
                    "success": True,
                    "text": result.text,
                    "confidence": result.confidence,
                    "duration": result.duration
                }
            else:
                print(f"⚠️ No result from {engine_name}")
                all_results[engine_name] = {
                    "available": True,
                    "success": False,
                    "error": "No speech detected"
                }
                
        except Exception as e:
            print(f"❌ Error testing {engine_name}: {e}")
            all_results[engine_name] = {
                "available": True,
                "success": False,
                "error": str(e)
            }
    
    # Summary
    print(f"\n{'=' * 60}")
    print("📋 DIAGNOSTIC SUMMARY")
    print(f"{'=' * 60}")
    
    successful_engines = []
    failed_engines = []
    
    for engine_name, result in all_results.items():
        if result.get("success"):
            successful_engines.append(engine_name)
            print(f"✅ {engine_name}: SUCCESS - '{result['text']}' ({result['confidence']:.2f})")
        else:
            failed_engines.append(engine_name)
            error = result.get("error", "Unknown error")
            print(f"❌ {engine_name}: FAILED - {error}")
    
    print(f"\n📊 Results: {len(successful_engines)} successful, {len(failed_engines)} failed")
    
    if successful_engines:
        print(f"🎉 Working engines: {', '.join(successful_engines)}")
        print("💡 Consider switching to a working engine if having issues")
    else:
        print("⚠️ No engines detected speech successfully")
        print("💡 This might indicate:")
        print("   - Microphone hardware issues")
        print("   - Audio input levels too low")
        print("   - System permissions problems")
        print("   - Need to speak more clearly/loudly")
    
    return len(successful_engines) > 0

def test_stt_manager_fallback():
    """Test STT Manager automatic fallback functionality"""
    print(f"\n{'=' * 40}")
    print("🔄 Testing STT Manager Fallback")
    print(f"{'=' * 40}")
    
    stt_manager = STTManager()
    
    print(f"🎯 Primary engine: {stt_manager.current_engine_name}")
    print("📢 SPEAK NOW - testing automatic fallback...")
    
    result = stt_manager.listen_once(timeout=12.0, phrase_timeout=3.0)
    
    if result:
        print(f"🎉 SUCCESS!")
        print(f"   Final engine used: {result.engine}")
        print(f"   Text: '{result.text}'")
        print(f"   Confidence: {result.confidence:.2f}")
        return True
    else:
        print("⚠️ All engines failed")
        return False

if __name__ == "__main__":
    print("🧪 Starting comprehensive STT diagnostics...")
    print("💡 Make sure your microphone is working and speak clearly when prompted")
    
    # Test individual engines
    individual_success = test_individual_engines()
    
    # Test fallback system
    fallback_success = test_stt_manager_fallback()
    
    print(f"\n{'=' * 60}")
    print("🏁 FINAL RESULTS")
    print(f"{'=' * 60}")
    
    if individual_success or fallback_success:
        print("✅ STT system is working!")
        if not individual_success and fallback_success:
            print("💡 Primary engines failed but fallback system worked")
    else:
        print("❌ STT system has issues")
        print("💡 Troubleshooting steps:")
        print("   1. Check microphone permissions")
        print("   2. Test microphone with other apps")
        print("   3. Adjust microphone input levels")
        print("   4. Speak clearly and loudly during tests")
    
    exit(0 if (individual_success or fallback_success) else 1)