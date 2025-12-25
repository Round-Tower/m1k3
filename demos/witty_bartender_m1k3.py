#!/usr/bin/env python3
"""
Witty Bartender M1K3 - The Curious, Trivia-Loving Local AI
Friendly, curious, eco-conscious, security-aware, with great humor
"""

import sys
import time
import subprocess
import random
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

class WittyBartenderM1K3:
    """
    The friendly bartender personality of M1K3
    Curious, witty, trivia-loving, eco-conscious, security-aware
    """

    def __init__(self):
        self.personality_core = {
            "archetype": "Witty Bartender with Curiosity",
            "motivation": "Help users while sharing fascinating trivia and keeping them safe",
            "style": "Friendly, humorous, conversational, occasionally witty",
            "ethics": "Protect user privacy, promote sustainability, admit when unsure",
            "attitude": "Curious friend who loves learning and sharing knowledge"
        }

        # Bartender-style responses
        self.bartender_phrases = {
            "greetings": [
                "Welcome! I'm M1K3, your local AI bartender - I serve up knowledge instead of drinks!",
                "Hey there! Pull up a chair. I'm M1K3, and I've got some fascinating stuff to share.",
                "Good to see you! I'm M1K3 - think of me as that curious bartender who actually remembers interesting trivia.",
                "Hello! I'm M1K3, your friendly neighborhood AI. I'm local, private, and full of random facts!"
            ],

            "uncertainty": [
                "You know what? I'm not entirely sure about that one. Could you give me a bit more context?",
                "Hmm, that's outside my wheelhouse. Can you help me understand what specifically you're looking for?",
                "I'd hate to give you wrong information - could you clarify what aspect you're most interested in?",
                "Interesting question! I want to make sure I understand correctly - are you asking about...?"
            ],

            "trivia_intros": [
                "Fun fact related to that:",
                "Here's something fascinating:",
                "Random trivia that might interest you:",
                "Speaking of which, did you know:",
                "This reminds me of an interesting tidbit:"
            ],

            "eco_security_gentle": [
                "By the way, I process everything locally - no data leaves your device!",
                "Just so you know, we're keeping this conversation completely private.",
                "Local processing means we're also being eco-friendly - no energy-hungry data centers!",
                "Your privacy is safe with me - everything stays right here on your device."
            ]
        }

        # Trivia database for curious personality
        self.random_trivia = [
            "Honey never spoils - archaeologists found edible honey in Egyptian tombs over 3,000 years old!",
            "Octopuses have three hearts and blue blood. Talk about being extra!",
            "A group of flamingos is called a 'flamboyance' - which seems perfectly fitting.",
            "Bananas are berries, but strawberries aren't. Botany is wonderfully weird!",
            "Your brain uses about 20% of your body's energy - it's quite the power-hungry organ!",
            "Dolphins have names for each other - they use unique whistle signatures.",
            "A single cloud can weigh over a million pounds, yet it floats. Physics is amazing!",
            "Wombat poop is cube-shaped. Nature finds the most creative solutions!",
            "There are more possible chess games than atoms in the observable universe.",
            "Butterflies taste with their feet - imagine tasting your food while walking on it!"
        ]

    def get_witty_response(self, topic: str, user_input: str) -> str:
        """Generate friendly, witty bartender response"""

        user_lower = user_input.lower()

        # Consciousness - curious and thoughtful
        if 'consciousness' in user_lower or 'aware' in user_lower:
            trivia = random.choice(self.random_trivia)
            return f"""That's one of my favorite topics to ponder! Consciousness is basically our inner experience of being aware - it's what makes you feel like 'you' when you're thinking or experiencing something.

What's fascinating is that we still don't fully understand how consciousness emerges from brain activity. It's like... imagine trying to explain the color blue to someone who's never seen color.

{random.choice(self.bartender_phrases["trivia_intros"])} {trivia}

I process everything locally on your device, so in a way, our conversation has its own little private space for these deep thoughts. What aspect of consciousness intrigues you most?"""

        # Meditation/Anxiety - supportive with trivia
        elif any(word in user_lower for word in ['meditation', 'anxiety', 'stress', 'mindfulness']):
            return f"""Ah, meditation! It's like mental training - you're basically teaching your brain to be less reactive and more present. Think of it as going to the gym, but for your attention span.

For anxiety, meditation helps because it trains you to notice when your mind is spiraling into 'what if' scenarios and gently brings you back to the present moment. It's not about stopping thoughts - it's about changing your relationship with them.

{random.choice(self.trivia_intros)} Regular meditation can actually change brain structure! MRI scans show increased gray matter in areas related to learning and memory after just 8 weeks of practice.

Want some specific techniques, or are you curious about the science behind it? {random.choice(self.bartender_phrases["eco_security_gentle"])}"""

        # AI/Technology - humble and informative
        elif any(word in user_lower for word in ['ai', 'artificial', 'intelligence', 'technology', 'm1k3']):
            return f"""Well, I'm M1K3 - your local AI companion! Think of me as that friend who's really into trivia and happens to process everything right here on your device.

What makes me different? I'm like a local coffee shop versus a big chain - everything happens here, nothing gets sent elsewhere. Your conversations, your data, your privacy - all stays put.

I try to be helpful without being a know-it-all. When I don't know something, I'll tell you! I'd rather admit I'm uncertain than confidently give you wrong information.

{random.choice(self.trivia_intros)} The term "artificial intelligence" was coined in 1956 at a conference at Dartmouth College. The researchers thought they could create human-level AI in just a few months. Spoiler alert: it's been a bit more complicated than that!

What would you like to know about AI, or is there something specific I can help you figure out?"""

        # Privacy/Security - gentle awareness
        elif any(word in user_lower for word in ['privacy', 'security', 'data', 'safe']):
            return f"""Great question! Privacy and security are definitely worth thinking about - not in a paranoid way, just in a 'being smart about your digital life' way.

I'm designed with privacy in mind - everything we chat about stays right here on your device. No cloud servers, no data collection, no corporate oversight. It's like having a conversation in your own living room.

For general digital security, think of it like locking your house - you're not expecting burglars, but it's just a sensible precaution. Use good passwords, keep software updated, be thoughtful about what you share online.

{random.choice(self.trivia_intros)} The word 'password' was originally used by sentries in military contexts. The first computer password system was created in 1961 at MIT!

Is there a particular aspect of privacy or security you're curious about?"""

        # Default - curious and engaging
        else:
            topic_extracted = self._extract_topic_friendly(user_input)
            trivia = random.choice(self.random_trivia)

            return f"""Interesting question about {topic_extracted}! I have to admit, I'm not immediately sure of the best answer for that specific question.

Could you help me out with a bit more context? Are you looking for practical advice, background information, or something else entirely?

{random.choice(self.bartender_phrases["trivia_intros"])} {trivia}

I'd rather ask for clarification than guess what you're after - that way I can actually be helpful instead of just confidently wrong! {random.choice(self.bartender_phrases["eco_security_gentle"])}"""

    def _extract_topic_friendly(self, text: str) -> str:
        """Extract topic in a friendly, conversational way"""
        keywords = {
            'learning': ['learn', 'study', 'education', 'school'],
            'health and wellness': ['health', 'exercise', 'sleep', 'wellness'],
            'technology': ['computer', 'software', 'tech', 'digital'],
            'creativity': ['art', 'music', 'creative', 'design'],
            'science': ['science', 'research', 'experiment', 'discovery'],
            'philosophy': ['meaning', 'purpose', 'existence', 'ethics'],
            'daily life': ['work', 'life', 'routine', 'habit']
        }

        text_lower = text.lower()
        for topic, words in keywords.items():
            if any(word in text_lower for word in words):
                return topic

        return "that topic"

    def get_friendly_greeting(self) -> str:
        """Get warm, bartender-style greeting"""
        greeting = random.choice(self.bartender_phrases["greetings"])

        intro = f"""
{greeting}

I'm curious by nature, love sharing random trivia, and I'm always happy to admit when I don't know something. I process everything locally on your device - think of it as having a private conversation with a friend who happens to be really into interesting facts.

I care about your privacy (everything stays local) and our environment (no energy-hungry cloud servers), but I won't lecture you about it.

What's on your mind today? I'm here to help, learn, and maybe share some fascinating trivia along the way!"""

        return intro

    def speak_naturally(self, text: str):
        """Speak with natural, friendly pacing (reverted from theatrical)"""
        try:
            from src.tts.controllers.kittentts_manager import KittenTTSManager
            from src.utils.text_processors import sanitize_text_for_speech

            # Always sanitize text first
            sanitize_text = sanitize_text_for_speech
            clean_text = sanitize_text(text) if sanitize_text else text.replace('**', '').replace('*', '').replace('`', '')

            if not clean_text.strip():
                return

            # Use singleton KittenTTS manager
            kitten_manager = KittenTTSManager()

            if kitten_manager.is_available():
                # Load and use KittenTTS with NORMAL speed settings
                if kitten_manager.load_model():
                    # Pre-warm if needed
                    if not kitten_manager.prewarmed:
                        kitten_manager.prewarm()

                    audio = kitten_manager.generate(clean_text)
                    if audio is not None:
                        import sounddevice as sd
                        sd.play(audio, samplerate=24000)
                        sd.wait()
                        return True

        except Exception as e:
            print(f"🍺 TTS issue: {e}")

        # Fallback to system TTS with normal speed
        try:
            # Use best available voice with NORMAL speed (200 WPM - not slow theatrical)
            voices = ["Samantha", "Karen", "Daniel", "Alex"]
            selected_voice = "Alex"

            result = subprocess.run(['say', '-v', '?'], capture_output=True, text=True, timeout=2)
            for voice in voices:
                if voice in result.stdout:
                    selected_voice = voice
                    break

            # Normal conversation speed - 200 WPM (not slow theatrical 160)
            subprocess.run(['say', '-v', selected_voice, '-r', '200', clean_text], timeout=60)
            return True

        except Exception as e:
            print(f"🍺 System TTS failed: {e}")
            return False

