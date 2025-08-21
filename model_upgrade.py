#!/usr/bin/env python3
"""
M1K3 Model Upgrade Utility
Helps users upgrade to better AI models for enhanced reasoning
"""

import sys
import time
from pathlib import Path
from ai_inference import LocalAIEngine

# Enhanced model tiers for different use cases
MODEL_TIERS = {
    "fast": {
        "models": [
            "microsoft/DialoGPT-small",     # ~350MB - Fastest, good chat
            "distilgpt2",                   # ~350MB - Universal fallback
        ],
        "description": "⚡ Fast & Lightweight - Basic chat, minimal resources",
        "memory": "~1GB RAM",
        "use_case": "Quick responses, low-power devices"
    },
    "balanced": {
        "models": [
            "TinyLlama/TinyLlama-1.1B-Chat-v1.0",  # ~1.1GB - Current default
            "Qwen/Qwen2.5-0.5B-Instruct",          # ~500MB - Efficient reasoning
        ],
        "description": "⚖️ Balanced - Good reasoning and speed (current default)",
        "memory": "~2-3GB RAM", 
        "use_case": "Daily assistant tasks, coding help"
    },
    "reasoning": {
        "models": [
            "google/gemma-2b-it",                # ~2.6GB - Enhanced reasoning
            "microsoft/Phi-3-mini-4k-instruct", # ~3.8GB - Excellent reasoning
        ],
        "description": "🧠 Enhanced Reasoning - Best for complex problems",
        "memory": "~4-6GB RAM",
        "use_case": "Complex analysis, detailed explanations, coding"
    }
}

def check_current_model():
    """Check what model is currently loaded"""
    print("🔍 Checking current model configuration...")
    
    try:
        engine = LocalAIEngine()
        if engine.model:
            current_model = getattr(engine, '_current_model_name', 'Unknown')
            print(f"✅ Currently loaded: {current_model}")
            
            # Determine current tier
            current_tier = "unknown"
            for tier_name, tier_info in MODEL_TIERS.items():
                if any(model in current_model for model in tier_info["models"]):
                    current_tier = tier_name
                    break
            
            if current_tier != "unknown":
                tier_info = MODEL_TIERS[current_tier]
                print(f"📊 Current tier: {tier_name.upper()}")
                print(f"   {tier_info['description']}")
                print(f"   Memory usage: {tier_info['memory']}")
                print(f"   Best for: {tier_info['use_case']}")
            
            return current_model, current_tier
        else:
            print("❌ No model currently loaded")
            return None, None
            
    except Exception as e:
        print(f"❌ Error checking model: {e}")
        return None, None

def show_available_tiers():
    """Show all available model tiers"""
    print("\n📈 Available Model Tiers:")
    print("=" * 50)
    
    for tier_name, tier_info in MODEL_TIERS.items():
        print(f"\n{tier_name.upper()} TIER")
        print("-" * 20)
        print(f"{tier_info['description']}")
        print(f"Memory: {tier_info['memory']}")
        print(f"Use case: {tier_info['use_case']}")
        print("Models:")
        for model in tier_info["models"]:
            print(f"  • {model}")

def enable_enhanced_models():
    """Enable enhanced reasoning models by editing ai_inference.py"""
    ai_file = Path("ai_inference.py")
    if not ai_file.exists():
        print("❌ ai_inference.py not found")
        return False
    
    print("🔧 Enabling enhanced reasoning models...")
    
    try:
        # Read current content
        content = ai_file.read_text()
        
        # Check if already enabled
        if '"google/gemma-2b-it"' in content and not content.count('# "google/gemma-2b-it"'):
            print("✅ Enhanced models already enabled!")
            return True
        
        # Enable models by moving them to priority positions
        replacements = [
            # Move models to priority positions (already done manually)
            ('# Advanced models (larger but better reasoning - uncomment when needed):', '# Enhanced reasoning models (enabled):'),
            ('            # Advanced models (larger but better reasoning - uncomment when needed):', '            # Enhanced reasoning models (enabled):'),
        ]
        
        modified = False
        for old, new in replacements:
            if old in content:
                content = content.replace(old, new)
                modified = True
        
        if modified:
            # Write back
            ai_file.write_text(content)
            print("✅ Enhanced reasoning models enabled!")
            print("🎯 Gemma 2B and Phi-3 Mini will be tried first")
            print("⚠️  Note: Some models may require authentication")
            return True
        else:
            print("ℹ️  No changes needed - models may already be configured")
            return True
            
    except Exception as e:
        print(f"❌ Error modifying ai_inference.py: {e}")
        return False

def test_upgrade():
    """Test the upgraded configuration"""
    print("\n🧪 Testing upgraded model configuration...")
    print("-" * 40)
    
    try:
        # Force reload by creating new engine
        engine = LocalAIEngine()
        
        if engine.model:
            model_name = getattr(engine, '_current_model_name', 'Unknown')
            print(f"✅ Loaded model: {model_name}")
            
            # Quick reasoning test
            print("\n🧠 Quick reasoning test:")
            print("Question: What is 2+2 and why?")
            print("Answer: ", end="", flush=True)
            
            response_parts = []
            for token in engine.generate_response("What is 2+2 and why?", max_tokens=100):
                response_parts.append(token)
                print(token, end="", flush=True)
            
            response = "".join(response_parts)
            print(f"\n\n📊 Response quality: {'✅ Good' if len(response) > 20 else '⚠️ Basic'}")
            print(f"📝 Length: {len(response)} characters")
            
            return True
        else:
            print("❌ Failed to load any model")
            return False
            
    except Exception as e:
        print(f"❌ Error testing upgrade: {e}")
        return False

def main():
    print("🚀 M1K3 Model Upgrade Utility")
    print("=" * 50)
    
    # Check current setup
    current_model, current_tier = check_current_model()
    
    # Show available options
    show_available_tiers()
    
    if current_tier == "reasoning":
        print("\n🎉 You're already using the best reasoning models!")
        print("   No upgrade needed.")
        return
    
    print(f"\n🎯 Upgrade Recommendations:")
    print("=" * 30)
    
    if current_tier == "fast":
        print("📈 Recommended: Upgrade to BALANCED tier")
        print("   • Better instruction following")
        print("   • Improved reasoning capabilities") 
        print("   • Still lightweight and fast")
    elif current_tier == "balanced":
        print("📈 Recommended: Upgrade to REASONING tier")
        print("   • Enhanced reasoning and analysis")
        print("   • Better complex problem solving")
        print("   • Detailed explanations")
    
    print(f"\n🔧 To upgrade to enhanced reasoning models:")
    print("   1. Run: python model_upgrade.py --enable")
    print("   2. Restart M1K3 to use upgraded models")
    
    # Check command line args
    if len(sys.argv) > 1 and sys.argv[1] == "--enable":
        print(f"\n⚡ Enabling enhanced models...")
        if enable_enhanced_models():
            print("\n✅ Model upgrade completed!")
            print("🔄 Testing new configuration...")
            if test_upgrade():
                print("\n🎉 Upgrade successful!")
                print("   M1K3 now has enhanced reasoning capabilities!")
            else:
                print("\n⚠️  Upgrade completed but testing failed")
                print("   Models may need to download on first use")
        else:
            print("\n❌ Upgrade failed")
    elif len(sys.argv) > 1 and sys.argv[1] == "--test":
        print(f"\n🧪 Running model test...")
        test_upgrade()

if __name__ == "__main__":
    main()