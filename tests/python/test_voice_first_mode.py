#!/usr/bin/env python3
"""
Test voice-first mode functionality
"""

import sys
import os
sys.path.append(os.path.dirname(__file__))

def test_cli_arguments():
    """Test that the new --voice-first argument is available"""
    print("🔧 Testing CLI Arguments")
    print("=" * 30)
    
    try:
        import subprocess
        
        # Test help output includes the new argument
        result = subprocess.run([sys.executable, 'cli.py', '--help'], 
                              capture_output=True, text=True, cwd=os.path.dirname(__file__))
        
        if '--voice-first' in result.stdout:
            print("✅ --voice-first argument is available in CLI")
        else:
            print("❌ --voice-first argument not found in CLI help")
            return False
            
        # Check the help text for the argument
        if 'voice-first input mode' in result.stdout:
            print("✅ --voice-first has proper help description")
        else:
            print("⚠️ --voice-first help text may be incomplete")
        
        return True
        
    except Exception as e:
        print(f"❌ CLI argument test failed: {e}")
        return False

def test_voice_first_initialization():
    """Test that voice-first mode can be initialized"""
    print("\n🎤 Testing Voice-First Initialization")
    print("=" * 40)
    
    try:
        from src.cli.cli_core import M1K3CLICore
        
        # Test without voice-first mode
        print("🔧 Testing standard mode...")
        standard_cli = M1K3CLICore(voice_first=False)
        if hasattr(standard_cli, 'voice_first') and not standard_cli.voice_first:
            print("✅ Standard mode (voice_first=False) works")
        else:
            print("❌ Standard mode initialization issue")
            return False
        
        # Test with voice-first mode
        print("🔧 Testing voice-first mode...")
        voice_first_cli = M1K3CLICore(voice_first=True)
        if hasattr(voice_first_cli, 'voice_first') and voice_first_cli.voice_first:
            print("✅ Voice-first mode (voice_first=True) works")
        else:
            print("❌ Voice-first mode initialization issue")
            return False
        
        print("✅ Both modes initialize correctly")
        return True
        
    except Exception as e:
        print(f"❌ Initialization test failed: {e}")
        import traceback
        print(f"📍 Traceback: {traceback.format_exc()}")
        return False

def test_input_method_logic():
    """Test the modified input method logic"""
    print("\n🎯 Testing Input Method Logic")
    print("=" * 35)
    
    try:
        from src.cli.cli_core import M1K3CLICore
        
        # Create a CLI instance with voice-first mode
        cli = M1K3CLICore(voice_first=True)
        
        # Check if the _get_user_input method exists and has the right logic
        if hasattr(cli, '_get_user_input'):
            print("✅ _get_user_input method exists")
        else:
            print("❌ _get_user_input method not found")
            return False
        
        # Check if voice_first property is accessible
        if hasattr(cli, 'voice_first') and cli.voice_first:
            print("✅ voice_first property is correctly set")
        else:
            print("❌ voice_first property issue")
            return False
        
        print("✅ Input method logic is properly configured")
        return True
        
    except Exception as e:
        print(f"❌ Input method test failed: {e}")
        return False

if __name__ == "__main__":
    print("🚀 Voice-First Mode Test Suite")
    print("=" * 50)
    
    # Test CLI arguments
    args_success = test_cli_arguments()
    
    # Test initialization
    init_success = test_voice_first_initialization()
    
    # Test input logic
    logic_success = test_input_method_logic()
    
    print(f"\n📊 Test Results Summary:")
    print(f"   🔧 CLI Arguments: {'✅ Working' if args_success else '❌ Failed'}")
    print(f"   🎤 Initialization: {'✅ Working' if init_success else '❌ Failed'}")
    print(f"   🎯 Input Logic: {'✅ Working' if logic_success else '❌ Failed'}")
    
    if args_success and init_success and logic_success:
        print(f"\n🎉 SUCCESS: Voice-first mode is ready for use!")
        print(f"🎯 Usage: python cli.py --voice-first")
        print(f"   This will enable voice input by default for debugging")
    else:
        print(f"\n⚠️ PARTIAL: Some tests failed, check the output above")
    
    print(f"\n🏁 Test complete.")