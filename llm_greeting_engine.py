#!/usr/bin/env python3
"""
M1K3 LLM-Powered Greeting Engine
Generates dynamic, contextual startup greetings using the AI engine
"""

import time
import datetime
from typing import Optional, Dict, Any
from dataclasses import dataclass

@dataclass
class GreetingContext:
    """Context information for generating greetings"""
    time_of_day: str
    cpu_usage: float
    memory_percent: float
    battery_level: Optional[int] = None
    battery_status: Optional[str] = None
    thermal_state: str = "normal"
    voice_enabled: bool = True
    avatar_enabled: bool = True
    ai_model: str = "unknown"
    session_count: int = 1
    uptime_hours: float = 0.0
    # Enhanced context awareness
    timezone: Optional[str] = None
    locale: Optional[str] = None
    connected_devices: int = 0
    ble_devices: Optional[list] = None
    network_status: str = "unknown"
    disk_usage: float = 0.0
    available_memory_gb: float = 0.0
    cpu_cores: int = 0
    platform: str = "unknown"
    # Eco/Environmental metrics for sustainability-focused greetings
    energy_saved_kwh: float = 0.0
    water_saved_ml: float = 0.0
    co2_saved_g: float = 0.0
    privacy_score: str = "100% Local"
    data_transmitted_bytes: int = 0
    total_responses: int = 0
    session_eco_impact: bool = False  # Flag to highlight eco achievements
    
