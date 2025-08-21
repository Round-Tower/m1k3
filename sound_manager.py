#!/usr/bin/env python3
"""
M1K3 Sound Manager
Advanced sound effects system with contextual audio and startup sounds
"""

import random
import time
import threading
import subprocess
import platform
from pathlib import Path
from typing import Dict, List, Optional, Any
import json

class SoundManager:
    """Advanced sound effects management for M1K3"""
    
    def __init__(self, sounds_dir: str = "sounds"):
        self.sounds_dir = Path(sounds_dir)
        self.platform = platform.system().lower()
        self.sound_effects = self._discover_sounds()
        self.sound_profiles = self._create_sound_profiles()
        self.current_profile = "default"
        self.volume = 0.8
        self.enabled = True
        
    def _discover_sounds(self) -> Dict[str, Dict[str, Path]]:
        """Discover and categorize all sound files"""
        sounds = {
            "startup": {},
            "success": {},
            "error": {},
            "notification": {},
            "interaction": {},
            "ambient": {},
            "special": {}
        }
        
        if not self.sounds_dir.exists():
            return sounds
        
        # Startup sounds (special category)
        startup_dir = self.sounds_dir / "startup"
        if startup_dir.exists():
            for sound_file in startup_dir.glob("*"):
                if sound_file.suffix.lower() in ['.wav', '.mp3', '.m4a']:
                    sounds["startup"][sound_file.stem] = sound_file
        
        # Categorize main sound directory
        sound_categories = {
            "success": ["complete", "victory", "chime", "ding", "success", "coin", "cash", "plus", "collect"],
            "error": ["error", "incorrect", "bad", "fail", "oh_no", "ugh", "break"],
            "notification": ["alert", "reminder", "wave", "bell", "notify"],
            "interaction": ["click", "tap", "select", "brush", "camera", "smack"],
            "special": ["magic", "spell", "laser", "voltage", "whoosh", "drumroll", "hammer"],
            "ambient": ["water", "wind", "nature", "ripples"]
        }
        
        # Categorize sounds by filename
        for sound_file in self.sounds_dir.glob("*.wav"):
            categorized = False
            filename_lower = sound_file.stem.lower()
            
            for category, keywords in sound_categories.items():
                if any(keyword in filename_lower for keyword in keywords):
                    sounds[category][sound_file.stem] = sound_file
                    categorized = True
                    break
            
            if not categorized:
                sounds["interaction"][sound_file.stem] = sound_file
        
        return sounds
    
    def _create_sound_profiles(self) -> Dict[str, Dict[str, Any]]:
        """Create different sound profiles for different moods/contexts"""
        return {
            "default": {
                "startup_sounds": ["psx", "cursed-psx-startup-sound"],
                "success_sounds": ["complete", "victory_confetti", "coin", "chime1"],
                "error_sounds": ["click_error", "incorrect_sfx", "oh_no"],
                "interaction_sounds": ["brush_sfx", "camera_sfx", "plus_sfx"],
                "special_sounds": ["cast_a_spell_sound", "laser_shot", "voltage"]
            },
            "retro": {
                "startup_sounds": ["psx", "the-sound-of-dial-up-internet-6240"],
                "success_sounds": ["coin", "plus_sfx", "complete"],
                "error_sounds": ["badBoing", "donk", "windowBreak"],
                "interaction_sounds": ["donk2", "smack", "laser_shot2"],
                "special_sounds": ["voltage", "whoosh", "drumroll"]
            },
            "gaming": {
                "startup_sounds": ["cursed-psx-startup-sound"],
                "success_sounds": ["victory_confetti", "cat_star_collect", "horray_fireworks"],
                "error_sounds": ["oh_no", "ugh", "badBoing"],
                "interaction_sounds": ["laser_shot", "minty_attack", "protect_sound"],
                "special_sounds": ["cast_a_spell_sound", "short_magic_shot", "wildrumble_healing"]
            },
            "minimal": {
                "startup_sounds": ["chime1"],
                "success_sounds": ["ding_ding", "complete"],
                "error_sounds": ["click_error"],
                "interaction_sounds": ["brush_sfx"],
                "special_sounds": ["water_ripples"]
            }
        }
    
    def play_sound(self, sound_name: str, category: str = None, background: bool = True) -> bool:
        """Play a specific sound effect"""
        if not self.enabled:
            return False
        
        sound_path = None
        
        # Find sound in specified category or search all
        if category and category in self.sound_effects:
            if sound_name in self.sound_effects[category]:
                sound_path = self.sound_effects[category][sound_name]
        else:
            # Search all categories
            for cat_sounds in self.sound_effects.values():
                if sound_name in cat_sounds:
                    sound_path = cat_sounds[sound_name]
                    break
        
        if not sound_path:
            return False
        
        return self._execute_sound_playback(sound_path, background)
    
    def play_contextual_sound(self, context: str, background: bool = True) -> bool:
        """Play a contextual sound based on current profile"""
        if not self.enabled:
            return False
        
        profile = self.sound_profiles.get(self.current_profile, self.sound_profiles["default"])
        sound_list_key = f"{context}_sounds"
        
        if sound_list_key not in profile:
            return False
        
        available_sounds = profile[sound_list_key]
        if not available_sounds:
            return False
        
        # Choose random sound from context
        chosen_sound = random.choice(available_sounds)
        return self.play_sound(chosen_sound, background=background)
    
    def play_startup_sequence(self, sequence_type: str = "random") -> bool:
        """Play startup sound sequence (non-blocking for fast startup)"""
        if not self.enabled:
            return False
        
        startup_sounds = self.sound_effects["startup"]
        if not startup_sounds:
            return False
        
        if sequence_type == "random":
            sound_name = random.choice(list(startup_sounds.keys()))
            return self.play_sound(sound_name, "startup", background=True)  # Non-blocking for fast startup
        
        elif sequence_type == "retro":
            # Play dial-up sound for nostalgic effect
            if "the-sound-of-dial-up-internet-6240" in startup_sounds:
                return self.play_sound("the-sound-of-dial-up-internet-6240", "startup", background=True)  # Non-blocking
        
        elif sequence_type == "psx":
            # PlayStation startup sound
            psx_sounds = [name for name in startup_sounds.keys() if "psx" in name.lower()]
            if psx_sounds:
                return self.play_sound(random.choice(psx_sounds), "startup", background=True)  # Non-blocking
        
        return False
    
    def play_success_sequence(self, level: str = "normal") -> bool:
        """Play success sound with different intensity levels"""
        if level == "major":
            sounds = ["victory_confetti", "horray_fireworks", "girl_yeaaaaah", "crowd_cheer_sfx"]
        elif level == "minor":
            sounds = ["coin", "plus_sfx", "ding_ding"]
        else:  # normal
            sounds = ["complete", "chime1", "cat_star_collect"]
        
        # Filter to available sounds
        available = [s for s in sounds if self._sound_exists(s)]
        if available:
            return self.play_sound(random.choice(available), background=True)
        return False
    
    def play_thinking_ambient(self) -> bool:
        """Play subtle ambient sound while AI is thinking"""
        ambient_sounds = ["water_ripples", "wave_sfx", "wildrumble_healing"]
        available = [s for s in ambient_sounds if self._sound_exists(s)]
        
        if available:
            return self.play_sound(random.choice(available), background=True)
        return False
    
    def set_sound_profile(self, profile_name: str) -> bool:
        """Switch to different sound profile"""
        if profile_name in self.sound_profiles:
            self.current_profile = profile_name
            print(f"🔊 Sound profile changed to: {profile_name}")
            return True
        return False
    
    def get_available_profiles(self) -> List[str]:
        """Get list of available sound profiles"""
        return list(self.sound_profiles.keys())
    
    def get_sound_inventory(self) -> Dict[str, List[str]]:
        """Get inventory of all available sounds by category"""
        inventory = {}
        for category, sounds in self.sound_effects.items():
            inventory[category] = list(sounds.keys())
        return inventory
    
    def _sound_exists(self, sound_name: str) -> bool:
        """Check if a sound exists in any category"""
        for cat_sounds in self.sound_effects.values():
            if sound_name in cat_sounds:
                return True
        return False
    
    def _execute_sound_playback(self, sound_path: Path, background: bool) -> bool:
        """Execute platform-specific sound playback"""
        try:
            if self.platform == "darwin":  # macOS
                cmd = ["afplay", str(sound_path)]
            elif self.platform == "linux":
                if sound_path.suffix.lower() == ".wav":
                    cmd = ["aplay", str(sound_path)]
                else:
                    cmd = ["mpg123", "-q", str(sound_path)]
            elif self.platform == "windows":
                cmd = ["powershell", "-Command", 
                      f"(New-Object Media.SoundPlayer '{sound_path}').PlaySync()"]
            else:
                return False
            
            if background:
                subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                result = subprocess.run(cmd, timeout=30, capture_output=True)
                return result.returncode == 0
            
            return True
            
        except Exception as e:
            print(f"🔇 Sound playback error: {e}")
            return False
    
    def test_sound_system(self) -> Dict[str, Any]:
        """Test the sound system and return diagnostics"""
        results = {
            "platform": self.platform,
            "sounds_directory": str(self.sounds_dir),
            "total_sounds": sum(len(sounds) for sounds in self.sound_effects.values()),
            "categories": {cat: len(sounds) for cat, sounds in self.sound_effects.items()},
            "profiles_available": len(self.sound_profiles),
            "current_profile": self.current_profile,
            "test_results": {}
        }
        
        # Test basic playback capability
        test_sound = None
        for cat_sounds in self.sound_effects.values():
            if cat_sounds:
                test_sound = next(iter(cat_sounds.values()))
                break
        
        if test_sound:
            try:
                success = self._execute_sound_playback(test_sound, background=False)
                results["test_results"]["basic_playback"] = success
            except Exception as e:
                results["test_results"]["basic_playback"] = False
                results["test_results"]["error"] = str(e)
        
        return results

