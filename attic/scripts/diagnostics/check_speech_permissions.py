#!/usr/bin/env python3
"""
Check current speech recognition permissions status
"""
import sys
sys.path.insert(0, 'src')

try:
    from Speech import SFSpeechRecognizer
    AVAILABLE = True
except ImportError:
    AVAILABLE = False

def check_speech_permissions():
    """Check current speech recognition permission status"""
    print("🔍 Speech Recognition Permissions Check")
    print("=" * 50)
    
    if not AVAILABLE:
        print("❌ Speech framework not available")
        return False
    
    try:
        # Check current authorization status
        auth_status = SFSpeechRecognizer.authorizationStatus()
        
        status_map = {
            0: ("NotDetermined", "⚠️", "Permission not yet requested"),
            1: ("Denied", "❌", "Permission explicitly denied"),
            2: ("Restricted", "❌", "Permission restricted by system policy"),
            3: ("Authorized", "✅", "Permission granted - ready to use!")
        }
        
        status_name, emoji, description = status_map.get(auth_status, ("Unknown", "❓", f"Unknown status: {auth_status}"))
        
        print(f"Status Code: {auth_status}")
        print(f"Status Name: {status_name}")
        print(f"Description: {emoji} {description}")
        
        if auth_status == 3:
            print("\n🎉 Speech recognition is ready to use!")
            return True
        elif auth_status == 0:
            print("\n💡 To request permission, run the CLI or test scripts")
            print("   python cli.py")
            print("   python test_macos_stt.py")
            return False
        else:
            print(f"\n💡 To fix permissions:")
            print("   1. Open System Preferences/Settings")
            print("   2. Go to Privacy & Security → Speech Recognition")  
            print("   3. Enable access for Terminal or Python")
            return False
            
    except Exception as e:
        print(f"❌ Error checking permissions: {e}")
        return False

if __name__ == "__main__":
    success = check_speech_permissions()
    print("\n" + "=" * 50)
    exit(0 if success else 1)