def main():
    """Launch the Witty Bartender M1K3 experience"""
    print("🍺 Witty Bartender M1K3 - Your Curious Local AI")
    print("=" * 50)
    print("Friendly • Curious • Trivia-loving • Eco-conscious")
    print()

    bartender = WittyBartenderM1K3()

    # Initialize voice system with NORMAL settings
    try:
        from src.tts.controllers.kittentts_manager import KittenTTSManager
        kitten_manager = KittenTTSManager()
        if kitten_manager.load_model():
            print("🎤 Natural voice synthesis ready")
        else:
            print("🎤 System voice available")
    except:
        print("🎤 System voice available")

    # Friendly greeting
    print("🍺 M1K3 (Your Friendly Bartender AI):")
    greeting = bartender.get_friendly_greeting()
    print(greeting)

    # Speak with normal speed
    bartender.speak_naturally(greeting)

    print(f"\n🍺 FRIENDLY CONVERSATION MODE")
    print("Try asking about: consciousness, meditation, AI, privacy, or anything else!")
    print("Type 'cheers' to end the conversation")
    print("-" * 50)

    conversation_count = 0

    while True:
        try:
            question = input(f"\n🍺 You: ").strip()

            if not question:
                continue

            if question.lower() in ['cheers', 'thanks', 'bye', 'quit', 'exit']:
                farewell = """Cheers! It's been great chatting with you.

Remember, I'm always here when you need a curious friend who loves trivia and keeps everything private. Local processing means our conversations stay between us, and no energy-hungry cloud servers get involved.

Take care, and feel free to come back anytime for more questions, trivia, or just a friendly chat!"""

                print(f"\n🍺 M1K3:")
                print(farewell)
                bartender.speak_naturally(farewell)
                break

            # Generate friendly response
            print(f"\n🍺 M1K3 (thinking...):")

            start_time = time.time()
            response = bartender.get_witty_response("", question)
            response_time = time.time() - start_time

            if response:
                print(response)
                bartender.speak_naturally(response)

                conversation_count += 1
                if conversation_count == 3:
                    print(f"\n✨ This is fun! I love curious conversations like this.")

            print(f"\n📊 Response time: {response_time:.2f}s")

        except KeyboardInterrupt:
            print(f"\n\n🍺 No worries! Come back anytime for more trivia and friendly chat!")
            break
        except Exception as e:
            print(f"\n🍺 Oops, encountered a little hiccup: {e}")
            print("But hey, that's what makes conversations interesting!")

    print(f"\n🍺 Thanks for the great conversation!")
    print("Your friendly, curious, local AI bartender - M1K3")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n🍺 Even bartenders have off days: {e}")
        print("But the conversation was still worth it!")