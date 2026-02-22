#!/usr/bin/env python3
"""Direct benchmark of CPU vs GPU Kokoro TTS"""
import os
import sys
sys.path.insert(0, '.')

from src.tts.controllers.kokoro_tts_manager import KokoroTTSManager

TEST_TEXT = "This is a performance benchmark comparing CPU versus GPU acceleration."

print("🔬 Kokoro TTS Performance Benchmark")
print("====================================\n")

# Test 1: CPU-only
print("📊 Test 1: CPU-only (forcing CPUExecutionProvider)")
print("-" * 50)
os.environ["ONNX_PROVIDER"] = "CPUExecutionProvider"
kokoro_cpu = KokoroTTSManager()
kokoro_cpu.set_voice('bm_daniel')
if kokoro_cpu.load_model():
    audio = kokoro_cpu.generate(TEST_TEXT)
    print()
else:
    print("❌ Failed to load CPU model")

# Test 2: GPU (CoreML)
print("📊 Test 2: GPU (CoreML acceleration)")
print("-" * 50)
os.environ["ONNX_PROVIDER"] = "CoreMLExecutionProvider"
kokoro_gpu = KokoroTTSManager()
kokoro_gpu._instance = None  # Force new instance
kokoro_gpu = KokoroTTSManager()
kokoro_gpu.set_voice('bm_daniel')
if kokoro_gpu.load_model():
    audio = kokoro_gpu.generate(TEST_TEXT)
    print()
else:
    print("❌ Failed to load GPU model")

print("\n✅ Benchmark complete!")
print("Lower RTF = faster (RTF < 1.0 = faster than real-time)")
