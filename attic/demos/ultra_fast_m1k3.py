#!/usr/bin/env python3
"""
Ultra-Fast M1K3 Demo - Instant Response (Sub-5 Second Startup)
Direct engine loading without heavy model discovery overhead
"""

import sys
import time
import subprocess
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

class UltraFastM1K3:
    """Minimal M1K3 demo optimized for instant startup"""

    def __init__(self):
        self.ready = False

    def instant_init(self):
        """Sub-5 second initialization"""
        print("⚡ Ultra-Fast M1K3 - Instant Startup Mode")
        print("=" * 50)

        start_time = time.time()

        # Direct KittenTTS loading without engine wrapper
        print("🎤 Direct voice loading...")
        try:
            from src.tts.controllers.kittentts_manager import KittenManager
            from src.utils.text_processors import sanitize_text_for_speech

            if KittenManager.load_model():
                print("✅ Voice ready")
                self.sanitize_text = sanitize_text_for_speech
                self.voice_ready = True
            else:
                print("⚠️ Voice fallback")
                self.voice_ready = False

        except Exception as e:
            print(f"❌ Voice failed: {e}")
            self.voice_ready = False

        # Minimal AI initialization (no heavy discovery)
        print("🤖 Minimal AI loading...")
        try:
            # Try direct ollama connection first (fastest)
            import subprocess

            # Check if Gemma is available in Ollama (quick check)
            result = subprocess.run(['ollama', 'list'], capture_output=True, text=True, timeout=3)
            if 'gemma3:270m' in result.stdout:
                self.ai_mode = "ollama_direct"
                print("✅ Ollama direct ready")
            else:
                # Fallback to basic responses
                self.ai_mode = "fallback"
                print("⚠️ AI fallback mode")

        except Exception as e:
            print(f"⚠️ AI fallback: {e}")
            self.ai_mode = "fallback"

        init_time = time.time() - start_time
        print(f"⚡ Ultra-fast startup: {init_time:.2f}s")
        print()

        self.ready = True
        return True

    def direct_ai_response(self, question: str) -> str:
        """Direct AI response without heavy wrapper"""
        try:
            if self.ai_mode == "ollama_direct":
                # Direct ollama call for maximum speed
                cmd = ['ollama', 'run', 'gemma3:270m', question]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)

                if result.returncode == 0 and result.stdout.strip():
                    response = result.stdout.strip()
                    # Quick response truncation for speed
                    sentences = response.split('. ')
                    if len(sentences) > 3:
                        response = '. '.join(sentences[:3]) + '.'
                    return response
                else:
                    return self.fallback_response(question)
            else:
                return self.fallback_response(question)

        except Exception as e:
            print(f"⚠️ AI error: {e}")
            return self.fallback_response(question)

    def fallback_response(self, question: str) -> str:
        """Quick fallback responses"""
        fallbacks = {
            "consciousness": "Consciousness is our awareness of our own thoughts and experiences. It's what makes you feel like 'you' - the inner experience of being aware and thinking.",
            "anxiety": "Meditation can help with anxiety by training your mind to focus on the present moment instead of worrying about future problems. Regular practice can reduce stress hormones.",
            "meditation": "Meditation is about training attention and awareness. Start with just 5 minutes daily, focusing on your breathing. It's like exercise for your mind.",
            "philosophy": "Philosophy asks big questions about existence, knowledge, and values. It's thinking carefully about life's fundamental questions.",
            "ai": "I'm M1K3, a privacy-focused AI assistant that runs locally on your device. All processing happens here - no data goes to the cloud.",
        }

        # Simple keyword matching for fast responses
        question_lower = question.lower()
        for key, response in fallbacks.items():
            if key in question_lower:
                return response

        # Default response
        return "That's an interesting question. As M1K3, I process everything locally on your device for complete privacy. What specific aspect would you like to explore?"

    def instant_speech(self, text: str):
        """Instant speech synthesis"""
        if not text:
            return

        try:
            # Always sanitize first (CRITICAL FIX)
            if hasattr(self, 'sanitize_text'):
                clean_text = self.sanitize_text(text)
            else:
                # Manual sanitization if function unavailable
                clean_text = text.replace('**', '').replace('*', '').replace('`', '')

            if not clean_text.strip():
                return

            print(f"🎤 {clean_text[:60]}...")

            if self.voice_ready:
                # Direct KittenTTS call
                from src.tts.controllers.kittentts_manager import KittenManager

                # Pre-warm for speed
                if not KittenManager.prewarmed:
                    KittenManager.prewarm()

                # Generate and play directly
                audio = KittenManager.generate(clean_text)
                if audio is not None:
                    # Direct audio playback
                    try:
                        import sounddevice as sd
                        sd.play(audio, samplerate=24000)
                        sd.wait()
                    except:
                        # System fallback
                        self.system_speak(clean_text)
                else:
                    self.system_speak(clean_text)
            else:
                self.system_speak(clean_text)

        except Exception as e:
            print(f"⚠️ Speech error: {e}")
            try:
                self.system_speak(clean_text if 'clean_text' in locals() else text)
            except:
                pass

    def system_speak(self, text: str):
        """Fast system TTS"""
        try:
            # Quick macOS TTS
            subprocess.run(['say', '-r', '200', text], timeout=30)
        except:
            pass

    def demo_conversation(self):
        """Ultra-fast conversation demo"""
        if not self.instant_init():
            print("❌ Initialization failed")
            return

        # Brand message
        brand = "I'm M1K3, your privacy-focused local AI assistant. 100% local processing, zero bytes transmitted. Let's have a conversation!"
        print(f"🤖 M1K3: {brand}")
        self.instant_speech(brand)

        print("\n💬 Ultra-Fast Chat Mode")
        print("Ask me about: consciousness, meditation, anxiety, philosophy, AI")
        print("Type 'quit' to exit")
        print("-" * 50)

        while True:
            try:
                question = input("\n🗣️ You: ").strip()

                if not question:
                    continue

                if question.lower() in ['quit', 'exit', 'q']:
                    break

                # Ultra-fast response
                start_time = time.time()

                print(f"🤖 M1K3: ", end="", flush=True)
                response = self.direct_ai_response(question)

                if response:
                    print(response)
                    self.instant_speech(response)

                response_time = time.time() - start_time
                print(f"⚡ Total time: {response_time:.2f}s")

            except KeyboardInterrupt:
                print("\n👋 Ultra-fast demo interrupted")
                break
            except Exception as e:
                print(f"❌ Error: {e}")

        print("\n✨ Ultra-fast M1K3 complete!")

def quick_test():
    """Quick functionality test"""
    print("🧪 Ultra-Fast Test")
    demo = UltraFastM1K3()

    if demo.instant_init():
        question = "What is consciousness?"
        print(f"\n💬 Test: {question}")

        start_time = time.time()
        response = demo.direct_ai_response(question)

        if response:
            print(f"🤖 Response: {response}")
            demo.instant_speech(response)

        total_time = time.time() - start_time
        print(f"⚡ Total test time: {total_time:.2f}s")
        print("✅ Ultra-fast test complete!")
    else:
        print("❌ Test failed")

def main():
    """Main entry point"""
    if len(sys.argv) > 1 and sys.argv[1] == "--test":
        quick_test()
    else:
        demo = UltraFastM1K3()
        demo.demo_conversation()

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n⚡ Ultra-fast demo interrupted!")
    except Exception as e:
        print(f"\n❌ Ultra-fast demo failed: {e}")