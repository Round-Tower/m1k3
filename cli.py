#!/usr/bin/env python3
"""
M1K3 CLI Application with Local AI Integration
Command-line interface with avatar state management
"""

import sys
import time
import threading
from enum import Enum
from typing import Optional
import argparse

# Try to import the real AI engine first, fall back to mock if not available
try:
    from ai_inference import LocalAIEngine
    REAL_AI_AVAILABLE = True
    print("🧠 Using real AI inference engine")
except ImportError as e:
    print(f"⚠️  Real AI engine not available: {e}")
    print("🔄 Falling back to mock AI engine")
    from simple_ai_engine import SimpleAIEngine
    REAL_AI_AVAILABLE = False

from download_model import download_model
from enhanced_voice_engine import create_voice_engine
from system_metrics import SystemMonitor, generate_dynamic_greeting
from cli_animations import CLIAnimator, AnimationType

class AvatarState(Enum):
    IDLE = "💤"
    THINKING = "🤔" 
    GENERATING = "⚡"
    ERROR = "❌"
    LOADING = "⏳"
    SPEAKING = "🔊"

class M1K3CLI:
    def __init__(self, voice_enabled: bool = True):
        # Initialize system monitoring first to gather context
        self.system_monitor = SystemMonitor()
        
        # Use real AI engine if available, otherwise fall back to mock
        if REAL_AI_AVAILABLE:
            self.ai_engine = LocalAIEngine()
            print("🚀 Initialized with real AI inference engine")
        else:
            self.ai_engine = SimpleAIEngine()
            print("🎭 Initialized with mock AI engine (demo mode)")
            
        self.voice_engine = create_voice_engine()
        self.animator = CLIAnimator()
        self.avatar_state = AvatarState.IDLE
        self.running = True
        self.voice_enabled = voice_enabled
        self.show_context = False  # Show detailed system context
        
        # Set up AI engine with system context
        self._initialize_ai_context()
    
    def _initialize_ai_context(self):
        """Initialize AI engine with rich system context"""
        metrics = self.system_monitor.collect_metrics()
        
        # Set session context with system information
        session_context = {
            "device_type": f"{metrics.cpu_model} with {metrics.memory_total_gb:.1f}GB RAM" if metrics.memory_total_gb else f"{metrics.cpu_model}",
            "operating_system": f"{metrics.os_name} {metrics.os_version}",
            "performance_state": metrics.performance_status(),
            "battery_state": metrics.battery_status(),
            "thermal_state": metrics.thermal_status(),
            "timezone": metrics.timezone,
            "locale": metrics.locale_info,
            "cpu_cores": f"{metrics.cpu_cores}c/{metrics.cpu_threads}t",
            "uptime_hours": f"{metrics.uptime_hours:.1f}h" if metrics.uptime_hours else "unknown"
        }
        
        # Only set session context if the engine supports it
        if hasattr(self.ai_engine, 'set_session_context'):
            self.ai_engine.set_session_context(session_context)
        
        # Set some default user preferences (can be updated based on interaction)
        if hasattr(self.ai_engine, 'add_user_preference'):
            self.ai_engine.add_user_preference("response_style", "conversational")
            self.ai_engine.add_user_preference("technical_level", "intermediate")
        
        # Set up trim callback for animations
        if hasattr(self.ai_engine, '_trim_callback'):
            self.ai_engine._trim_callback = self._animate_context_trim
        
    def set_avatar_state(self, state: AvatarState):
        """Update avatar state"""
        self.avatar_state = state
        
    def print_with_avatar(self, message: str, state: Optional[AvatarState] = None):
        """Print message with current avatar state"""
        if state:
            self.set_avatar_state(state)
        print(f"{self.avatar_state.value} {message}")
        
    def animated_print(self, message: str, state: Optional[AvatarState] = None, effect: str = "typewriter"):
        """Print message with animation effects"""
        if state:
            self.set_avatar_state(state)
            
        if effect == "typewriter":
            self.animator.typewriter_effect(f"{self.avatar_state.value} {message}")
        elif effect == "fade":
            self.animator.fade_in_text(f"{self.avatar_state.value} {message}")
        elif effect == "pulse":
            self.animator.pulse_text(f"{self.avatar_state.value} {message}")
        else:
            self.print_with_avatar(message, state)
    
    def start_animated_status(self, message: str, animation_type: str = "thinking"):
        """Start an animated status indicator"""
        if animation_type in ["thinking", "generating", "loading", "speaking", "listening", "processing"]:
            self.animator.start_avatar_animation(animation_type, message)
        else:
            self.animator.start_animation(AnimationType.SPINNER, message)
    
    def stop_animated_status(self):
        """Stop the current animated status"""
        self.animator.stop_animation()
        
    def setup_ai(self) -> bool:
        """Initialize AI engine and voice with animations"""
        self.animated_print("Initializing M1K3 AI Engine...", AvatarState.LOADING, "fade")
        
        # Check if model exists
        if not self.ai_engine.is_model_available():
            self.start_animated_status("Downloading SmolLM-135M model...", "loading")
            model_path = download_model()
            self.stop_animated_status()
            
            if not model_path:
                self.animated_print("Failed to download model", AvatarState.ERROR, "pulse")
                return False
                
        # Load AI model
        self.start_animated_status("Loading AI model...", "processing")
        success = self.ai_engine.load_model()
        self.stop_animated_status()
        
        if not success:
            self.animated_print("Failed to load model", AvatarState.ERROR, "pulse")
            return False
            
        # Load voice model if enabled
        if self.voice_enabled and self.voice_engine.is_available():
            self.start_animated_status("Loading voice synthesis...", "loading")
            voice_success = self.voice_engine.load_model()
            self.stop_animated_status()
            
            if voice_success:
                self.animated_print("Voice synthesis ready!", AvatarState.IDLE, "pulse")
            else:
                self.print_with_avatar("Voice failed, continuing without", AvatarState.IDLE)
                self.voice_enabled = False
        elif not self.voice_engine.is_available():
            self.voice_enabled = False
            
        self.animated_print("AI Engine ready!", AvatarState.IDLE, "pulse")
        return True
        
    def handle_user_input(self, user_input: str):
        """Process user input and generate AI response"""
        
        # Handle special commands
        if user_input.lower() in ['quit', 'exit', 'q']:
            goodbye_msg = "Goodbye!"
            self.print_with_avatar(goodbye_msg, AvatarState.IDLE)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(goodbye_msg, background=False)
            self.running = False
            return
            
        elif user_input.lower() in ['clear', 'reset']:
            msg = "Context cleared"
            self.ai_engine.clear_context()
            self.print_with_avatar(msg, AvatarState.IDLE)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(msg)
            return
            
        elif user_input.lower() in ['voice', 'mute']:
            self.voice_enabled = not self.voice_enabled
            self.voice_engine.set_voice_enabled(self.voice_enabled)
            msg = f"Voice {'enabled' if self.voice_enabled else 'disabled'}"
            self.print_with_avatar(msg, AvatarState.IDLE)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play("Voice enabled")
            return
            
        elif user_input.lower().startswith('persona ') or user_input.lower().startswith('character '):
            # Support both "persona" and "character" commands
            if user_input.lower().startswith('persona '):
                persona_name = user_input[8:].strip()
            else:
                persona_name = user_input[10:].strip()
                
            if hasattr(self.voice_engine, 'quick_persona_switch'):
                if self.voice_engine.quick_persona_switch(persona_name):
                    # Get current persona info
                    if hasattr(self.voice_engine, 'get_current_persona'):
                        persona = self.voice_engine.get_current_persona()
                        msg = f"Persona: {persona.get('name', persona_name)}"
                        self.print_with_avatar(msg, AvatarState.IDLE)
                        if self.voice_enabled:
                            self.voice_engine.speak_persona_intro()
                    else:
                        msg = f"Persona: {persona_name}"
                        self.print_with_avatar(msg, AvatarState.IDLE)
                else:
                    available = list(self.voice_engine.get_personas().keys()) if hasattr(self.voice_engine, 'get_personas') else []
                    shortcuts = ["natural", "assistant", "pa_system", "broadcast", "terminal", "clear", "ai", "pa", "radio"]
                    msg = f"Available personas: {', '.join(available)} (shortcuts: {', '.join(shortcuts)})"
                    self.print_with_avatar(msg, AvatarState.IDLE)
            else:
                self.print_with_avatar("Persona switching not available", AvatarState.IDLE)
            return
            
        elif user_input.lower() in ['zen', 'classic', 'retro']:
            if hasattr(self.voice_engine, 'toggle_zen_mode'):
                success = self.voice_engine.toggle_zen_mode()
                if success and self.voice_enabled:
                    mode = "zen" if self.voice_engine.zen_mode else "classic"
                    self.voice_engine.synthesize_and_play(f"{mode.title()} voice mode activated")
            return
            
        elif user_input.lower() in ['stats', 'status']:
            self.display_system_stats()
            return
            
        elif user_input.lower() in ['context', 'device']:
            self.display_device_context()
            return
            
        elif user_input.lower() in ['tokens', 'usage']:
            self.display_token_stats()
            return
            
        elif user_input.lower() in ['animate', 'demo']:
            self.demo_animations()
            return
            
        elif user_input.lower() in ['help', 'h']:
            self.show_help()
            return
            
        # Generate AI response with animations
        self.start_animated_status("Thinking...", "thinking")
        time.sleep(1.0)  # Give time to see thinking animation
        self.stop_animated_status()
        
        self.start_animated_status("Generating response...", "generating")
        time.sleep(0.5)  # Brief pause before starting generation
        self.stop_animated_status()
        
        print(f"{AvatarState.GENERATING.value} ", end="", flush=True)
        
        try:
            response_started = False
            full_response = ""
            for token in self.ai_engine.generate_response(user_input):
                if not response_started:
                    print("\n", end="", flush=True)  # New line before response
                    response_started = True
                print(token, end="", flush=True)
                full_response += token
                
            print()  # Final newline
            self.set_avatar_state(AvatarState.IDLE)
            
            # Display eco-friendly metrics and token usage
            self._display_post_response_metrics()
            
            # Synthesize voice in background
            if self.voice_enabled and full_response.strip():
                self.set_avatar_state(AvatarState.SPEAKING)
                self.voice_engine.synthesize_and_play(full_response.strip())
                self.set_avatar_state(AvatarState.IDLE)
            
        except Exception as e:
            error_msg = f"Error: {e}"
            self.print_with_avatar(error_msg, AvatarState.ERROR)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(error_msg)
            
    def show_help(self):
        """Display help information"""
        help_text = """
M1K3 Local AI CLI Commands:
  
  Chat Commands:
    <message>       Send message to AI
    clear, reset    Clear conversation context  
    stats, status   Show system statistics with animations
    context, device Show comprehensive device context
    tokens, usage   Show token usage and eco impact
    animate, demo   Demonstrate animation capabilities
    help, h         Show this help
    quit, q         Exit application
    
  Voice Commands:
    voice, mute     Toggle voice synthesis on/off
    persona <name>  Set persona (natural, assistant, pa_system, broadcast, terminal)
    zen, classic    Toggle between zen and standard voice modes
    
  Optimized Personas:
    assistant      Clean, clear voice with light processing (default)
    natural        Pure, unprocessed voice with maximum clarity
    pa_system      Public address system voice with gentle filtering
    broadcast      Radio-quality voice with professional sound  
    terminal       Retro computer terminal voice
    
  Avatar States:
    💤 Idle        Ready for input
    ⏳ Loading     Starting up or downloading
    🤔 Thinking    Processing your input  
    ⚡ Generating  Streaming AI response
    🔊 Speaking    Voice synthesis active
    ❌ Error       Something went wrong
    
  Features:
    • High-quality voice synthesis with faster, natural pacing
    • Rich CLI animations and visual effects
    • Comprehensive device context collection (privacy-focused)
    • Real-time system monitoring and statistics
    • Dynamic system-aware greetings based on device state
    • Hardware capability detection and display
    • Animated status indicators and progress bars
    • Context-aware conversations with streaming responses
"""
        print(help_text)
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Here's what I can do for you")
    
    def display_system_stats(self):
        """Display animated system statistics"""
        self.start_animated_status("Collecting system statistics...", "processing")
        
        ai_stats = self.ai_engine.get_memory_usage()
        voice_status = self.voice_engine.get_status()
        metrics = self.system_monitor.collect_metrics()
        
        self.stop_animated_status()
        
        print("\n📊 System Statistics:")
        print("=" * 40)
        
        # AI Engine stats
        print(f"🧠 AI Engine:")
        print(f"   Memory Usage: {ai_stats['memory_mb']} MB")
        print(f"   Context: {ai_stats['context_tokens']} tokens")
        print(f"   Model: {ai_stats.get('model_name', 'SmolLM-135M')}")
        
        # Voice engine stats
        print(f"🔊 Voice Engine:")
        print(f"   Status: {'Enabled' if voice_status['enabled'] else 'Disabled'}")
        print(f"   Persona: {voice_status.get('persona_name', 'Unknown')}")
        print(f"   Engine: {voice_status.get('engine', 'None')}")
        
        # System performance
        if metrics.cpu_usage is not None:
            print(f"⚙️  Performance:")
            print(f"   CPU Usage: {metrics.cpu_usage:.1f}%")
            print(f"   Memory: {metrics.memory_percent:.1f}%")
            if metrics.load_average:
                print(f"   Load Average: {metrics.load_average:.2f}")
        
        # Power and thermal
        if metrics.battery_percent is not None:
            battery_emoji = "🔋" if not metrics.battery_plugged else "⚡"
            print(f"{battery_emoji} Power:")
            print(f"   Battery: {metrics.battery_percent}% ({metrics.battery_status()})")
        
        if metrics.cpu_temp is not None:
            print(f"🌡️  Thermal:")
            print(f"   CPU Temp: {metrics.cpu_temp:.1f}°C ({metrics.thermal_status()})")
            
        if self.voice_enabled:
            status_msg = f"System running {metrics.performance_status()}"
            if metrics.battery_percent:
                status_msg += f", battery at {metrics.battery_percent} percent"
            self.voice_engine.synthesize_and_play(status_msg)
    
    def display_device_context(self):
        """Display comprehensive device context"""
        self.start_animated_status("Analyzing device capabilities...", "processing")
        
        metrics = self.system_monitor.collect_metrics()
        context_summary = self.system_monitor.get_context_summary(metrics)
        
        self.stop_animated_status()
        
        print("\n🖥️  Device Context:")
        print("=" * 50)
        
        # Hardware
        if metrics.cpu_model:
            print(f"💻 Hardware:")
            print(f"   CPU: {metrics.cpu_model}")
            if metrics.cpu_cores and metrics.cpu_threads:
                print(f"   Cores: {metrics.cpu_cores} physical, {metrics.cpu_threads} threads")
            if metrics.cpu_arch:
                print(f"   Architecture: {metrics.cpu_arch}")
            if metrics.gpu_info:
                print(f"   GPU: {metrics.gpu_info}")
            if metrics.memory_total_gb:
                print(f"   Memory: {metrics.memory_total_gb:.1f} GB")
        
        # System environment
        if metrics.os_name:
            print(f"🌍 Environment:")
            print(f"   OS: {metrics.os_name} {metrics.os_version or ''}")
            if metrics.hostname:
                print(f"   Device: {metrics.hostname}")
            if metrics.timezone:
                sign = "+" if (metrics.timezone_offset or 0) >= 0 else "-"
                print(f"   Timezone: {metrics.timezone} (UTC{sign}{abs(metrics.timezone_offset or 0)})")
            if metrics.locale_info:
                print(f"   Locale: {metrics.locale_info}")
            if metrics.current_time:
                print(f"   Time: {metrics.current_time}")
        
        # Capabilities
        capabilities = []
        if metrics.has_microphone:
            capabilities.append("🎤 Microphone")
        if metrics.has_speakers:
            capabilities.append("🔊 Audio Output")
        if metrics.has_wifi:
            capabilities.append("📶 WiFi")
        if metrics.has_ethernet:
            capabilities.append("🌐 Ethernet")
        if metrics.display_count and metrics.display_count > 1:
            capabilities.append(f"🖥️ {metrics.display_count} Displays")
        
        if capabilities:
            print(f"🔌 Capabilities:")
            for cap in capabilities:
                print(f"   {cap}")
        
        # Storage
        if metrics.disk_total_gb:
            print(f"💾 Storage:")
            print(f"   Total: {metrics.disk_total_gb:.1f} GB")
            print(f"   Used: {metrics.disk_usage_percent:.1f}%")
            print(f"   Free: {metrics.disk_free_gb:.1f} GB")
        
        print(f"\n📋 Summary:")
        print(f"   {context_summary}")
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Device context collected successfully")
    
    def demo_animations(self):
        """Demonstrate animation capabilities"""
        print("\n🎬 Animation Demo:")
        print("=" * 30)
        
        # Typewriter effect
        print("\n1. Typewriter Effect:")
        self.animator.typewriter_effect("✨ M1K3 is typing this message character by character...")
        
        # Fade in effect
        print("\n2. Fade In Effect:")
        self.animator.fade_in_text("🌟 This text fades in gradually!")
        
        # Pulse effect
        print("\n3. Pulse Effect:")
        self.animator.pulse_text("💫 This message pulses with intensity!")
        
        # Progress bar
        print("\n4. Progress Bar:")
        for i in range(0, 101, 5):
            self.animator.progress_bar(i, 100, prefix="Demo Progress")
            time.sleep(0.1)
        
        # Avatar animations
        print("\n5. Avatar State Animations:")
        
        animations = [
            ("thinking", "Processing your request..."),
            ("generating", "Generating response..."),
            ("loading", "Loading resources..."),
            ("speaking", "Voice synthesis active..."),
        ]
        
        for anim_type, message in animations:
            print(f"\n   {anim_type.title()}:")
            self.animator.start_avatar_animation(anim_type, message, 0.3)
            time.sleep(2)
            self.animator.stop_animation()
        
        print("\n✅ Animation demo complete!")
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Animation demonstration finished")
        
    def run_interactive(self):
        """Run interactive CLI session with animations"""
        # Animated startup
        self.animator.typewriter_effect("🧘 M1K3 - Local AI with Advanced Voice & Animations")
        self.animator.fade_in_text("✨ Fast synthesis • Rich context • Privacy-focused")
        print("=" * 60)
        
        # Setup AI engine
        if not self.setup_ai():
            return 1
        
        # Generate dynamic greeting based on system metrics
        try:
            self.start_animated_status("Collecting device context for personalized greeting...", "processing")
            metrics = self.system_monitor.collect_metrics()
            greeting = generate_dynamic_greeting(metrics)
            context_summary = self.system_monitor.get_context_summary(metrics)
            self.stop_animated_status()
            
            print(f"\n💬 {greeting}")
            
            # Show brief context summary
            if context_summary:
                print(f"🔍 Context: {context_summary[:100]}{'...' if len(context_summary) > 100 else ''}")
            
            self.animated_print("Type 'help' for commands or start chatting!", effect="fade")
            
            # Speak the greeting if voice is enabled
            if self.voice_enabled:
                self.start_animated_status("Speaking greeting...", "speaking")
                self.voice_engine.synthesize_and_play(greeting, background=False)
                self.stop_animated_status()
                
        except Exception as e:
            print(f"\nReady to chat! (Greeting error: {e})")
            self.animated_print("Type 'help' for commands or start chatting!", effect="typewriter")
        
        while self.running:
            try:
                # Get user input with avatar prompt
                user_input = input(f"\n{self.avatar_state.value} > ").strip()
                
                if not user_input:
                    continue
                    
                self.handle_user_input(user_input)
                
            except KeyboardInterrupt:
                self.print_with_avatar("\nExiting...", AvatarState.IDLE)
                break
            except EOFError:
                break
            except Exception as e:
                self.print_with_avatar(f"Unexpected error: {e}", AvatarState.ERROR)
                
        return 0
        
    def run_single_query(self, query: str):
        """Run single query mode"""
        if not self.setup_ai():
            return 1
            
        self.handle_user_input(query)
        return 0

    def _animate_context_trim(self, messages_removed: int):
        """Animate context trimming process"""
        self.animator.animate_context_trimming(messages_removed)
    
    def _display_post_response_metrics(self):
        """Display token usage and eco metrics after response"""
        # Get current token usage
        token_usage = self.ai_engine.get_token_usage()
        
        # Display animated token bar
        token_display = self.animator.animate_token_bar(
            token_usage["current_tokens"], 
            token_usage["max_tokens"]
        )
        print(token_display)
        
        # Get and display eco metrics
        eco_metrics = self.ai_engine.get_eco_metrics()
        self.animator.animate_eco_metrics(
            eco_metrics["energy_saved_kwh"],
            eco_metrics["water_saved_gallons"], 
            eco_metrics["co2_saved_grams"]
        )
        
        # Show privacy shield animation
        self.animator.animate_privacy_shield(eco_metrics["data_transmitted"])
        
    def display_token_stats(self):
        """Display comprehensive token and eco statistics"""
        print("🧠 M1K3 Token & Eco Statistics")
        print("=" * 50)
        
        # Token usage
        token_usage = self.ai_engine.get_token_usage()
        token_display = self.animator.animate_token_bar(
            token_usage["current_tokens"], 
            token_usage["max_tokens"]
        )
        print(token_display)
        print(f"📊 Messages in context: {token_usage['messages_count']}")
        print(f"🎯 Trimming threshold: {token_usage['trimming_threshold']:,} tokens")
        
        if token_usage["needs_trimming"]:
            print("⚠️ Context will be trimmed on next message")
        else:
            remaining = token_usage["trimming_threshold"] - token_usage["current_tokens"]
            print(f"✅ {remaining:,} tokens until trimming")
            
        print()
        
        # Eco metrics
        eco_metrics = self.ai_engine.get_eco_metrics()
        print("🌱 Environmental Impact:")
        print(f"   ⚡ Energy saved: {eco_metrics['energy_saved_kwh']} kWh")
        print(f"   💧 Water saved: {eco_metrics['water_saved_gallons']} gallons")
        print(f"   🌍 CO2 prevented: {eco_metrics['co2_saved_grams']}g")
        print(f"   🔒 Privacy score: {eco_metrics['privacy_score']}")
        print(f"   📡 Data transmitted: {eco_metrics['data_transmitted']}")
        print(f"   💬 Responses generated: {eco_metrics['responses_count']}")

