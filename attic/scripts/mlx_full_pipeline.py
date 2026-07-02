#!/opt/homebrew/bin/python3.12
"""
M1K3 Full E2E Pipeline
MLX Intelligence → Kokoro Voice → Intercom Effects → Speaker
"""

import sys
import os
import time
import re
import numpy as np
import requests
import kokoro_onnx
import sounddevice as sd

sys.path.insert(0, '.')

from src.tts.effects.audio_effects import (
    IntercomEffect,
    CompressionEffect,
    NormalizationEffect,
    Film80sEffect,
)

MLX_URL = "http://localhost:8080/v1/chat/completions"

# ═══ Load Kokoro ═══
print("🎙️  Loading Kokoro...")
tts = kokoro_onnx.Kokoro('models/kokoro/kokoro-v1.0.onnx', 'models/kokoro/voices-v1.0.bin')
print("✅  Ready.\n")

# ═══ M1K3 Signature Sound: Intercom + Warmth ═══
EFFECTS = [
    IntercomEffect({"low_freq": 250, "high_freq": 3800}),
    Film80sEffect({"preset": "vhs_hifi"}),
    CompressionEffect({"threshold": 0.5, "ratio": 0.3}),
    NormalizationEffect({"level": 0.9}),
]


def split_sentences(text):
    """Split text into speakable sentences."""
    import re
    # Split on sentence-ending punctuation, keeping the punctuation
    parts = re.split(r'(?<=[.!?])\s+', text.strip())
    return [p.strip() for p in parts if p.strip()]


def speak(text, voice='bm_daniel'):
    """Kokoro → Effects → Speaker (sentence-by-sentence for full playback)"""
    try:
        sentences = split_sentences(text)
        if not sentences:
            sentences = [text]

        for sentence in sentences:
            samples, sr = tts.create(sentence, voice=voice)
            audio = np.array(samples, dtype=np.float32)

            # Pad tail with 0.3s silence — gives effects room to decay
            pad = np.zeros(int(sr * 0.3), dtype=np.float32)
            audio = np.concatenate([audio, pad])

            for fx in EFFECTS:
                audio = fx.apply(audio, sr)

            sd.play(audio, sr)
            sd.wait()
            time.sleep(0.05)  # Small buffer between sentences

    except Exception as e:
        print(f"  ❌ {e}")


def ask(question):
    """Query MLX and speak the answer"""
    print(f"\n  ❓ {question}")

    try:
        r = requests.post(MLX_URL, json={
            "messages": [
                {"role": "system", "content": (
                    "You are M1K3, a local AI on Apple Silicon. "
                    "Warm, Irish, thoughtful. Under 50 words. "
                    "Answer directly. Do NOT use <think> tags."
                )},
                {"role": "user", "content": question}
            ],
            "max_tokens": 500,
            "temperature": 0.7
        }, timeout=30.0)

        answer = r.json()['choices'][0]['message']['content'].strip()

        # Strip thinking tags — extract visible answer
        if '<think>' in answer:
            if '</think>' in answer:
                after_think = answer.split('</think>')[-1].strip()
                if after_think:
                    answer = after_think
                else:
                    # Everything inside think block — grab last paragraph
                    think_content = answer.split('</think>')[0].split('<think>')[-1]
                    paragraphs = [p.strip() for p in think_content.split('\n\n') if p.strip()]
                    answer = paragraphs[-1] if paragraphs else ""
            else:
                before = answer.split('<think>')[0].strip()
                if before:
                    answer = before
                else:
                    inner = answer.split('<think>')[-1]
                    paragraphs = [p.strip() for p in inner.split('\n\n') if p.strip()]
                    answer = paragraphs[-1] if paragraphs else ""

        answer = re.sub(r'</?think>', '', answer)
        answer = re.sub(r'\\[\[\]]', '', answer)
        answer = answer.strip()

        if not answer:
            answer = "Hmm, let me think about that differently."

        print(f"  🤖 {answer}\n")
        speak(answer)
        return answer

    except Exception as e:
        print(f"  ❌ {e}")
        return None


def main():
    print("═" * 50)
    print("  M1K3 — Local AI with Kokoro Voice")
    print("  Intercom + VHS Hi-Fi + Compression")
    print("═" * 50)

    # Emergent capability tests
    tests = [
        "Introduce yourself.",
        "A farmer has 23 chickens, buys 17, then sells half. How many left?",
        "Write a haiku about thinking inside a machine.",
        "What does privacy mean to an AI that never leaves home?",
        "What are you thinking right now?",
    ]

    for q in tests:
        ask(q)
        input("  [Enter] ")

    # Interactive
    print("\n  💬 Ask anything. Type 'quit' to exit.\n")
    while True:
        try:
            q = input("  🎤 You: ").strip()
            if q.lower() in ['quit', 'exit', 'q']:
                speak("Goodbye.")
                break
            if q:
                ask(q)
        except (KeyboardInterrupt, EOFError):
            break

    print("\n  ✅ Done.\n")


if __name__ == "__main__":
    main()
