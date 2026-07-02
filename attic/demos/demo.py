#!/usr/bin/env python3
"""
M1K3 Demo Script
Showcases voice synthesis and dynamic greetings
"""

import time
from src.utils.performance.system_metrics import SystemMonitor, generate_dynamic_greeting
from src.engines.voice.simple_voice_engine import create_voice_engine

def run_demo():
    print("🎬 M1K3 Voice & Greeting Demo")
    print("=" * 40)
    
    # System metrics demo
    print("\n📊 System Metrics Collection:")
    monitor = SystemMonitor()
    metrics = monitor.collect_metrics()
    
    print(f"  Battery: {metrics.battery_percent}% ({metrics.battery_status()})")
    print(f"  CPU Usage: {metrics.cpu_usage}%")
    print(f"  Memory: {metrics.memory_percent}%")
    print(f"  Performance: {metrics.performance_status()}")
    
    # Dynamic greeting demo
    print("\n💬 Dynamic Greeting Generation:")
    for i in range(3):
        greeting = generate_dynamic_greeting(metrics)
        print(f"  {i+1}. \"{greeting}\"")
        time.sleep(0.5)
    
    # Voice synthesis demo
    print("\n🔊 Voice Synthesis Demo:")
    voice_engine = create_voice_engine()
    
    if voice_engine.is_available():
        if voice_engine.load_model():
            test_greeting = generate_dynamic_greeting(metrics)
            print(f"Speaking: \"{test_greeting}\"")
            voice_engine.synthesize_and_play(test_greeting, background=False)
            
            print("Speaking: \"M1K3 voice synthesis demonstration complete!\"")
            voice_engine.synthesize_and_play("M1K3 voice synthesis demonstration complete!", background=False)
        else:
            print("❌ Voice model loading failed")
    else:
        print("❌ Voice synthesis not available on this system")
    
    print("\n✅ Demo Complete!")
    print("Run 'python cli.py' to start the interactive M1K3 experience!")

if __name__ == "__main__":
    run_demo()