def main():
    parser = argparse.ArgumentParser(description="M1K3 Local AI CLI with Voice")
    parser.add_argument("--query", "-q", help="Single query mode")
    parser.add_argument("--download-only", action="store_true", help="Download model and exit")
    parser.add_argument("--model", default="SmolLM-135M-Q4_K_M", help="Model to download")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--test-voice", action="store_true", help="Test voice synthesis only")
    
    args = parser.parse_args()
    
    if args.download_only:
        print(f"Downloading {args.model}...")
        model_path = download_model()  # Use default model
        if model_path:
            print(f"Model downloaded successfully: {model_path}")
            return 0
        else:
            print("Failed to download model")
            return 1
    
    if args.test_voice:
        from enhanced_voice_engine import create_voice_engine
        engine = create_voice_engine()
        if engine.is_available():
            if engine.load_model():
                print("🔊 Testing voice synthesis...")
                engine.synthesize_and_play("Voice synthesis test successful! M1K3 is ready to speak.", background=False)
                return 0
            else:
                print("❌ Failed to load voice model")
                return 1
        else:
            print("❌ Voice synthesis not available")
            return 1
            
    voice_enabled = not args.no_voice
    cli = M1K3CLI(voice_enabled=voice_enabled)
    
    if args.query:
        return cli.run_single_query(args.query)
    else:
        return cli.run_interactive()

if __name__ == "__main__":
    sys.exit(main())