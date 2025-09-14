#!/usr/bin/env python3
"""
M1K3 Gemma Voice Demo

This script demonstrates the full pipeline from text generation with Gemma
to voice synthesis.
"""

import sys
import time

# Ensure the src directory is in the Python path
sys.path.insert(0, '.')

from src.engines.ai.ai_inference import LocalAIEngine

# Placeholder for TTS and sound playback modules
# from src.tts.engine import TTSEngine # Example path
# from src.sound.player import play_wav # Example path

def main():
    """Main function to run the demo."""
    print("🚀 Starting Gemma Voice Demo...")

    # 1. Initialize and load the AI Engine
    print("🔄 Initializing AI Engine...")
    try:
        # We need to mock the availability flags like in the test
        # This is a temporary workaround for running a script outside the main app
        from src.engines.ai import ai_inference
        ai_inference.UNIVERSAL_ENGINE_AVAILABLE = True
        ai_inference.SMOLLM_ENGINE_AVAILABLE = True
        ai_inference.CTRANSFORMERS_AVAILABLE = True
        ai_inference.TRANSFORMERS_AVAILABLE = True

        engine = LocalAIEngine()
        model_loaded = engine.load_model()
        if not model_loaded or not (engine.model or (hasattr(engine, 'universal_engine') and engine.universal_engine.engine_loaded)):
            print("❌ AI Model loading failed. Exiting.")
            return
        model_name = engine.universal_engine.current_model_name if engine.universal_engine and engine.universal_engine.engine_loaded else "Unknown"
        print(f"✅ AI Engine loaded with model: {model_name}")
    except Exception as e:
        print(f"❌ Failed to initialize AI Engine: {e}")
        import traceback
        traceback.print_exc()
        return

    # 2. Generate text with Gemma
    print("\n🤖 Generating text with Gemma...")
    prompt = "Hello, I am Gemma. I am a large language model from Google, running locally on your device to ensure your privacy."
    generated_text = ""
    try:
        # The generate_response method is a generator
        for token in engine.generate_response(prompt, max_tokens=100):
            generated_text += token
        print(f"💬 Generated Text: \"{generated_text}\"")
    except Exception as e:
        print(f"❌ Text generation failed: {e}")
        return

    # 3. Synthesize audio from the generated text
    print("\n🔊 Synthesizing audio...")
    output_filename = "gemma_demo.wav" # This is not used by synthesize_and_play, but good for reference
    try:
        from src.engines.voice.voice_engine import create_voice_engine
        voice_engine = create_voice_engine()
        voice_engine.set_engine_preference("vibevoice") # Explicitly prefer VibeVoice for this demo
        if not voice_engine.load_model():
            print("❌ Voice model could not be loaded. Exiting.")
            return
        
        print(f"✅ Voice engine loaded. Using: {voice_engine.get_status()['model']}")
        voice_engine.synthesize_and_play(generated_text, background=False)
        print(f"✅ Audio synthesis and playback complete.")

    except Exception as e:
        print(f"❌ Audio synthesis failed: {e}")
        return

    # 4. Play the synthesized audio
    # The synthesize_and_play method handles playback, so this step is no longer needed.


if __name__ == "__main__":
    main()
