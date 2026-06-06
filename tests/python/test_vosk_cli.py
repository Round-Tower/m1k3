#!/usr/bin/env python3
"""
Quick test to verify Vosk is now the primary STT engine
"""

import sys
import os
sys.path.append(os.path.dirname(__file__))

from src.engines.stt.stt_manager import STTManager

def test_stt_priority():
    print("🧪 Testing STT Engine Priority")
    print("=" * 40)
    
    # Create STT manager (should use auto-detection)
    manager = STTManager()
    
    print(f"✅ Current engine: {manager.current_engine_name}")
    print(f"🎯 Available engines: {list(manager.engines.keys())}")
    
    # Test engine availability
    if manager.current_engine:
        print(f"🔍 Engine available: {manager.current_engine.is_available()}")
        print(f"📋 Engine type: {type(manager.current_engine).__name__}")
    else:
        print("❌ No current engine available")
    
    return manager.current_engine_name == "vosk"

if __name__ == "__main__":
    success = test_stt_priority()
    if success:
        print("\n✅ SUCCESS: Vosk is now the primary STT engine!")
    else:
        print("\n⚠️  WARNING: Vosk is not the primary engine")