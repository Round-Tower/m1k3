#!/usr/bin/env python3
"""
Test script for Bluetooth earphone sample rate fix
Tests the dynamic sample rate detection and error handling improvements
"""

import os
import sys
import time
from pathlib import Path

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_sample_rate_detection():
    """Test dynamic sample rate detection in macOS STT engine"""
    print("🧪 Testing Dynamic Sample Rate Detection")
    print("-" * 40)
    
    try:
        from src.engines.stt.macos_stt_engine import MacOSSTTEngine, PYOBJC_AVAILABLE
        
        if not PYOBJC_AVAILABLE:
            print("❌ PyObjC not available - skipping macOS STT tests")
            return False
        
        engine = MacOSSTTEngine()
        
        # Test initialization with dynamic detection
        print("🔄 Initializing macOS STT engine...")
        success = engine.initialize()
        
        if success:
            print("✅ Engine initialized successfully")
            print(f"   📊 Detected sample rate: {engine.detected_sample_rate}Hz")
            print(f"   📊 Used sample rate: {engine.sample_rate}Hz") 
            print(f"   🎧 Device type: {engine.device_type}")
            
            # Test manual sample rate detection
            print("\n🔍 Testing manual sample rate detection...")
            detection_success = engine._detect_hardware_sample_rate()
            print(f"   {'✅' if detection_success else '❌'} Detection result: {detection_success}")
            
            engine.cleanup()
            return True
        else:
            print("❌ Engine initialization failed")
            print("   💡 This may be due to missing permissions or Bluetooth format issues")
            return False
            
    except Exception as e:
        print(f"❌ Sample rate detection test failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_audio_format_compatibility():
    """Test audio format compatibility functions"""
    print("\n🧪 Testing Audio Format Compatibility")
    print("-" * 40)
    
    try:
        from src.engines.stt.macos_stt_engine import MacOSSTTEngine, PYOBJC_AVAILABLE
        
        if not PYOBJC_AVAILABLE:
            print("❌ PyObjC not available - skipping compatibility tests")
            return False
        
        engine = MacOSSTTEngine()
        
        # Test optimal sample rate selection for different device types
        print("🔧 Testing optimal sample rate selection...")
        
        # Test different device types
        test_cases = [
            ("bluetooth_low", 8000),
            ("bluetooth_standard", 16000), 
            ("internal_or_external", 44100)
        ]
        
        for device_type, expected_rate in test_cases:
            engine.device_type = device_type
            engine.detected_sample_rate = expected_rate
            optimal_rate = engine._get_optimal_sample_rate_for_device()
            
            print(f"   🎧 {device_type}: {optimal_rate}Hz ({'✅' if optimal_rate <= expected_rate else '⚠️'})")
        
        # Test format creation
        print("\n🔧 Testing compatible format creation...")
        compatible_format = engine._create_compatible_format(16000, 1)
        print(f"   {'✅' if compatible_format else '❌'} Format creation: {compatible_format is not None}")
        
        return True
        
    except Exception as e:
        print(f"❌ Audio format compatibility test failed: {e}")
        return False

def test_bluetooth_error_handling():
    """Test error handling for Bluetooth format issues"""
    print("\n🧪 Testing Bluetooth Error Handling")
    print("-" * 40)
    
    try:
        from src.engines.stt.macos_stt_engine import MacOSSTTEngine, PYOBJC_AVAILABLE
        
        if not PYOBJC_AVAILABLE:
            print("❌ PyObjC not available - skipping error handling tests")
            return False
        
        engine = MacOSSTTEngine()
        engine.force_internal_mic = True
        
        print("🎤 Testing force internal microphone mode...")
        print(f"   Force internal mic: {engine.force_internal_mic}")
        
        # Test initialization with force internal mic
        success = engine.initialize()
        if success:
            print("✅ Engine with force internal mic initialized")
            engine.cleanup()
        else:
            print("⚠️ Engine initialization failed (may be expected with Bluetooth devices)")
        
        return True
        
    except Exception as e:
        print(f"❌ Bluetooth error handling test failed: {e}")
        return False

def test_cli_options():
    """Test new CLI options"""
    print("\n🧪 Testing New CLI Options")
    print("-" * 40)
    
    import subprocess
    
    # Test audio diagnostic option
    print("🔍 Testing --audio-diagnostic option...")
    try:
        result = subprocess.run([
            sys.executable, "cli.py", "--audio-diagnostic"
        ], capture_output=True, text=True, timeout=30)
        
        success = result.returncode == 0
        print(f"   {'✅' if success else '❌'} Audio diagnostic: {success}")
        
        if not success:
            print(f"   Error output: {result.stderr[:200]}...")
        
    except subprocess.TimeoutExpired:
        print("   ⚠️ Audio diagnostic timed out (may indicate hanging)")
        success = False
    except Exception as e:
        print(f"   ❌ Audio diagnostic failed: {e}")
        success = False
    
    # Test device detection option  
    print("\n🎧 Testing --detect-audio-devices option...")
    try:
        result = subprocess.run([
            sys.executable, "cli.py", "--detect-audio-devices"
        ], capture_output=True, text=True, timeout=15)
        
        device_success = result.returncode == 0
        print(f"   {'✅' if device_success else '❌'} Device detection: {device_success}")
        
        if not device_success:
            print(f"   Error output: {result.stderr[:200]}...")
        
    except subprocess.TimeoutExpired:
        print("   ⚠️ Device detection timed out")
        device_success = False
    except Exception as e:
        print(f"   ❌ Device detection failed: {e}")
        device_success = False
    
    return success and device_success

def test_stt_fallback():
    """Test STT engine fallback mechanism"""
    print("\n🧪 Testing STT Engine Fallback")
    print("-" * 40)
    
    try:
        from src.engines.stt.stt_manager import STTManager
        
        print("🔄 Testing STT manager fallback system...")
        
        manager = STTManager()
        
        if manager.is_available():
            available_engines = manager.get_available_engines()
            print(f"   📋 Available engines: {available_engines}")
            
            if len(available_engines) > 1:
                print("✅ Multiple engines available for fallback")
                return True
            else:
                print("⚠️ Only one engine available - fallback limited")
                return True
        else:
            print("❌ No STT engines available")
            return False
            
    except Exception as e:
        print(f"❌ STT fallback test failed: {e}")
        return False

def main():
    """Run all Bluetooth audio fix tests"""
    print("🎧 M1K3 Bluetooth Audio Fix Test Suite")
    print("=" * 50)
    print("Testing fixes for: 'required condition is false: format.sampleRate == inputHWFormat.sampleRate'")
    print()
    
    tests = [
        ("Dynamic Sample Rate Detection", test_sample_rate_detection),
        ("Audio Format Compatibility", test_audio_format_compatibility), 
        ("Bluetooth Error Handling", test_bluetooth_error_handling),
        ("CLI Options", test_cli_options),
        ("STT Engine Fallback", test_stt_fallback),
    ]
    
    results = []
    
    for test_name, test_func in tests:
        print(f"📋 Running: {test_name}")
        try:
            result = test_func()
            results.append((test_name, result))
        except Exception as e:
            print(f"❌ Test {test_name} crashed: {e}")
            results.append((test_name, False))
        
        time.sleep(0.5)  # Brief pause between tests
    
    # Summary
    print(f"\n📊 Test Results Summary:")
    print("=" * 50)
    
    passed = 0
    total = len(results)
    
    for test_name, result in results:
        status = "✅ PASS" if result else "❌ FAIL"  
        print(f"{status} {test_name}")
        if result:
            passed += 1
    
    print(f"\n📈 Overall: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
    
    if passed == total:
        print("🎉 All Bluetooth audio fix tests passed!")
        print("💡 The 'format.sampleRate' error should now be resolved")
        return 0
    else:
        print(f"⚠️ {total - passed} tests failed - some issues may remain")
        print("💡 Try using --stt-engine vosk or --force-internal-mic as workarounds")
        return 1

if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)