class ContextualSoundManager:
    """Extended sound manager with AI context awareness"""
    
    def __init__(self, sound_manager: SoundManager):
        self.sound_manager = sound_manager
        self.context_history = []
        self.max_history = 10
        
    def play_response_sound(self, ai_response: str, user_query: str) -> bool:
        """Play contextually appropriate sound based on AI response"""
        response_lower = ai_response.lower()
        query_lower = user_query.lower()
        
        # Error/problem context
        if any(word in response_lower for word in ["error", "sorry", "can't", "unable", "problem"]):
            return self.sound_manager.play_contextual_sound("error")
        
        # Success/completion context  
        elif any(word in response_lower for word in ["complete", "done", "finished", "success", "great"]):
            return self.sound_manager.play_contextual_sound("success")
        
        # Code/programming context
        elif any(word in query_lower for word in ["code", "program", "function", "script"]):
            return self.sound_manager.play_sound("laser_shot", background=True)
        
        # Question/help context
        elif "?" in user_query or any(word in query_lower for word in ["help", "how", "what", "explain"]):
            return self.sound_manager.play_sound("chime1", background=True)
        
        # Default interaction
        else:
            return self.sound_manager.play_contextual_sound("interaction")
    
    def play_system_event_sound(self, event_type: str, details: Dict[str, Any] = None) -> bool:
        """Play sounds for system events"""
        event_sounds = {
            "model_loaded": "complete",
            "model_failed": "oh_no", 
            "voice_enabled": "chime1",
            "avatar_connected": "cast_a_spell_sound",
            "wifi_scan_complete": "wave_sfx",
            "bluetooth_found": "ding_ding",
            "cache_cleared": "brush_sfx",
            "shutdown": "water_ripples"
        }
        
        sound_name = event_sounds.get(event_type)
        if sound_name:
            return self.sound_manager.play_sound(sound_name, background=True)
        
        return False