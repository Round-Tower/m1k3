#!/usr/bin/env python3
"""
Test script to verify all M1K3 interface connections
"""

import sys
import traceback

def test_interface_connections():
    """Test all interface imports and basic functionality"""
    
    print("🧪 Testing M1K3 Interface Connections")
    print("="*50)
    
    results = {}
    
    # Test 1: Classic CLI Import
    print("1. Testing Classic CLI import...")
    try:
        from cli import main as cli_main
        print("   ✅ cli.py main() import successful")
        results["classic_cli"] = True
    except Exception as e:
        print(f"   ❌ cli.py import failed: {e}")
        results["classic_cli"] = False
    
    # Test 2: Textual TUI Import
    print("\n2. Testing Textual TUI import...")
    try:
        from src.cli.m1k3_tui import M1K3TUIApp
        print("   ✅ M1K3TUIApp import successful")
        results["textual_tui"] = True
    except Exception as e:
        print(f"   ❌ Textual TUI import failed: {e}")
        print(f"   🔍 Error details: {traceback.format_exc()}")
        results["textual_tui"] = False
    
    # Test 3: Rich TUI Import  
    print("\n3. Testing Rich TUI import...")
    try:
        from src.cli.m1k3_rich_tui import launch_rich_tui
        print("   ✅ launch_rich_tui import successful")
        results["rich_tui"] = True
    except Exception as e:
        print(f"   ❌ Rich TUI import failed: {e}")
        print(f"   🔍 Error details: {traceback.format_exc()}")
        results["rich_tui"] = False
    
    # Test 4: Main launcher structure
    print("\n4. Testing main launcher...")
    try:
        from m1k3 import main, launch_classic_cli
        print("   ✅ Main launcher functions import successful")
        results["main_launcher"] = True
    except Exception as e:
        print(f"   ❌ Main launcher import failed: {e}")
        results["main_launcher"] = False
    
    # Summary
    print("\n📊 Summary:")
    print(f"   Classic CLI: {'✅' if results.get('classic_cli') else '❌'}")
    print(f"   Textual TUI: {'✅' if results.get('textual_tui') else '❌'}")
    print(f"   Rich TUI: {'✅' if results.get('rich_tui') else '❌'}")
    print(f"   Main Launcher: {'✅' if results.get('main_launcher') else '❌'}")
    
    all_working = all(results.values())
    
    if all_working:
        print("\n🎉 All interfaces connected successfully!")
        print("\n📋 Available launch modes:")
        print("   python m1k3.py                 # Classic CLI (default)")
        print("   python m1k3.py --tui           # Textual TUI")
        print("   python m1k3.py --fullscreen    # Rich TUI")
        print("   python m1k3.py --no-voice      # Any mode without voice")
        print("   python m1k3.py --no-avatar     # Any mode without avatar server")
        print("   python m1k3.py --rag           # Any mode with RAG enabled")
    else:
        failed = [k for k, v in results.items() if not v]
        print(f"\n⚠️  Some interfaces failed: {', '.join(failed)}")
        print("   Check dependencies and import paths")
    
    return all_working

if __name__ == "__main__":
    success = test_interface_connections()
    sys.exit(0 if success else 1)