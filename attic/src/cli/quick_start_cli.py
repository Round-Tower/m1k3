#!/usr/bin/env python3
"""
M1K3 Quick Start CLI 
Fast startup with pre-loaded models and sound effects
"""

import sys
import time
import random
from pathlib import Path

# Import our enhanced systems
try:
    from sound_manager import SoundManager
    SOUNDS_AVAILABLE = True
except ImportError:
    print("⚠️  Sound system not available")
    SOUNDS_AVAILABLE = False

try:
    from src.engines.voice.turbo_voice_engine import TurboVoiceEngine
    TURBO_VOICE_AVAILABLE = True
except ImportError:
    print("⚠️  Turbo voice not available")
    TURBO_VOICE_AVAILABLE = False

try:
    from wireless_scanner import WirelessScanner
    WIRELESS_AVAILABLE = True
except ImportError:
    print("⚠️  Wireless scanner not available") 
    WIRELESS_AVAILABLE = False

# Use existing AI but prevent model downloads
try:
    from src.engines.ai.ai_inference import LocalAIEngine
    AI_AVAILABLE = True
except ImportError:
    from src.engines.ai.simple_ai_engine import SimpleAIEngine
    AI_AVAILABLE = False

class QuickStartM1K3:
    """Fast-starting M1K3 with enhanced features"""
    
    def __init__(self, enable_sounds: bool = True, enable_voice: bool = True):
        self.enable_sounds = enable_sounds and SOUNDS_AVAILABLE
        self.enable_voice = enable_voice and TURBO_VOICE_AVAILABLE
        
        # Initialize systems
        self.sound_manager = None
        self.voice_engine = None
        self.wireless_scanner = None
        self.ai_engine = None
        
        print("🚀 M1K3 Quick Start")
        print("=" * 30)
        
    def startup_with_effects(self, startup_style: str = "psx"):
        """Fast startup with sound effects"""
        
        # 1. Initialize sound system first
        if self.enable_sounds:
            print("🔊 Loading sound system...")
            try:
                self.sound_manager = SoundManager()
                
                # Play startup sound immediately
                startup_sounds = {
                    "psx": "psx",
                    "retro": "the-sound-of-dial-up-internet-6240", 
                    "cursed": "cursed-psx-startup-sound",
                    "random": None
                }
                
                startup_sound = startup_sounds.get(startup_style, "psx")
                if startup_sound:
                    print(f"🎵 Playing {startup_style} startup sound...")
                    success = self.sound_manager.play_sound(startup_sound, "startup", background=False)
                    if not success:
                        print("⚠️  Startup sound not found, trying random...")
                        self.sound_manager.play_startup_sequence("random")
                else:
                    self.sound_manager.play_startup_sequence("random")
                    
            except Exception as e:
                print(f"⚠️  Sound system error: {e}")
                self.enable_sounds = False
        
        # 2. Initialize voice (fast)
        if self.enable_voice:
            print("🎤 Loading turbo voice...")
            try:
                self.voice_engine = TurboVoiceEngine()
                voice_ready = self.voice_engine.load_model()
                if voice_ready:
                    self.voice_engine.synthesize_and_play("M1K3 ready for action!", background=False)
                    if self.enable_sounds:
                        self.sound_manager.play_contextual_sound("success")
            except Exception as e:
                print(f"⚠️  Voice system error: {e}")
                self.enable_voice = False
        
        # 3. Initialize AI (use cached models only)
        print("🧠 Loading AI engine...")
        try:
            if AI_AVAILABLE:
                # Create AI engine but don't trigger downloads
                self.ai_engine = LocalAIEngine()
                print("✅ AI engine ready")
            else:
                from src.engines.ai.simple_ai_engine import SimpleAIEngine
                self.ai_engine = SimpleAIEngine()
                print("✅ Mock AI engine ready")
                
            if self.enable_sounds:
                self.sound_manager.play_sound("complete", background=True)
                
        except Exception as e:
            print(f"⚠️  AI engine error: {e}")
        
        # 4. Initialize wireless scanner (optional)
        if WIRELESS_AVAILABLE:
            try:
                self.wireless_scanner = WirelessScanner()
                print("📡 Wireless scanner ready")
            except:
                pass
        
        # 5. Completion sequence
        if self.enable_sounds:
            self.sound_manager.play_success_sequence("major")
        
        if self.enable_voice:
            self.voice_engine.synthesize_and_play("All systems operational", background=True)
        
        print("✅ M1K3 startup complete!")
        
    def quick_demo(self):
        """Quick demo of enhanced features"""
        print("\\n🎯 Quick Feature Demo")
        print("-" * 20)
        
        # Sound effects demo
        if self.enable_sounds:
            print("🔊 Testing sound effects...")
            
            effects_to_test = [
                ("success", "victory_confetti"),
                ("interaction", "laser_shot"),
                ("special", "cast_a_spell_sound")
            ]
            
            for category, sound in effects_to_test:
                print(f"   Playing: {sound}")
                self.sound_manager.play_sound(sound, category, background=False)
                time.sleep(0.5)
        
        # Voice demo
        if self.enable_voice:
            print("🎤 Testing turbo voice...")
            messages = [
                "Voice synthesis is ultra fast now!",
                "Much better quality and speed.",
                "Ready for conversations!"
            ]
            
            for msg in messages:
                self.voice_engine.synthesize_and_play(msg, background=False)
                time.sleep(0.3)
        
        # Wireless demo
        if self.wireless_scanner:
            print("📡 Quick wireless scan...")
            try:
                networks = self.wireless_scanner.scan_wifi_networks()
                print(f"   Found {len(networks)} WiFi networks")
                
                if networks and self.enable_voice:
                    self.voice_engine.synthesize_and_play(f"Detected {len(networks)} WiFi networks nearby", background=False)
                    
            except Exception as e:
                print(f"   Scan error: {e}")
        
        # AI demo
        if self.ai_engine:
            print("🧠 Testing AI response...")
            try:
                response_parts = []
                for token in self.ai_engine.generate_response("Say hello in a creative way", max_tokens=50):
                    response_parts.append(token)
                    print(token, end="", flush=True)
                
                print()  # New line
                
                full_response = "".join(response_parts)
                if self.enable_voice and full_response:
                    self.voice_engine.synthesize_and_play(full_response, background=False)
                    
            except Exception as e:
                print(f"   AI error: {e}")
    
    def interactive_mode(self):
        """Simple interactive mode with enhanced features"""
        print("\\n💬 Interactive Mode (type 'quit' to exit)")
        print("Commands: 'scan wifi', 'play sound', 'voice test', 'help'")
        print("-" * 50)
        
        while True:
            try:
                user_input = input("\\n> ").strip()
                
                if user_input.lower() in ['quit', 'exit']:
                    if self.enable_sounds:
                        self.sound_manager.play_sound("water_ripples", background=False)
                    if self.enable_voice:
                        self.voice_engine.synthesize_and_play("Goodbye!", background=False)
                    break
                
                elif user_input.lower() == 'scan wifi':
                    self.scan_wifi_command()
                
                elif user_input.lower() == 'play sound':
                    self.sound_test_command()
                    
                elif user_input.lower() == 'voice test':
                    self.voice_test_command()
                    
                elif user_input.lower() == 'help':
                    self.show_help()
                
                elif user_input:
                    self.ai_chat_command(user_input)
                    
            except KeyboardInterrupt:
                print("\\n👋 Goodbye!")
                break
            except EOFError:
                break
    
    def scan_wifi_command(self):
        """WiFi scanning command"""
        if not self.wireless_scanner:
            print("❌ Wireless scanner not available")
            return
        
        if self.enable_sounds:
            self.sound_manager.play_sound("wave_alert", background=True)
        
        print("📡 Scanning WiFi networks...")
        try:
            networks = self.wireless_scanner.scan_wifi_networks()
            print(f"✅ Found {len(networks)} networks")
            
            for i, network in enumerate(networks[:5]):  # Show first 5
                ssid = network.get('ssid', 'Hidden')
                signal = network.get('signal_strength', 'Unknown')
                print(f"   {i+1}. {ssid} ({signal})")
            
            if len(networks) > 5:
                print(f"   ... and {len(networks) - 5} more")
            
            if self.enable_voice:
                summary = f"Found {len(networks)} WiFi networks"
                self.voice_engine.synthesize_and_play(summary, background=False)
                
            if self.enable_sounds:
                self.sound_manager.play_contextual_sound("success")
                
        except Exception as e:
            print(f"❌ Scan error: {e}")
            if self.enable_sounds:
                self.sound_manager.play_contextual_sound("error")
    
    def sound_test_command(self):
        """Sound effect testing"""
        if not self.enable_sounds:
            print("❌ Sound system not available")
            return
        
        print("🎵 Sound Effect Test")
        sounds_to_test = [
            "coin", "laser_shot", "cast_a_spell_sound", 
            "victory_confetti", "ding_ding"
        ]
        
        for sound in sounds_to_test:
            print(f"   Playing: {sound}")
            self.sound_manager.play_sound(sound, background=False)
            time.sleep(0.8)
    
    def voice_test_command(self):
        """Voice engine testing"""
        if not self.enable_voice:
            print("❌ Voice system not available")
            return
        
        test_phrases = [
            "This is the turbo voice engine.",
            "Much faster than before!",
            "Crystal clear quality.",
            "Ready for action!"
        ]
        
        print("🎤 Voice Test")
        for phrase in test_phrases:
            print(f"   Saying: {phrase}")
            self.voice_engine.synthesize_and_play(phrase, background=False)
            time.sleep(0.2)
    
    def ai_chat_command(self, user_input: str):
        """AI chat with enhanced feedback"""
        if not self.ai_engine:
            print("❌ AI engine not available")
            return
        
        if self.enable_sounds:
            self.sound_manager.play_sound("brush_sfx", background=True)
        
        print("🤖 M1K3: ", end="", flush=True)
        
        try:
            response_parts = []
            for token in self.ai_engine.generate_response(user_input, max_tokens=100):
                response_parts.append(token)
                print(token, end="", flush=True)
            
            print()  # New line
            
            full_response = "".join(response_parts)
            
            # Enhanced feedback
            if self.enable_voice and full_response:
                self.voice_engine.synthesize_and_play(full_response, background=True)
            
            if self.enable_sounds:
                if "error" in full_response.lower() or "sorry" in full_response.lower():
                    self.sound_manager.play_contextual_sound("error")
                else:
                    self.sound_manager.play_contextual_sound("interaction")
        
        except Exception as e:
            print(f"❌ AI error: {e}")
            if self.enable_sounds:
                self.sound_manager.play_contextual_sound("error")
    
    def show_help(self):
        """Show help information"""
        help_text = """
🎯 M1K3 Quick Start Commands:
   
   scan wifi    - Scan for WiFi networks
   play sound   - Test sound effects  
   voice test   - Test voice synthesis
   help         - Show this help
   quit/exit    - Exit M1K3
   
   Or just type anything to chat with AI!
        """
        print(help_text)
        
        if self.enable_voice:
            self.voice_engine.synthesize_and_play("Help information displayed", background=True)

def main():
    """Main entry point"""
    import argparse
    
    parser = argparse.ArgumentParser(description="M1K3 Quick Start CLI")
    parser.add_argument("--no-sounds", action="store_true", help="Disable sound effects")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--startup-style", choices=["psx", "retro", "cursed", "random"], 
                       default="psx", help="Startup sound style")
    parser.add_argument("--demo-only", action="store_true", help="Run demo then exit")
    
    args = parser.parse_args()
    
    # Create M1K3 instance
    m1k3 = QuickStartM1K3(
        enable_sounds=not args.no_sounds,
        enable_voice=not args.no_voice
    )
    
    # Startup sequence
    m1k3.startup_with_effects(args.startup_style)
    
    if args.demo_only:
        # Run demo and exit
        m1k3.quick_demo()
    else:
        # Enter interactive mode
        m1k3.interactive_mode()

if __name__ == "__main__":
    main()