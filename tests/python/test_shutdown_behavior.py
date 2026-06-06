#!/usr/bin/env python3
"""
Test script for M1K3 CLI shutdown behavior
Tests various shutdown scenarios to ensure clean termination
"""

import os
import sys
import time
import signal
import subprocess
import threading
from pathlib import Path

# Add src directory to path for imports
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_basic_shutdown():
    """Test basic CLI startup and shutdown"""
    print("🧪 Testing basic CLI startup and shutdown...")
    
    try:
        # Start CLI in test mode
        process = subprocess.Popen([
            sys.executable, "cli.py", 
            "--no-voice", "--no-avatar", "--no-browser",
            "--query", "test message"
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        
        # Wait for completion or timeout
        try:
            stdout, stderr = process.communicate(timeout=10)
            print(f"✅ Basic shutdown test completed (exit code: {process.returncode})")
            return True
        except subprocess.TimeoutExpired:
            print("⚠️ Process didn't terminate within timeout, killing...")
            process.kill()
            process.communicate()
            return False
            
    except Exception as e:
        print(f"❌ Basic shutdown test failed: {e}")
        return False

def test_interrupt_shutdown():
    """Test Ctrl+C interrupt handling"""
    print("🧪 Testing Ctrl+C interrupt handling...")
    
    try:
        # Start CLI in interactive mode
        process = subprocess.Popen([
            sys.executable, "cli.py",
            "--no-voice", "--no-avatar", "--no-browser"
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, 
        preexec_fn=os.setsid)  # Create process group for clean termination
        
        # Give it time to start up
        time.sleep(2)
        
        # Send SIGINT (Ctrl+C)
        os.killpg(os.getpgid(process.pid), signal.SIGINT)
        
        # Wait for graceful shutdown
        try:
            stdout, stderr = process.communicate(timeout=5)
            print(f"✅ Interrupt shutdown test completed (exit code: {process.returncode})")
            return True
        except subprocess.TimeoutExpired:
            print("⚠️ Process didn't terminate gracefully, force killing...")
            os.killpg(os.getpgid(process.pid), signal.SIGKILL)
            process.communicate()
            return False
            
    except Exception as e:
        print(f"❌ Interrupt shutdown test failed: {e}")
        return False

def test_voice_engine_shutdown():
    """Test shutdown while voice engine is active"""
    print("🧪 Testing voice engine shutdown behavior...")
    
    try:
        # Start CLI with voice enabled but use test voice
        process = subprocess.Popen([
            sys.executable, "cli.py",
            "--no-avatar", "--no-browser", "--test-voice"
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        preexec_fn=os.setsid)
        
        # Give it time to start voice synthesis
        time.sleep(3)
        
        # Send interrupt during voice synthesis
        os.killpg(os.getpgid(process.pid), signal.SIGINT)
        
        # Wait for shutdown
        try:
            stdout, stderr = process.communicate(timeout=8)
            print(f"✅ Voice engine shutdown test completed (exit code: {process.returncode})")
            return True
        except subprocess.TimeoutExpired:
            print("⚠️ Voice engine didn't shutdown cleanly, force killing...")
            os.killpg(os.getpgid(process.pid), signal.SIGKILL)
            process.communicate()
            return False
            
    except Exception as e:
        print(f"❌ Voice engine shutdown test failed: {e}")
        return False

def test_double_interrupt():
    """Test double Ctrl+C for force shutdown"""
    print("🧪 Testing double interrupt for force shutdown...")
    
    try:
        # Start CLI in interactive mode
        process = subprocess.Popen([
            sys.executable, "cli.py",
            "--no-voice", "--no-avatar", "--no-browser"
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        preexec_fn=os.setsid)
        
        # Give it time to start
        time.sleep(1)
        
        # Send first SIGINT
        os.killpg(os.getpgid(process.pid), signal.SIGINT)
        time.sleep(0.5)  # Brief pause
        
        # Send second SIGINT for force shutdown
        os.killpg(os.getpgid(process.pid), signal.SIGINT)
        
        # Should terminate quickly
        try:
            stdout, stderr = process.communicate(timeout=3)
            print(f"✅ Double interrupt test completed (exit code: {process.returncode})")
            return True
        except subprocess.TimeoutExpired:
            print("⚠️ Force shutdown didn't work, manually killing...")
            os.killpg(os.getpgid(process.pid), signal.SIGKILL)
            process.communicate()
            return False
            
    except Exception as e:
        print(f"❌ Double interrupt test failed: {e}")
        return False

def test_stt_engine_shutdown():
    """Test shutdown while STT engine might be listening"""
    print("🧪 Testing STT engine shutdown behavior...")
    
    try:
        # Start CLI with voice-first mode to trigger STT
        process = subprocess.Popen([
            sys.executable, "cli.py",
            "--voice-first", "--no-avatar", "--no-browser", 
            "--stt-engine", "vosk"  # Use Vosk for consistent testing
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        preexec_fn=os.setsid)
        
        # Give it time to start STT
        time.sleep(3)
        
        # Send interrupt during potential STT listening
        os.killpg(os.getpgid(process.pid), signal.SIGINT)
        
        # Wait for shutdown
        try:
            stdout, stderr = process.communicate(timeout=6)
            print(f"✅ STT engine shutdown test completed (exit code: {process.returncode})")
            return True
        except subprocess.TimeoutExpired:
            print("⚠️ STT engine didn't shutdown cleanly, force killing...")
            os.killpg(os.getpgid(process.pid), signal.SIGKILL)
            process.communicate()
            return False
            
    except Exception as e:
        print(f"❌ STT engine shutdown test failed: {e}")
        return False

def main():
    """Run all shutdown behavior tests"""
    print("🚀 M1K3 CLI Shutdown Behavior Test Suite")
    print("=" * 50)
    
    tests = [
        ("Basic Shutdown", test_basic_shutdown),
        ("Interrupt Shutdown (Ctrl+C)", test_interrupt_shutdown),
        ("Voice Engine Shutdown", test_voice_engine_shutdown),
        ("Double Interrupt (Force)", test_double_interrupt),
        ("STT Engine Shutdown", test_stt_engine_shutdown),
    ]
    
    results = []
    
    for test_name, test_func in tests:
        print(f"\n📋 Running: {test_name}")
        try:
            result = test_func()
            results.append((test_name, result))
        except Exception as e:
            print(f"❌ Test {test_name} crashed: {e}")
            results.append((test_name, False))
        
        # Brief pause between tests
        time.sleep(1)
    
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
        print("🎉 All shutdown behavior tests passed!")
        return 0
    else:
        print(f"⚠️ {total - passed} tests failed - shutdown behavior needs attention")
        return 1

if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)