class LLMGreetingEngine:
    """
    LLM-powered greeting generator that creates contextual startup messages
    """
    
    def __init__(self, adaptive_ai_engine=None):
        self.adaptive_ai_engine = adaptive_ai_engine
        self.greeting_cache = {}
        self.last_greeting_time = 0
        self.cache_duration = 300  # 5 minutes
        
    def generate_greeting(self, context: GreetingContext, max_length: int = 80) -> str:
        """
        Generate a contextual greeting using the LLM
        
        Args:
            context: System context for greeting generation
            max_length: Maximum greeting length in characters
            
        Returns:
            Generated greeting string
        """
        
        # Check cache first to avoid regenerating identical greetings
        cache_key = self._create_cache_key(context)
        current_time = time.time()
        
        if (cache_key in self.greeting_cache and 
            current_time - self.last_greeting_time < self.cache_duration):
            return self.greeting_cache[cache_key]
        
        # Generate new greeting using LLM
        if self.adaptive_ai_engine:
            try:
                greeting = self._generate_llm_greeting(context, max_length)
                
                # Cache the result
                self.greeting_cache[cache_key] = greeting
                self.last_greeting_time = current_time
                
                return greeting
                
            except Exception as e:
                print(f"LLM greeting generation failed: {e}")
                # Fall back to rule-based greeting
                return self._generate_fallback_greeting(context)
        else:
            # No AI engine available, use fallback
            return self._generate_fallback_greeting(context)
    
    def _generate_llm_greeting(self, context: GreetingContext, max_length: int) -> str:
        """Generate greeting using the LLM with intelligent prompting"""
        
        # Create a context-aware prompt for greeting generation
        prompt = self._create_greeting_prompt(context, max_length)
        
        # Use the adaptive AI engine to generate the greeting
        # Check if we're in debug mode for thinking display
        show_thinking_mode = False
        try:
            from model_transparency import transparency_engine, TransparencyLevel
            if transparency_engine and transparency_engine.transparency_level == TransparencyLevel.DEBUG:
                show_thinking_mode = True
        except:
            pass
        
        full_response = ""
        for token in self.adaptive_ai_engine.generate_response(
            prompt, 
            max_tokens=50,  # Keep it concise for greetings
            show_thinking=show_thinking_mode  # Show thinking in debug mode
        ):
            full_response += token
        
        # Extract and clean the greeting
        greeting = self._extract_greeting(full_response, max_length)
        return greeting
    
    def _create_greeting_prompt(self, context: GreetingContext, max_length: int) -> str:
        """Create a contextual prompt for greeting generation"""
        
        # Build enhanced context information
        context_parts = []
        
        # Time and location context
        time_info = f"It's {context.time_of_day}"
        if context.timezone:
            time_info += f" in {context.timezone}"
        context_parts.append(time_info)
        
        # System capabilities and performance
        system_info = []
        if context.available_memory_gb > 0:
            if context.available_memory_gb >= 8:
                system_info.append("plenty of RAM")
            elif context.available_memory_gb < 4:
                system_info.append("limited RAM")
        
        if context.cpu_cores > 0:
            if context.cpu_cores >= 8:
                system_info.append("powerful CPU")
            elif context.cpu_cores <= 2:
                system_info.append("efficient CPU")
        
        if context.cpu_usage > 80:
            system_info.append("working hard")
        elif context.cpu_usage < 20:
            system_info.append("running smoothly")
        
        if system_info:
            context_parts.append("system has " + " and ".join(system_info))
        
        # Storage context
        if context.disk_usage > 90:
            context_parts.append("storage is nearly full")
        elif context.disk_usage < 50:
            context_parts.append("plenty of storage available")
        
        # Battery context
        if context.battery_level is not None:
            if context.battery_level > 80:
                context_parts.append("battery is excellent")
            elif context.battery_level < 20:
                context_parts.append("battery is getting low")
        
        # Connected devices context
        if context.connected_devices > 0:
            if context.connected_devices == 1:
                context_parts.append("1 device connected")
            elif context.connected_devices > 3:
                context_parts.append(f"{context.connected_devices} devices connected")
        
        # BLE devices context
        if context.ble_devices and len(context.ble_devices) > 0:
            ble_count = len(context.ble_devices)
            if ble_count == 1:
                context_parts.append("1 Bluetooth device nearby")
            elif ble_count > 2:
                context_parts.append(f"{ble_count} Bluetooth devices nearby")
        
        # Environmental Impact & Sustainability Context
        eco_highlights = []
        if context.energy_saved_kwh > 0:
            if context.energy_saved_kwh >= 1.0:
                eco_highlights.append(f"saved {context.energy_saved_kwh:.1f}kWh energy")
            else:
                eco_highlights.append(f"saved {context.energy_saved_kwh*1000:.0f}Wh energy")
        
        if context.water_saved_ml > 0:
            if context.water_saved_ml >= 1000:
                eco_highlights.append(f"conserved {context.water_saved_ml/1000:.1f}L water")
            else:
                eco_highlights.append(f"conserved {context.water_saved_ml:.0f}ml water")
        
        if context.co2_saved_g > 0:
            if context.co2_saved_g >= 1000:
                eco_highlights.append(f"prevented {context.co2_saved_g/1000:.1f}kg CO2")
            else:
                eco_highlights.append(f"prevented {context.co2_saved_g:.0f}g CO2")
        
        if context.data_transmitted_bytes == 0 and context.total_responses > 0:
            eco_highlights.append("100% private processing")
        
        # Platform and capabilities
        capabilities = []
        if context.voice_enabled:
            capabilities.append("voice synthesis")
        if context.avatar_enabled:
            capabilities.append("avatar visualization")
        if context.ai_model and context.ai_model != "unknown":
            capabilities.append(f"{context.ai_model} AI")
        else:
            capabilities.append("local AI")
        
        context_str = ", ".join(context_parts)
        capabilities_str = ", ".join(capabilities)
        eco_str = ", ".join(eco_highlights) if eco_highlights else ""
        
        # Create an eco-aware, exciting, inspiring prompt!
        if eco_highlights and context.session_eco_impact:
            # Eco-focused greeting when there are environmental achievements
            prompt = f"""Generate an exciting, enthusiastic eco-focused greeting for M1K3 AI assistant! Highlight environmental benefits: {eco_str}. Time: {context.time_of_day.lower()}. Maximum 50 characters. Be energetic, positive, and sustainability-focused! Include eco emojis like 🌱🔋💧. Just the greeting text, nothing else."""
        elif eco_highlights:
            # Include eco context in regular greeting  
            prompt = f"""Generate an exciting, enthusiastic greeting for M1K3 AI assistant! Mention eco benefits: {eco_str}. Time: {context.time_of_day.lower()}. Maximum 50 characters. Be energetic and positive! Include a green emoji. Just the greeting text, nothing else."""
        else:
            # Standard greeting with sustainability undertone
            prompt = f"""Generate an exciting, enthusiastic greeting for M1K3 AI assistant! Emphasize local, private, eco-friendly AI. Time: {context.time_of_day.lower()}. Maximum 50 characters. Be energetic and positive! Just the greeting text, nothing else."""
        
        return prompt
    
    def _extract_greeting(self, llm_response: str, max_length: int) -> str:
        """Extract and clean the greeting from LLM response"""
        
        # Debug logging in debug mode
        debug_mode = False
        try:
            from model_transparency import transparency_engine, TransparencyLevel
            if transparency_engine and transparency_engine.transparency_level == TransparencyLevel.DEBUG:
                debug_mode = True
                print(f"🔍 [GREETING DEBUG] Raw LLM response: '{llm_response[:200]}...'")
        except:
            pass
        
        # Clean the response first
        response = llm_response.strip()
        
        # Handle thinking process format first
        if "**Response:**" in response:
            # Extract content after **Response:** marker
            response_part = response.split("**Response:**", 1)[1].strip()
            if debug_mode:
                print(f"🔍 [GREETING DEBUG] Found **Response:** section: '{response_part[:100]}...'")
            
            # Try to extract quoted content first
            import re
            quoted_match = re.search(r'"([^"]+)"', response_part)
            if quoted_match:
                response = quoted_match.group(1)
                if debug_mode:
                    print(f"🔍 [GREETING DEBUG] Extracted quoted greeting: '{response}'")
            else:
                # Use the first sentence/line if no quotes
                first_sentence = response_part.split('.')[0].split('!')[0].split('?')[0].strip()
                if len(first_sentence) > 5:
                    response = first_sentence
                else:
                    response = response_part
                if debug_mode:
                    print(f"🔍 [GREETING DEBUG] Using unquoted response: '{response}'")
        elif "💭" in response and "**Thinking Process:**" in response:
            # Try to find actual response after thinking
            parts = response.split("**Thinking Process:**")
            if len(parts) > 1:
                # Look for the actual response after thinking
                remaining = parts[1]
                if "**Response:**" in remaining:
                    response = remaining.split("**Response:**")[1].strip()
                    # Try to extract quoted content
                    import re
                    quoted_match = re.search(r'"([^"]+)"', response)
                    if quoted_match:
                        response = quoted_match.group(1)
                        if debug_mode:
                            print(f"🔍 [GREETING DEBUG] Extracted quoted response from thinking: '{response}'")
                else:
                    # Try to find quoted content directly
                    import re
                    quoted_match = re.search(r'"([^"]+)"', remaining)
                    if quoted_match:
                        response = quoted_match.group(1)
                        if debug_mode:
                            print(f"🔍 [GREETING DEBUG] Extracted quoted response: '{response}'")
        
        # Remove common unwanted patterns
        unwanted_patterns = [
            "[Your response]", "[Your answer]", "Your greeting:", "Generate greeting:",
            "Context:", "Features:", "Model:", "Examples:", "Create a", "Your greeting is:",
            "Now, check if", "meets the requirements", "Generate a", "Here's a",
            "💭", "**Thinking Process:**", "**Response:**", "The user wants"
        ]
        
        for pattern in unwanted_patterns:
            response = response.replace(pattern, "").strip()
        
        # Split by common delimiters and find the actual greeting
        lines = [line.strip() for line in response.split('\n') if line.strip()]
        
        if debug_mode:
            print(f"🔍 [GREETING DEBUG] Lines after cleaning: {lines}")
        
        # Look for the generated greeting
        potential_greetings = []
        for line in lines:
            # Skip empty lines and obvious non-greetings
            if not line or len(line) < 5:
                continue
                
            # Skip template-looking lines
            if any(skip in line.lower() for skip in ['generate', 'create', 'example', 'character', 'length']):
                continue
                
            # Remove quotes if present
            if line.startswith('"') and line.endswith('"'):
                line = line[1:-1].strip()
            if line.startswith("'") and line.endswith("'"):
                line = line[1:-1].strip()
            
            # Check if it looks like a greeting - be more permissive
            greeting_words = ['hello', 'hi', 'good', 'welcome', 'm1k3', 'ready', 'morning', 'evening', 'afternoon', 'hey', 'greetings', 'howdy']
            if any(word in line.lower() for word in greeting_words) or len(line) >= 10:
                potential_greetings.append(line)
                if debug_mode:
                    print(f"🔍 [GREETING DEBUG] Found potential greeting: '{line}'")
        
        # Use the first valid greeting found
        if potential_greetings:
            greeting = potential_greetings[0]
            if debug_mode:
                print(f"🔍 [GREETING DEBUG] Selected greeting: '{greeting}'")
        else:
            # If no clear greeting found, try to extract from the first substantial line
            for line in lines:
                if len(line) > 10 and not any(skip in line.lower() for skip in ['generate', 'create', 'example']):
                    greeting = line
                    if debug_mode:
                        print(f"🔍 [GREETING DEBUG] Fallback to substantial line: '{greeting}'")
                    break
            else:
                # Improve the fallback to use context
                greeting = "M1K3 ready!"
                if debug_mode:
                    print(f"🔍 [GREETING DEBUG] Using final fallback: '{greeting}'")
        
        # Clean up any remaining artifacts
        greeting = greeting.replace("- ", "").replace("* ", "").strip()
        
        # Ensure it's within length limit
        if len(greeting) > max_length:
            # Try to truncate at a word boundary
            words = greeting.split()
            truncated = ""
            for word in words:
                if len(truncated + word + " ") <= max_length - 3:  # Leave room for "..."
                    truncated += word + " "
                else:
                    break
            greeting = truncated.strip() + "..." if truncated else greeting[:max_length-3] + "..."
        
        return greeting.strip()
    
    def _generate_fallback_greeting(self, context: GreetingContext) -> str:
        """Generate a context-aware fallback greeting when LLM is unavailable"""
        
        # EXCITING greetings that spark joy!
        base_greetings = [
            f"{context.time_of_day}! Let's create something amazing!",
            f"{context.time_of_day}! Ready to rock your world!",
            f"Hey! M1K3 is fired up and ready!",
            f"{context.time_of_day}! Let's make magic happen!",
            f"Welcome back! Ready for an adventure?",
            f"{context.time_of_day}! Your AI companion is excited!",
            f"Hello! Let's turn ideas into reality!",
            f"{context.time_of_day}! Time to unleash creativity!"
        ]
        
        # Enhanced EXCITING greetings based on system context
        enhanced_greetings = []
        
        # Platform-specific exciting greetings
        if context.platform == "Darwin":
            enhanced_greetings.append(f"{context.time_of_day}! macOS + M1K3 = Pure power!")
            enhanced_greetings.append(f"Apple Silicon meets AI brilliance!")
        elif context.platform == "Linux":
            enhanced_greetings.append(f"{context.time_of_day}! Linux power unleashed!")
            enhanced_greetings.append(f"Open source AI at your command!")
        
        # Performance-aware exciting greetings
        if context.available_memory_gb >= 16:
            enhanced_greetings.append(f"{context.time_of_day}! {context.available_memory_gb:.0f}GB RAM ready to fly!")
            enhanced_greetings.append(f"Massive memory, limitless possibilities!")
        elif context.cpu_cores >= 8:
            enhanced_greetings.append(f"{context.time_of_day}! {context.cpu_cores} cores of pure power!")
            enhanced_greetings.append(f"Multicore mastery activated!")
        
        # Connectivity-aware exciting greetings
        if context.connected_devices > 2:
            enhanced_greetings.append(f"Network synergy achieved! Let's go!")
        elif context.ble_devices and len(context.ble_devices) > 0:
            enhanced_greetings.append(f"Bluetooth connected, possibilities endless!")
        
        # Battery-aware motivational greetings
        if context.battery_level and context.battery_level > 80:
            enhanced_greetings.append(f"Full power mode! Nothing can stop us!")
            enhanced_greetings.append(f"Battery charged, ambitions unlimited!")
        
        # Voice-enabled excitement
        if context.voice_enabled:
            enhanced_greetings.append(f"Voice ready! Let's have a conversation!")
            enhanced_greetings.append(f"Speaking mode activated! Hello world!")
        
        # Avatar excitement
        if context.avatar_enabled:
            enhanced_greetings.append(f"Avatar online! Visual magic awaits!")
            enhanced_greetings.append(f"Your AI companion is visually alive!")
        
        # Combine all possible greetings
        all_greetings = base_greetings + enhanced_greetings
        
        # Use true randomness for variety
        import random
        return random.choice(all_greetings)
    
    def _create_cache_key(self, context: GreetingContext) -> str:
        """Create a cache key from context (rounded to avoid too many variations)"""
        
        # Round values to create reasonable cache buckets
        return f"{context.time_of_day}_{int(context.cpu_usage/10)}_{int(context.memory_percent/10)}_{context.battery_level and int(context.battery_level/20)}_{context.thermal_state}_{context.voice_enabled}_{context.avatar_enabled}"

