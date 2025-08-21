#!/usr/bin/env python3
"""
Autonomous Pixel Art Generator for M1K3
LLM-driven pixel art creation system that scales with model complexity
"""

import json
import random
import time
from typing import Dict, List, Tuple, Optional
from enum import Enum
from dataclasses import dataclass

class ComplexityLevel(Enum):
    """Pixel art complexity levels based on model capabilities"""
    SIMPLE = "simple"      # TinyLlama: 8x8, basic patterns
    MODERATE = "moderate"  # Smaller models: 16x16, simple sprites  
    DETAILED = "detailed"  # Medium models: 24x24, detailed sprites
    COMPLEX = "complex"    # Large models: 32x32, complex scenes
    MASTERPIECE = "masterpiece"  # Huge models: 48x48+, artistic compositions

@dataclass
class PixelArtPrompt:
    """Structured prompt for LLM pixel art generation"""
    complexity: ComplexityLevel
    canvas_size: Tuple[int, int]
    color_palette: List[str]
    theme: str
    constraints: List[str]
    inspiration: str
    technical_specs: Dict[str, any]

class AutonomousPixelArtGenerator:
    """Generates pixel art prompts and coordinates with LLM"""
    
    def __init__(self):
        self.gameboy_palettes = {
            "classic": ["#9BBC0F", "#8BAC0F", "#306230", "#0F380F"],
            "ocean": ["#6B73FF", "#0000FF", "#0F0F23", "#000040"], 
            "sunset": ["#FFDC00", "#FF8800", "#CC4400", "#661100"],
            "forest": ["#9BBC0F", "#8BAC0F", "#306230", "#0F380F"],
            "crystal": ["#E0E6FF", "#B7C7FF", "#6B73FF", "#000040"],
            "blood": ["#FF0000", "#CC0000", "#660000", "#330000"]
        }
        
        self.themes = {
            ComplexityLevel.SIMPLE: [
                "geometric pattern", "simple emoji", "basic symbol", "weather icon",
                "food item", "simple animal face", "house", "tree"
            ],
            ComplexityLevel.MODERATE: [
                "character portrait", "landscape scene", "detailed icon", "robot design",
                "fantasy creature", "space scene", "cityscape", "abstract art"
            ],
            ComplexityLevel.DETAILED: [
                "character with background", "detailed landscape", "mechanical design",
                "architectural structure", "complex creature", "story scene", "technical diagram"
            ],
            ComplexityLevel.COMPLEX: [
                "multi-character scene", "detailed environment", "dynamic action scene",
                "complex narrative moment", "technical cutaway", "artistic composition"
            ],
            ComplexityLevel.MASTERPIECE: [
                "epic scene composition", "photorealistic interpretation", "abstract masterpiece",
                "complex narrative sequence", "technical marvel", "artistic statement"
            ]
        }
        
        self.model_complexity_map = {
            "TinyLlama": ComplexityLevel.SIMPLE,
            "DialoGPT-small": ComplexityLevel.SIMPLE, 
            "distilgpt2": ComplexityLevel.MODERATE,
            "gpt2": ComplexityLevel.MODERATE,
            "SmolLM": ComplexityLevel.SIMPLE,
            "Phi-3-mini": ComplexityLevel.DETAILED,
            "Gemma-2B": ComplexityLevel.DETAILED,
            "Llama-3-8B": ComplexityLevel.COMPLEX,
            "Claude-3": ComplexityLevel.MASTERPIECE,
            "GPT-4": ComplexityLevel.MASTERPIECE
        }
    
    def determine_complexity(self, model_name: str, model_size: Optional[str] = None) -> ComplexityLevel:
        """Determine complexity level based on current AI model"""
        # Extract model size if available
        if model_size:
            if "7B" in model_size or "8B" in model_size:
                return ComplexityLevel.COMPLEX
            elif "13B" in model_size or "15B" in model_size:
                return ComplexityLevel.MASTERPIECE
            elif "3B" in model_size or "2B" in model_size:
                return ComplexityLevel.DETAILED
            elif "1B" in model_size or "135M" in model_size:
                return ComplexityLevel.SIMPLE
        
        # Fallback to name mapping
        for model_key, complexity in self.model_complexity_map.items():
            if model_key.lower() in model_name.lower():
                return complexity
                
        return ComplexityLevel.MODERATE  # Safe default
    
    def generate_pixel_art_prompt(self, current_model: str, context: Dict = None) -> PixelArtPrompt:
        """Generate a structured pixel art creation prompt"""
        complexity = self.determine_complexity(current_model)
        
        # Canvas size based on complexity
        canvas_sizes = {
            ComplexityLevel.SIMPLE: (8, 8),
            ComplexityLevel.MODERATE: (16, 16), 
            ComplexityLevel.DETAILED: (24, 24),
            ComplexityLevel.COMPLEX: (32, 32),
            ComplexityLevel.MASTERPIECE: (48, 48)
        }
        
        canvas_size = canvas_sizes[complexity]
        palette_name = random.choice(list(self.gameboy_palettes.keys()))
        palette = self.gameboy_palettes[palette_name]
        theme = random.choice(self.themes[complexity])
        
        # Context-aware theming
        if context:
            theme = self._contextualize_theme(theme, context)
        
        constraints = self._generate_constraints(complexity, canvas_size, palette)
        inspiration = self._generate_inspiration(complexity, theme)
        technical_specs = self._generate_technical_specs(complexity, canvas_size)
        
        return PixelArtPrompt(
            complexity=complexity,
            canvas_size=canvas_size,
            color_palette=palette,
            theme=theme,
            constraints=constraints,
            inspiration=inspiration,
            technical_specs=technical_specs
        )
    
    def _contextualize_theme(self, base_theme: str, context: Dict) -> str:
        """Make theme context-aware based on system state"""
        contextual_modifiers = []
        
        # Battery-based themes
        if context.get('battery_percent'):
            if context['battery_percent'] < 20:
                contextual_modifiers.append("low energy")
            elif context['battery_percent'] > 80:
                contextual_modifiers.append("high energy")
        
        # Network-based themes  
        if context.get('network_connected') == False:
            contextual_modifiers.append("disconnected")
        elif context.get('wifi_strength'):
            if context['wifi_strength'] > 80:
                contextual_modifiers.append("well connected")
        
        # Temperature-based themes
        if context.get('cpu_temp'):
            if context['cpu_temp'] > 70:
                contextual_modifiers.append("hot")
            elif context['cpu_temp'] < 40:
                contextual_modifiers.append("cool")
        
        # Eco metrics
        if context.get('eco_savings'):
            if context['eco_savings'] > 100:
                contextual_modifiers.append("eco-friendly")
        
        if contextual_modifiers:
            modifier = random.choice(contextual_modifiers)
            return f"{modifier} {base_theme}"
        
        return base_theme
    
    def _generate_constraints(self, complexity: ComplexityLevel, canvas_size: Tuple[int, int], palette: List[str]) -> List[str]:
        """Generate technical constraints for the pixel art"""
        constraints = [
            f"Canvas size: exactly {canvas_size[0]}x{canvas_size[1]} pixels",
            f"Color palette: only use these 4 colors: {', '.join(palette)}",
            "Each pixel must be clearly defined",
            "No anti-aliasing or gradients - pure pixel art",
        ]
        
        if complexity == ComplexityLevel.SIMPLE:
            constraints.extend([
                "Keep design minimalist and clear",
                "Focus on basic recognizable shapes",
                "Avoid fine details"
            ])
        elif complexity == ComplexityLevel.MODERATE:
            constraints.extend([
                "Include some detail but keep it readable",
                "Use dithering patterns for shading if needed",
                "Clear subject matter"
            ])
        elif complexity in [ComplexityLevel.DETAILED, ComplexityLevel.COMPLEX]:
            constraints.extend([
                "Rich detail appropriate for canvas size",
                "Advanced dithering and texture techniques",
                "Complex composition allowed"
            ])
        elif complexity == ComplexityLevel.MASTERPIECE:
            constraints.extend([
                "Push the boundaries of pixel art",
                "Sophisticated artistic techniques",
                "Complex narrative or artistic expression"
            ])
        
        return constraints
    
    def _generate_inspiration(self, complexity: ComplexityLevel, theme: str) -> str:
        """Generate artistic inspiration text"""
        style_references = {
            ComplexityLevel.SIMPLE: "early Game Boy games, simple icons, basic emoji",
            ComplexityLevel.MODERATE: "16-bit era sprites, detailed icons, classic arcade games", 
            ComplexityLevel.DETAILED: "advanced 16-bit art, detailed character sprites, indie pixel art",
            ComplexityLevel.COMPLEX: "modern pixel art, complex game environments, artistic pixel compositions",
            ComplexityLevel.MASTERPIECE: "pixel art masters, museum-quality pixel compositions, pushing medium boundaries"
        }
        
        return f"Create {theme} inspired by {style_references[complexity]}. Channel the aesthetic of classic Gameboy Color games with their distinctive 4-color palettes and crisp pixel precision."
    
    def _generate_technical_specs(self, complexity: ComplexityLevel, canvas_size: Tuple[int, int]) -> Dict:
        """Generate technical specifications"""
        return {
            "pixel_perfect": True,
            "antialiasing": False,
            "dithering_allowed": complexity.value in ["detailed", "complex", "masterpiece"],
            "animation_frames": 1 if complexity in [ComplexityLevel.SIMPLE, ComplexityLevel.MODERATE] else random.randint(1, 4),
            "expected_generation_time": self._estimate_generation_time(complexity),
            "validation_rules": [
                "Every pixel must be one of the 4 palette colors",
                f"Exact dimensions: {canvas_size[0]}x{canvas_size[1]}",
                "No partial pixels or blending"
            ]
        }
    
    def _estimate_generation_time(self, complexity: ComplexityLevel) -> str:
        """Estimate how long generation should take"""
        times = {
            ComplexityLevel.SIMPLE: "10-30 seconds",
            ComplexityLevel.MODERATE: "30-60 seconds",
            ComplexityLevel.DETAILED: "1-2 minutes", 
            ComplexityLevel.COMPLEX: "2-5 minutes",
            ComplexityLevel.MASTERPIECE: "5-10 minutes"
        }
        return times[complexity]
    
    def create_llm_prompt(self, pixel_prompt: PixelArtPrompt) -> str:
        """Convert PixelArtPrompt into actual LLM prompt text"""
        prompt = f"""# Pixel Art Generation Task

You are a master pixel artist creating Gameboy Color-style artwork. Your task is to design {pixel_prompt.theme} with the following specifications:

## Canvas & Technical Requirements
- **Dimensions**: {pixel_prompt.canvas_size[0]}x{pixel_prompt.canvas_size[1]} pixels (exactly)
- **Complexity Level**: {pixel_prompt.complexity.value.title()}
- **Color Palette**: {', '.join(pixel_prompt.color_palette)} (Gameboy Color authentic)
- **Style**: Pure pixel art, no anti-aliasing

## Creative Brief
{pixel_prompt.inspiration}

## Technical Constraints
"""
        
        for constraint in pixel_prompt.constraints:
            prompt += f"- {constraint}\n"
        
        prompt += f"""
## Output Format
Please provide:
1. **Concept Description**: Brief explanation of your design concept
2. **Pixel Map**: A {pixel_prompt.canvas_size[0]}x{pixel_prompt.canvas_size[1]} grid where each cell contains the hex color code
3. **Design Notes**: Technical decisions and artistic choices

## Example Output Format (for reference):
```
Concept: A simple tree sprite with classic Gameboy aesthetics
Pixel Map:
#9BBC0F #9BBC0F #306230 #306230 #306230 #9BBC0F #9BBC0F #9BBC0F
#9BBC0F #306230 #8BAC0F #8BAC0F #8BAC0F #306230 #9BBC0F #9BBC0F
...continuing for all {pixel_prompt.canvas_size[1]} rows

Design Notes: Used classic green palette, focused on clear silhouette
```

Create something that showcases the {pixel_prompt.complexity.value} level capabilities while staying true to authentic Gameboy Color aesthetics!
"""
        
        return prompt
    
    def should_generate_art(self, system_state: Dict) -> bool:
        """Determine if system should generate pixel art now"""
        # Generate art during idle periods
        if system_state.get('is_idle', False):
            return True
        
        # Generate based on system health (happy system = more art)
        if system_state.get('battery_percent', 0) > 50 and system_state.get('cpu_usage', 100) < 30:
            return random.random() < 0.3  # 30% chance during good conditions
        
        # Generate based on eco achievements
        if system_state.get('eco_milestone_reached', False):
            return True
            
        return False
    
    def parse_llm_pixel_art_output(self, llm_output: str) -> Optional[Dict]:
        """Parse LLM output into structured pixel art data"""
        try:
            lines = llm_output.strip().split('\n')
            pixel_map = []
            concept = ""
            notes = ""
            
            parsing_map = False
            
            for line in lines:
                line = line.strip()
                
                if line.startswith("Concept:"):
                    concept = line.replace("Concept:", "").strip()
                elif line.startswith("Pixel Map:"):
                    parsing_map = True
                    continue
                elif line.startswith("Design Notes:"):
                    parsing_map = False
                    notes = line.replace("Design Notes:", "").strip()
                elif parsing_map and line and not line.startswith("```"):
                    # Parse hex color row
                    colors = [color.strip() for color in line.split() if color.strip().startswith('#')]
                    if colors:
                        pixel_map.append(colors)
            
            if pixel_map:
                return {
                    "concept": concept,
                    "pixel_map": pixel_map,
                    "notes": notes,
                    "timestamp": time.time(),
                    "canvas_size": (len(pixel_map[0]), len(pixel_map))
                }
        
        except Exception as e:
            print(f"Error parsing pixel art output: {e}")
        
        return None

