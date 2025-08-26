#!/usr/bin/env python3
"""
Check microphone permissions and provide instructions
"""
import speech_recognition as sr
import time

def check_microphone_permissions():
    """Check if microphone permissions are granted"""
    print("🔍 Microphone Permissions Diagnostic")
    print("=" * 50)

    # Check if we can create microphone object
    try:
        m = sr.Microphone()
        print("✅ Microphone object created successfully")
    except Exception as e:
        print(f"❌ Cannot create microphone object: {e}")
        return False

    # Check if we can open microphone context
    try:
        with m as source:
            print("✅ Microphone context opened successfully")
            
            # Check if we can call methods that require microphone access
            try:
                # This should work if we have permission
                r = sr.Recognizer()
                print("🔧 Attempting to adjust for ambient noise (this requires mic permission)...")
                r.adjust_for_ambient_noise(source, duration=0.1)
                print(f"✅ Ambient noise adjustment successful! Energy threshold: {r.energy_threshold:.1f}")
                print("🎉 MICROPHONE PERMISSIONS ARE WORKING!")
                return True
                
            except Exception as e:
                print(f"❌ Cannot adjust for ambient noise: {e}")
                print("\n🔧 MICROPHONE PERMISSION ISSUE DETECTED")
                print("=" * 50)
                print("📋 To fix this on macOS:")
                print("1. Open System Preferences/Settings")
                print("2. Go to Privacy & Security → Microphone")
                print("3. Find 'Terminal' in the list")
                print("4. Enable microphone access for Terminal")
                print("5. If Terminal is not in the list, try running:")
                print("   sudo spctl --master-disable")
                print("   Then re-run this script")
                print("\n🔄 After granting permission, restart Terminal and try again.")
                return False
                
    except Exception as e:
        print(f"❌ Cannot open microphone context: {e}")
        return False

if __name__ == "__main__":
    success = check_microphone_permissions()
    print("\n" + "=" * 50)
    print("Microphone permissions check complete.")
    if not success:
        print("❌ Please fix microphone permissions and try again.")
        exit(1)
    else:
        print("✅ Microphone permissions are working correctly!")