# Utility functions for easy integration

def create_greeting_context(metrics, m1k3_context: dict = None) -> GreetingContext:
    """Create a GreetingContext from system metrics and M1K3 context with enhanced awareness"""
    
    # Determine time of day
    current_hour = datetime.datetime.now().hour
    if 5 <= current_hour < 12:
        time_of_day = "Good morning"
    elif 12 <= current_hour < 17:
        time_of_day = "Good afternoon"
    elif 17 <= current_hour < 22:
        time_of_day = "Good evening"
    else:
        time_of_day = "Hello"
    
    # Enhanced timezone and locale detection
    timezone = None
    locale = None
    try:
        import locale as locale_module
        import time
        timezone = time.tzname[0] if time.tzname else None
        locale = locale_module.getdefaultlocale()[0] if locale_module.getdefaultlocale()[0] else None
    except:
        pass
    
    # Enhanced system stats
    platform = "unknown"
    cpu_cores = 0
    available_memory_gb = 0.0
    disk_usage = 0.0
    
    try:
        import platform as platform_module
        import psutil
        platform = platform_module.system()
        cpu_cores = psutil.cpu_count()
        
        # Get available memory in GB
        memory_info = psutil.virtual_memory()
        available_memory_gb = memory_info.available / (1024**3)
        
        # Get disk usage
        disk_info = psutil.disk_usage('/')
        disk_usage = (disk_info.used / disk_info.total) * 100
    except:
        pass
    
    # BLE device detection
    ble_devices = []
    connected_devices = 0
    network_status = "unknown"
    
    try:
        import psutil
        # Count network connections as a proxy for connected devices
        connections = psutil.net_connections()
        connected_devices = len([c for c in connections if c.status == 'ESTABLISHED'])
        
        # Network status
        net_stats = psutil.net_if_stats()
        active_interfaces = [name for name, stats in net_stats.items() if stats.isup]
        if 'en0' in active_interfaces or 'eth0' in active_interfaces:
            network_status = "connected"
        elif 'lo0' in active_interfaces or 'lo' in active_interfaces:
            network_status = "local"
    except:
        pass
    
    # Try to detect BLE devices (platform-specific)
    try:
        if platform == "Darwin":  # macOS
            import subprocess
            result = subprocess.run(['system_profiler', 'SPBluetoothDataType'], 
                                  capture_output=True, text=True, timeout=2)
            if result.returncode == 0 and 'Connected:' in result.stdout:
                # Simple count of connected devices
                ble_devices = ['device'] * result.stdout.count('Connected: Yes')
    except:
        pass
    
    # Extract thermal state
    thermal_state = "normal"
    if hasattr(metrics, 'cpu_temp') and metrics.cpu_temp:
        if metrics.cpu_temp > 70:
            thermal_state = "hot"
        elif metrics.cpu_temp < 45:
            thermal_state = "cool"
    
    # Get M1K3 specific context
    ai_model = "Local AI"
    voice_enabled = True
    avatar_enabled = True
    
    if m1k3_context:
        ai_model = m1k3_context.get('ai_model', 'Local AI')
        voice_enabled = m1k3_context.get('voice_enabled', True)
        avatar_enabled = m1k3_context.get('avatar_enabled', True)
    
    # Extract eco metrics if available
    eco_metrics = m1k3_context.get('eco_metrics', {}) if m1k3_context else {}
    energy_saved = eco_metrics.get('energy_saved_kwh', 0.0)
    water_saved = eco_metrics.get('water_saved_ml', 0.0)  
    co2_saved = eco_metrics.get('co2_saved_g', 0.0)
    privacy_score = eco_metrics.get('privacy_score', '100% Local')
    data_transmitted = eco_metrics.get('data_transmitted_bytes', 0)
    total_responses = eco_metrics.get('total_responses', 0)
    
    # Determine if we should highlight eco achievements
    session_eco_impact = (energy_saved > 0.1 or water_saved > 50 or co2_saved > 10)

    return GreetingContext(
        time_of_day=time_of_day,
        cpu_usage=metrics.cpu_usage,
        memory_percent=metrics.memory_percent,
        battery_level=getattr(metrics, 'battery_level', None),
        battery_status=getattr(metrics, 'battery_status', None),
        thermal_state=thermal_state,
        voice_enabled=voice_enabled,
        avatar_enabled=avatar_enabled,
        ai_model=ai_model,
        uptime_hours=getattr(metrics, 'uptime_hours', 0.0),
        # Enhanced context
        timezone=timezone,
        locale=locale,
        connected_devices=connected_devices,
        ble_devices=ble_devices,
        network_status=network_status,
        disk_usage=disk_usage,
        available_memory_gb=available_memory_gb,
        cpu_cores=cpu_cores,
        platform=platform,
        # Eco/Environmental context
        energy_saved_kwh=energy_saved,
        water_saved_ml=water_saved,
        co2_saved_g=co2_saved,
        privacy_score=privacy_score,
        data_transmitted_bytes=data_transmitted,
        total_responses=total_responses,
        session_eco_impact=session_eco_impact
    )

