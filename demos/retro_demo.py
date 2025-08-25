#!/usr/bin/env python3
"""
M1K3 PlayStation 1 Voice Demo
Showcases all retro voice features and character presets
"""

import time
import warnings
import os

# Suppress warnings for cleaner demo
warnings.filterwarnings("ignore")
os.environ['PYTHONWARNINGS'] = 'ignore'

from src.utils.performance.system_metrics import SystemMonitor, generate_dynamic_greeting
from src.engines.voice.hybrid_voice_engine import create_voice_engine

def retro_demo():
    print("🎮 M1K3 PlayStation 1 Voice Demo")
    print("=" * 50)
    
    # System greeting
    print("\n📊 System Status Check:")
    monitor = SystemMonitor()
    metrics = monitor.collect_metrics()
    
    print(f"  Battery: {metrics.battery_percent}% ({metrics.battery_status()})")
    print(f"  Performance: {metrics.performance_status()}")
    
    # Load voice engine
    print("\n🎮 Loading Retro Voice Engine...")
    voice_engine = create_voice_engine()
    
    if not voice_engine.is_available():
        print("❌ Voice synthesis not available")
        return
        
    if not voice_engine.load_model():
        print("❌ Failed to load voice engine")
        return
    
    # Dynamic greeting
    greeting = generate_dynamic_greeting(metrics)
    print(f"\n💬 Dynamic Greeting: \"{greeting}\"")
    
    if voice_engine.voice_enabled:
        print("🔊 Speaking greeting...")
        voice_engine.synthesize_and_play(greeting, background=False)
        time.sleep(1)
    
    # Character preset demo
    print("\n🎭 PlayStation 1 Character Voice Presets:")
    
    character_demos = [
        ("m1k3", "Greetings, user. M1K3 system online and ready for commands."),
        ("hero", "Ready for action! Let's save the world together, ally."),
        ("narrator", "Welcome, traveler, to the world of retro gaming adventures."),
        ("villain", "Well, well, well... your puny systems are no match for my power."),
    ]
    
    for character, demo_text in character_demos:
        print(f"\n🎵 {character.upper()} Character:")
        print(f"   \"{demo_text}\"")
        
        if voice_engine.voice_enabled:
            voice_engine.set_character_preset(character)
            voice_engine.synthesize_and_play(demo_text, background=False)
            time.sleep(1.5)
    
    # Voice mode switching demo
    print("\n🔄 Voice Mode Switching Demo:")
    
    if hasattr(voice_engine, 'toggle_retro_mode'):
        print("   Switching to classic voice mode...")
        voice_engine.toggle_retro_mode()
        if voice_engine.voice_enabled:
            voice_engine.synthesize_and_play("Classic voice mode activated.", background=False)
            time.sleep(1)
        
        print("   Switching back to retro voice mode...")
        voice_engine.toggle_retro_mode()
        if voice_engine.voice_enabled:
            voice_engine.synthesize_and_play("PlayStation 1 retro voice mode reactivated.", background=False)
            time.sleep(1)
    
    # Gaming commands demo
    print("\n🎮 Gaming Command Responses:")
    
    gaming_commands = [
        "System ready",
        "Loading mission parameters",
        "Operation failed", 
        "Hero ready for action",
        "The dark lord awakens"
    ]
    
    for i, command in enumerate(gaming_commands):
        character = ["m1k3", "m1k3", "m1k3", "hero", "villain"][i]
        print(f"   {character.upper()}: \"{command}\"")
        
        if voice_engine.voice_enabled:
            voice_engine.set_character_preset(character)
            voice_engine.synthesize_and_play(command, background=False)
            time.sleep(1)
    
    # Final status
    print(f"\n📊 Final Voice Engine Status:")
    status = voice_engine.get_status()
    print(f"   Engine: {status['engine']}")
    print(f"   Mode: {'Retro' if status['retro_mode'] else 'Classic'}")
    print(f"   Character: {status['character_preset']}")
    print(f"   Model: {status['model']}")
    
    if voice_engine.voice_enabled:
        voice_engine.set_character_preset("m1k3")
        voice_engine.synthesize_and_play("M1K3 PlayStation 1 voice demonstration complete! Ready for interactive mode.", background=False)
    
    print("\n✅ Demo Complete!")
    print("🎮 Run 'python m1k3.py' to start interactive M1K3 with retro voice!")
    print("🎭 Try commands like 'character hero' or 'character villain' in interactive mode!")

if __name__ == "__main__":
    retro_demo()