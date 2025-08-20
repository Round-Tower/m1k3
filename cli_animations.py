#!/usr/bin/env python3
"""
CLI Animation System for M1K3
Provides animated status indicators, spinners, and transitions
"""

import time
import threading
import sys
from typing import List, Optional, Callable
from enum import Enum

class AnimationType(Enum):
    SPINNER = "spinner"
    PULSE = "pulse"
    WAVE = "wave"
    DOTS = "dots"
    PROGRESS = "progress"

class CLIAnimator:
    """Handles CLI animations and visual effects"""
    
    def __init__(self):
        self.is_animating = False
        self.animation_thread: Optional[threading.Thread] = None
        self.stop_event = threading.Event()
        
        # Animation sequences
        self.animations = {
            AnimationType.SPINNER: ["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"],
            AnimationType.PULSE: ["💤", "😴", "💤", "😴"],
            AnimationType.WAVE: ["▁", "▂", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃", "▂"],
            AnimationType.DOTS: ["⠁", "⠂", "⠄", "⠅", "⠆", "⠇", "⠈", "⠉", "⠊", "⠋"],
            AnimationType.PROGRESS: ["▱▱▱▱▱", "▰▱▱▱▱", "▰▰▱▱▱", "▰▰▰▱▱", "▰▰▰▰▱", "▰▰▰▰▰"]
        }
        
        # Avatar state animations
        self.avatar_animations = {
            "thinking": ["🤔", "💭", "🧠", "💡"],
            "generating": ["⚡", "✨", "💫", "🌟"],
            "loading": ["⏳", "⌛", "⏳", "⌛"],
            "speaking": ["🔊", "📢", "🗣️", "💬"],
            "listening": ["👂", "🎧", "🔍", "👀"],
            "processing": ["⚙️", "🔄", "⚡", "🧮"]
        }
        
    def start_animation(self, animation_type: AnimationType, message: str = "", 
                       callback: Optional[Callable] = None, duration: float = 0.1):
        """Start an animation with optional message and callback"""
        if self.is_animating:
            self.stop_animation()
            
        self.is_animating = True
        self.stop_event.clear()
        
        def animate():
            frames = self.animations[animation_type]
            frame_index = 0
            
            while not self.stop_event.is_set():
                frame = frames[frame_index % len(frames)]
                
                # Clear line and show animation
                sys.stdout.write(f"\r{frame} {message}")
                sys.stdout.flush()
                
                frame_index += 1
                time.sleep(duration)
                
                if callback and callback():
                    break
                    
            # Clear the animation line
            sys.stdout.write("\r" + " " * (len(message) + 10) + "\r")
            sys.stdout.flush()
            
        self.animation_thread = threading.Thread(target=animate, daemon=True)
        self.animation_thread.start()
        
    def start_avatar_animation(self, state: str, message: str = "", duration: float = 0.5):
        """Start avatar state animation"""
        if state not in self.avatar_animations:
            return
            
        if self.is_animating:
            self.stop_animation()
            
        self.is_animating = True
        self.stop_event.clear()
        
        def animate():
            frames = self.avatar_animations[state]
            frame_index = 0
            
            while not self.stop_event.is_set():
                frame = frames[frame_index % len(frames)]
                
                # Show animated avatar with message
                sys.stdout.write(f"\r{frame} {message}")
                sys.stdout.flush()
                
                frame_index += 1
                time.sleep(duration)
                
            # Clear the animation line
            sys.stdout.write("\r" + " " * (len(message) + 10) + "\r")
            sys.stdout.flush()
            
        self.animation_thread = threading.Thread(target=animate, daemon=True)
        self.animation_thread.start()
        
    def stop_animation(self):
        """Stop the current animation"""
        if self.is_animating:
            self.stop_event.set()
            if self.animation_thread and self.animation_thread.is_alive():
                self.animation_thread.join(timeout=0.5)
            self.is_animating = False
            
    def typewriter_effect(self, text: str, delay: float = 0.03):
        """Display text with typewriter effect"""
        for char in text:
            sys.stdout.write(char)
            sys.stdout.flush()
            time.sleep(delay)
        print()  # New line at end
        
    def fade_in_text(self, text: str, steps: int = 5):
        """Simulate fade-in effect with unicode block characters"""
        fade_chars = ["░", "▒", "▓", "█"]
        
        for step in range(steps):
            if step < len(fade_chars):
                fade_char = fade_chars[step]
                fade_text = ''.join(fade_char if c != ' ' else ' ' for c in text)
                sys.stdout.write(f"\r{fade_text}")
                sys.stdout.flush()
                time.sleep(0.1)
                
        # Final clear text
        sys.stdout.write(f"\r{text}")
        sys.stdout.flush()
        print()
        
    def progress_bar(self, current: int, total: int, width: int = 40, 
                    prefix: str = "Progress", suffix: str = "Complete"):
        """Display animated progress bar"""
        percent = (current / total) * 100
        filled_width = int(width * current // total)
        bar = "█" * filled_width + "░" * (width - filled_width)
        
        sys.stdout.write(f"\r{prefix} |{bar}| {percent:.1f}% {suffix}")
        sys.stdout.flush()
        
        if current >= total:
            print()  # New line when complete
            
    def matrix_effect(self, lines: List[str], duration: float = 2.0):
        """Display lines with Matrix-like cascading effect"""
        import random
        
        max_len = max(len(line) for line in lines) if lines else 0
        
        for i, line in enumerate(lines):
            # Cascading reveal effect
            reveal_speed = random.uniform(0.02, 0.05)
            
            for j in range(len(line) + 1):
                revealed = line[:j]
                remaining = "".join(random.choice("01") for _ in range(len(line) - j))
                display = revealed + remaining
                
                sys.stdout.write(f"\r{display}")
                sys.stdout.flush()
                time.sleep(reveal_speed)
                
            print()  # Move to next line
            time.sleep(0.1)
            
    def pulse_text(self, text: str, pulses: int = 3):
        """Make text pulse with intensity"""
        intensities = ["░", "▒", "▓", "█", "▓", "▒"]
        
        for pulse in range(pulses):
            for intensity in intensities:
                pulse_text = ''.join(intensity if c != ' ' else ' ' for c in text)
                sys.stdout.write(f"\r{pulse_text}")
                sys.stdout.flush()
                time.sleep(0.1)
                
        # Show final text
        sys.stdout.write(f"\r{text}")
        sys.stdout.flush()
        print()

# Global animator instance
animator = CLIAnimator()

def animate_loading(message: str, duration: float = 2.0):
    """Convenience function for loading animation"""
    animator.start_animation(AnimationType.SPINNER, message)
    time.sleep(duration)
    animator.stop_animation()

def animate_thinking(message: str, duration: float = 1.0):
    """Convenience function for thinking animation"""
    animator.start_avatar_animation("thinking", message)
    time.sleep(duration)
    animator.stop_animation()

if __name__ == "__main__":
    # Test animations
    print("🧘 M1K3 CLI Animation System Test")
    print("=" * 40)
    
    print("\n1. Spinner animation:")
    animate_loading("Loading AI model...", 2)
    
    print("\n2. Thinking animation:")
    animate_thinking("Processing your request...", 2)
    
    print("\n3. Typewriter effect:")
    animator.typewriter_effect("Greetings, fellow traveler. Welcome to M1K3.")
    
    print("\n4. Fade-in effect:")
    animator.fade_in_text("✨ Voice synthesis ready!")
    
    print("\n5. Progress bar:")
    for i in range(101):
        animator.progress_bar(i, 100, prefix="Model Loading")
        time.sleep(0.02)
        
    print("\n6. Pulse effect:")
    animator.pulse_text("🔊 M1K3 is ready!", 2)
    
    print("\n✅ Animation system test complete!")