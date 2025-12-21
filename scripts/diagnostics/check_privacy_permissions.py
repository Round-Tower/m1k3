#!/usr/bin/env python3
"""
Check macOS privacy permissions for microphone access
"""

import subprocess
import os

def check_microphone_permissions():
    """Check if current app has microphone permissions"""
    print("🔐 macOS Privacy Permissions Check")
    print("=" * 40)
    
    try:
        # Get the current executable/terminal that's running Python
        current_app = subprocess.check_output(['ps', '-p', str(os.getppid()), '-o', 'comm=']).decode().strip()
        print(f"🔍 Running from: {current_app}")
        
        # Check microphone permissions using tccutil (requires elevated permissions)
        print("\n📋 Checking microphone access permissions...")
        
        # Alternative method - try to get privacy database info
        try:
            result = subprocess.run([
                'sqlite3', 
                os.path.expanduser('~/Library/Application Support/com.apple.TCC/TCC.db'),
                "SELECT service, client, auth_value FROM access WHERE service='kTCCServiceMicrophone';"
            ], capture_output=True, text=True, check=False)
            
            if result.returncode == 0 and result.stdout:
                print("✅ Found microphone permissions in TCC database:")
                lines = result.stdout.strip().split('\n')
                for line in lines:
                    if line:
                        service, client, auth = line.split('|')
                        status = "✅ Granted" if auth == '1' else "❌ Denied" if auth == '0' else "❓ Unknown"
                        print(f"   {client}: {status}")
            else:
                print("⚠️  Could not access TCC database (normal for security)")
                
        except Exception as e:
            print(f"⚠️  TCC check failed: {e}")
        
        # Suggest manual check
        print("\n💡 Manual Check Required:")
        print("   1. Open System Preferences/System Settings")
        print("   2. Go to Privacy & Security > Microphone")
        print("   3. Make sure 'Terminal' or your IDE has microphone access")
        print("   4. If not listed, try running this script and it should prompt for permission")
        
        return True
        
    except Exception as e:
        print(f"❌ Permission check failed: {e}")
        return False

def test_microphone_with_permission_prompt():
    """Attempt microphone access that should trigger permission prompt"""
    print("\n🎤 Attempting Microphone Access (may trigger permission prompt)")
    print("=" * 70)
    
    try:
        import sounddevice as sd
        import numpy as np
        
        print("📢 This should trigger a microphone permission dialog if needed...")
        print("🔄 Recording 1 second of audio...")
        
        # This should trigger macOS permission dialog
        audio = sd.rec(44100, samplerate=44100, channels=1, dtype=np.float32)
        sd.wait()
        
        # Check if we got real data
        rms = np.sqrt(np.mean(audio**2))
        if rms > 0.00001:
            print(f"✅ SUCCESS: Got audio data (RMS: {rms:.6f})")
            return True
        else:
            print(f"❌ FAILURE: No audio data (RMS: {rms:.6f})")
            print("💡 Permission may have been denied or microphone is muted")
            return False
            
    except Exception as e:
        print(f"❌ Microphone test failed: {e}")
        return False

if __name__ == "__main__":
    print("🔒 macOS Microphone Permission Diagnostics")
    print("=" * 60)
    
    # Check current permissions
    check_microphone_permissions()
    
    # Test with permission prompt
    success = test_microphone_with_permission_prompt()
    
    if not success:
        print("\n📝 Troubleshooting Steps:")
        print("1. Check System Preferences > Privacy & Security > Microphone")
        print("2. Add Terminal (or your IDE) to the allowed apps")
        print("3. Make sure microphone volume is turned up")
        print("4. Try speaking louder or closer to the microphone")
        print("5. Restart Terminal/IDE after granting permissions")
    
    print("\n🏁 Diagnostic complete.")