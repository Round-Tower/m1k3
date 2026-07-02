#!/usr/bin/env python3
"""
Enhanced CLI with Sound Effects Integration
M1K3 CLI with contextual sound effects and startup sequences
"""

import time
import random
from sound_manager import SoundManager, ContextualSoundManager
from src.engines.voice.turbo_voice_engine import TurboVoiceEngineWithEffects
from wireless_scanner import WirelessScanner, AdvancedWirelessCapabilities

class EnhancedM1K3CLI:
    """M1K3 CLI with advanced sound, voice, and wireless capabilities"""
    
    def __init__(self):
        # Initialize enhanced systems
        self.sound_manager = SoundManager()
        self.voice_engine = TurboVoiceEngineWithEffects()
        self.wireless_scanner = WirelessScanner()
        self.contextual_sound = ContextualSoundManager(self.sound_manager)
        
        # System state
        self.startup_complete = False
        self.session_stats = {
            "start_time": time.time(),
            "interactions": 0,
            "wifi_scans": 0,
            "bluetooth_scans": 0,
            "sounds_played": 0
        }
    
    def startup_sequence(self, style: str = "random") -> bool:
        """Enhanced startup with sound effects"""
        print("🚀 M1K3 Enhanced Startup Sequence")
        print("=" * 50)
        
        # Play startup sound
        startup_sounds = {
            "retro": "the-sound-of-dial-up-internet-6240",
            "gaming": "cursed-psx-startup-sound", 
            "classic": "psx",
            "random": None  # Will choose randomly
        }
        
        startup_sound = startup_sounds.get(style, "random")
        if startup_sound:
            print(f"🔊 Playing {style} startup sound...")
            self.sound_manager.play_sound(startup_sound, "startup", background=False)
        else:
            print("🔊 Playing random startup sound...")
            self.sound_manager.play_startup_sequence("random")
        
        # Initialize voice engine
        print("🎤 Initializing Turbo Voice Engine...")
        voice_loaded = self.voice_engine.load_model()
        
        if voice_loaded:
            self.sound_manager.play_contextual_sound("success")
            self.voice_engine.synthesize_and_play("M1K3 voice system ready", background=False)
        
        # Quick wireless scan
        print("📡 Performing environmental scan...")
        wifi_count = len(self.wireless_scanner.scan_wifi_networks())
        bluetooth_count = len(self.wireless_scanner.scan_bluetooth_devices(scan_duration=3))
        
        print(f"   WiFi networks: {wifi_count}")
        print(f"   Bluetooth devices: {bluetooth_count}")
        
        if wifi_count > 0:
            self.sound_manager.play_sound("wave_sfx", background=True)
        
        # Completion
        self.sound_manager.play_success_sequence("major")
        self.startup_complete = True
        
        return True
    
    def enhanced_interaction(self, user_input: str, ai_response: str):
        """Enhanced interaction with contextual sounds and wireless awareness"""
        self.session_stats["interactions"] += 1
        
        # Play contextual sound for interaction
        self.contextual_sound.play_response_sound(ai_response, user_input)
        
        # Special commands with sound effects
        if "scan wifi" in user_input.lower():
            self.perform_wifi_scan()
        elif "scan bluetooth" in user_input.lower():
            self.perform_bluetooth_scan()
        elif "play sound" in user_input.lower():
            self.interactive_sound_test()
    
    def perform_wifi_scan(self) -> Dict[str, Any]:
        """Enhanced WiFi scanning with sound effects"""
        print("📡 Scanning WiFi environment...")
        self.sound_manager.play_sound("wave_alert", background=True)
        
        start_time = time.time()
        networks = self.wireless_scanner.scan_wifi_networks(include_hidden=True)
        scan_time = time.time() - start_time
        
        self.session_stats["wifi_scans"] += 1
        
        # Analyze results
        device_types = AdvancedWirelessCapabilities.detect_device_types(self.wireless_scanner)
        security_analysis = AdvancedWirelessCapabilities.security_analysis(self.wireless_scanner)
        
        # Sound effects based on results
        if len(networks) > 20:
            self.sound_manager.play_sound("crowd_cheer_sfx", background=True)
        elif len(networks) > 10:
            self.sound_manager.play_sound("victory_confetti", background=True)
        else:
            self.sound_manager.play_sound("complete", background=True)
        
        # Announce results
        summary = f"Found {len(networks)} WiFi networks in {scan_time:.1f} seconds"
        self.voice_engine.synthesize_and_play(summary, background=False)
        
        return {
            "networks": networks,
            "device_types": device_types,
            "security_analysis": security_analysis,
            "scan_time": scan_time
        }
    
    def perform_bluetooth_scan(self) -> Dict[str, Any]:
        """Enhanced Bluetooth scanning with effects"""
        print("📱 Scanning Bluetooth environment...")
        self.sound_manager.play_sound("cast_a_spell_sound", background=True)
        
        devices = self.wireless_scanner.scan_bluetooth_devices(scan_duration=10)
        self.session_stats["bluetooth_scans"] += 1
        
        if devices:
            self.sound_manager.play_sound("ding_ding", background=True)
            summary = f"Found {len(devices)} Bluetooth devices"
        else:
            self.sound_manager.play_sound("sheep_baah", background=True)  # Quirky "nothing found" sound
            summary = "No Bluetooth devices detected"
        
        self.voice_engine.synthesize_and_play(summary, background=False)
        
        return {"devices": devices}
    
    def interactive_sound_test(self):
        """Interactive sound effect testing"""
        print("🎵 Interactive Sound Test Mode")
        print("=" * 40)
        
        # Show available sound categories
        inventory = self.sound_manager.get_sound_inventory()
        
        for category, sounds in inventory.items():
            if sounds:
                print(f"\\n🎵 {category.title()} ({len(sounds)} sounds):")
                for sound in sounds[:5]:  # Show first 5
                    print(f"   • {sound}")
                if len(sounds) > 5:
                    print(f"   ... and {len(sounds) - 5} more")
        
        # Play random samples
        print(f"\\n🎲 Playing random sound samples...")
        
        categories_to_demo = ["startup", "success", "special", "interaction"]
        for category in categories_to_demo:
            if category in inventory and inventory[category]:
                sound = random.choice(inventory[category])
                print(f"   Playing {category}: {sound}")
                self.sound_manager.play_sound(sound, category, background=False)
                time.sleep(1)
    
    def get_enhanced_status(self) -> Dict[str, Any]:
        """Get comprehensive system status"""
        return {
            "startup_complete": self.startup_complete,
            "voice_engine": self.voice_engine.get_status(),
            "sound_system": self.sound_manager.test_sound_system(),
            "wireless_scanner": self.wireless_scanner.get_scanner_stats(),
            "session_stats": self.session_stats.copy(),
            "capabilities": [
                "Ultra-fast voice synthesis",
                "Contextual sound effects", 
                "WiFi network scanning",
                "Bluetooth device discovery",
                "Environmental analysis",
                "Sound profile switching",
                "Advanced model management"
            ]
        }

def main():
    """Demo the enhanced CLI capabilities"""
    cli = EnhancedM1K3CLI()
    
    # Demo startup
    cli.startup_sequence("gaming")
    
    time.sleep(2)
    
    # Demo interactions
    cli.enhanced_interaction("scan wifi", "Found 15 networks in your area")
    time.sleep(2)
    
    cli.enhanced_interaction("help with coding", "Here's how to write a Python function...")
    time.sleep(2)
    
    # Show final status
    status = cli.get_enhanced_status()
    print(f"\\n📊 Enhanced System Status:")
    print(f"   Interactions: {status['session_stats']['interactions']}")
    print(f"   WiFi scans: {status['session_stats']['wifi_scans']}")
    print(f"   Capabilities: {len(status['capabilities'])}")

if __name__ == "__main__":
    main()