def generate_llm_greeting(adaptive_ai_engine, metrics, m1k3_context: dict = None, max_length: int = 80) -> str:
    """
    Convenient function to generate an LLM-powered greeting
    
    Args:
        adaptive_ai_engine: The AI engine to use for generation
        metrics: System metrics for context
        m1k3_context: M1K3-specific context information
        max_length: Maximum greeting length
        
    Returns:
        Generated greeting string
    """
    
    greeting_engine = LLMGreetingEngine(adaptive_ai_engine)
    context = create_greeting_context(metrics, m1k3_context)
    
    return greeting_engine.generate_greeting(context, max_length)

if __name__ == "__main__":
    # Test the greeting engine
    from system_metrics import SystemMonitor
    
    # Create test context
    monitor = SystemMonitor()
    metrics = monitor.collect_metrics()
    
    test_context = {
        'ai_model': 'Qwen3-0.6B',
        'voice_enabled': True,
        'avatar_enabled': True
    }
    
    # Test fallback greeting (without AI engine)
    greeting_engine = LLMGreetingEngine()
    context = create_greeting_context(metrics, test_context)
    
    print("🧪 Testing LLM Greeting Engine")
    print("=" * 40)
    print(f"Context: {context}")
    print(f"Fallback greeting: {greeting_engine.generate_greeting(context)}")
    print("LLM greeting requires adaptive AI engine integration")