#!/usr/bin/env python3
"""
Test M1K3 Transparency Integration
Quick test of transparency features with CLI integration
"""

import sys
import os

def test_transparency_import():
    """Test that transparency module imports correctly"""
    try:
        from model_transparency import ModelTransparencyEngine, TransparencyLevel, transparency_engine
        print("✅ Transparency module imports successfully")
        return True
    except ImportError as e:
        print(f"❌ Transparency module import failed: {e}")
        return False

def test_transparency_cli_integration():
    """Test transparency integration with CLI"""
    try:
        from cli import M1K3CLI, TRANSPARENCY_AVAILABLE
        
        if not TRANSPARENCY_AVAILABLE:
            print("❌ Transparency not available in CLI")
            return False
            
        # Test CLI initialization with different transparency levels
        levels = ["off", "basic", "detailed", "full", "debug"]
        
        for level in levels:
            try:
                cli = M1K3CLI(voice_enabled=False, transparency_level=level)
                print(f"✅ CLI initialization successful with transparency level: {level}")
            except Exception as e:
                print(f"❌ CLI initialization failed with level {level}: {e}")
                return False
        
        return True
        
    except ImportError as e:
        print(f"❌ CLI integration test failed: {e}")
        return False

def test_transparency_commands():
    """Test transparency command handling"""
    try:
        from cli import M1K3CLI, TRANSPARENCY_AVAILABLE
        
        if not TRANSPARENCY_AVAILABLE:
            print("❌ Transparency not available for command testing")
            return False
            
        cli = M1K3CLI(voice_enabled=False, transparency_level="detailed")
        
        # Test transparency command parsing
        test_commands = [
            "/transparency status",
            "/transparency basic", 
            "/transparency detailed",
            "/transparency summary"
        ]
        
        for cmd in test_commands:
            try:
                # Test that command parsing works
                cli.handle_transparency_command(cmd)
                print(f"✅ Command handled successfully: {cmd}")
            except Exception as e:
                print(f"❌ Command handling failed for {cmd}: {e}")
                return False
        
        return True
        
    except Exception as e:
        print(f"❌ Command testing failed: {e}")
        return False

def test_ai_inference_transparency():
    """Test transparency integration with AI inference"""
    try:
        from ai_inference import LocalAIEngine, TRANSPARENCY_AVAILABLE as AI_TRANSPARENCY
        
        if not AI_TRANSPARENCY:
            print("⚠️ Transparency not available in AI inference (expected if module not found)")
            return True  # This is OK, just means the import wasn't successful
            
        # Test AI engine initialization
        engine = LocalAIEngine()
        print("✅ AI engine with transparency integration initialized")
        
        return True
        
    except Exception as e:
        print(f"❌ AI inference transparency test failed: {e}")
        return False

def main():
    """Run all transparency integration tests"""
    print("🧪 Testing M1K3 Transparency Integration\n")
    
    tests = [
        ("Import Test", test_transparency_import),
        ("CLI Integration", test_transparency_cli_integration), 
        ("Command Handling", test_transparency_commands),
        ("AI Inference Integration", test_ai_inference_transparency)
    ]
    
    results = []
    
    for test_name, test_func in tests:
        print(f"🔍 Running {test_name}...")
        try:
            result = test_func()
            results.append(result)
            print(f"{'✅ PASSED' if result else '❌ FAILED'}: {test_name}\n")
        except Exception as e:
            print(f"❌ ERROR in {test_name}: {e}\n")
            results.append(False)
    
    # Summary
    passed = sum(results)
    total = len(results)
    
    print(f"📊 Test Results: {passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All transparency integration tests passed!")
        return 0
    else:
        print("⚠️  Some tests failed. Check output above for details.")
        return 1

if __name__ == "__main__":
    sys.exit(main())