# Integration prompts for different idle states
IDLE_PROMPTS = {
    "system_healthy": """The system is running well with good battery and low CPU usage. Create a cheerful, energetic pixel art that reflects this positive state. Use bright colors and uplifting themes.""",
    
    "low_battery": """The system is running on low battery. Create a pixel art that reflects energy conservation - perhaps something sleepy, minimal, or related to saving power. Use darker, more subdued colors.""",
    
    "high_performance": """The system is under heavy load with high CPU usage. Create a pixel art that shows intensity, work, or processing - perhaps gears, lightning, or dynamic movement patterns.""",
    
    "network_issues": """The system has poor or no network connectivity. Create a pixel art that reflects isolation, offline mode, or self-sufficiency themes. Maybe a lighthouse, island, or hermit scene.""",
    
    "eco_milestone": """The system has achieved significant eco savings (energy, water, CO2). Create a pixel art celebrating environmental consciousness - trees, clean energy, nature themes.""",
    
    "first_boot": """This is the first time the system is starting up. Create a welcoming, introduction-themed pixel art - perhaps a greeting, welcome sign, or friendly character.""",
    
    "long_session": """The system has been running for many hours in a long session. Create a pixel art that reflects endurance, dedication, or the passage of time."""
}

def create_contextual_pixel_prompt(system_state: Dict, model_name: str) -> str:
    """Create a complete contextual prompt for autonomous pixel art generation"""
    generator = AutonomousPixelArtGenerator()
    
    # Determine context
    context_key = "system_healthy"  # default
    
    if system_state.get('battery_percent', 100) < 20:
        context_key = "low_battery"
    elif system_state.get('cpu_usage', 0) > 80:
        context_key = "high_performance" 
    elif not system_state.get('network_connected', True):
        context_key = "network_issues"
    elif system_state.get('eco_milestone_reached', False):
        context_key = "eco_milestone"
    elif system_state.get('session_duration_hours', 0) > 8:
        context_key = "long_session"
    elif system_state.get('first_boot', False):
        context_key = "first_boot"
    
    # Generate base prompt
    pixel_prompt = generator.generate_pixel_art_prompt(model_name, system_state)
    base_prompt = generator.create_llm_prompt(pixel_prompt)
    
    # Add contextual guidance
    context_guidance = IDLE_PROMPTS.get(context_key, "")
    
    final_prompt = f"""{base_prompt}

## Contextual Guidance
{context_guidance}

Remember: This pixel art will be displayed on the M1K3 avatar system and should reflect the current system state and user's computing environment. Make it meaningful and contextually appropriate!
"""
    
    return final_prompt

if __name__ == "__main__":
    # Test the system
    generator = AutonomousPixelArtGenerator()
    
    # Simulate different system states
    test_contexts = [
        {"model": "TinyLlama", "battery_percent": 95, "cpu_usage": 15},
        {"model": "Gemma-2B", "battery_percent": 15, "cpu_usage": 45},
        {"model": "GPT-4", "battery_percent": 85, "eco_milestone_reached": True}
    ]
    
    for context in test_contexts:
        prompt = create_contextual_pixel_prompt(context, context["model"])
        print(f"\n{'='*80}")
        print(f"Context: {context}")
        print(f"{'='*80}")
        print(prompt[:500] + "...")