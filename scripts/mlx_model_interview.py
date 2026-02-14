#!/opt/homebrew/bin/python3.12
"""
M1K3 Model Interview — Auditions for On-Device KMP Deployment
Same questions, every model, spoken through the full pipeline.
Pick the best brain for the app.
"""

import sys
import os
import time
import re
import subprocess
import signal
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

MLX_PORT = 8080
MLX_URL = f"http://localhost:{MLX_PORT}/v1/chat/completions"
MLX_MODELS_URL = f"http://localhost:{MLX_PORT}/v1/models"
MLX_ENV = os.path.expanduser("~/Development/m1k3/mlx-env/bin")

# ═══ Candidates (smallest → largest) ═══
CANDIDATES = [
    # Round 1 veterans
    "mlx-community/SmolLM2-135M-Instruct",
    "mlx-community/Qwen3-0.6B-4bit",
    "mlx-community/gemma-3-1b-it-qat-4bit",
    "mlx-community/Llama-3.2-1B-Instruct-4bit",
    "mlx-community/Qwen3-1.7B-4bit",
    "mlx-community/Qwen3-4B-4bit",
    # Round 2 — new challengers
    "mlx-community/Falcon-H1-Tiny-R-90M-bf16",       # 90M Mamba2 hybrid — SSM!
    "mlx-community/SmolLM2-360M-Instruct",            # 360M — 135M's big sibling
    "mlx-community/Qwen2.5-0.5B-Instruct-4bit",       # 0.5B — proven quality
    "mlx-community/TinyLlama-1.1B-Chat-v1.0-4bit",    # 1.1B — classic tiny
    "mlx-community/SmolLM3-3B-4bit",                   # 3B — brand new SmolLM3!
    "mlx-community/Falcon-H1R-7B-4bit",               # 7B — Falcon Mamba2 hybrid
]

# ═══ Interview Questions (same for every model) ═══
INTERVIEW = [
    {
        "name": "Introduction",
        "question": "Introduce yourself in one sentence.",
        "tests": "coherence, personality",
    },
    {
        "name": "Arithmetic",
        "question": "A farmer has 23 chickens, buys 17, then sells half. How many left?",
        "tests": "math reasoning",
    },
    {
        "name": "Logic",
        "question": "If all cats are animals, and some animals are fast, can we say some cats are fast?",
        "tests": "logical reasoning",
    },
    {
        "name": "Creative",
        "question": "Write a haiku about an AI waking up on a phone for the first time.",
        "tests": "creativity, format",
    },
    {
        "name": "Helpfulness",
        "question": "How do I make a cup of tea?",
        "tests": "practical knowledge, conciseness",
    },
]

SYSTEM_PROMPT = (
    "You are M1K3, a local AI running on Apple Silicon. "
    "Warm, Irish, fun. Under 40 words. "
    "Answer directly. Do NOT use <think> tags."
)

# ═══ Load Kokoro ═══
print("\n🎙️  Loading Kokoro TTS...")
tts = kokoro_onnx.Kokoro('models/kokoro/kokoro-v1.0.onnx', 'models/kokoro/voices-v1.0.bin')
print("✅  Kokoro ready.\n")

# ═══ M1K3 Signature Sound ═══
EFFECTS = [
    IntercomEffect({"low_freq": 250, "high_freq": 3800}),
    Film80sEffect({"preset": "vhs_hifi"}),
    CompressionEffect({"threshold": 0.5, "ratio": 0.3}),
    NormalizationEffect({"level": 0.9}),
]


def split_sentences(text):
    parts = re.split(r'(?<=[.!?])\s+', text.strip())
    return [p.strip() for p in parts if p.strip()]


def speak(text, voice='bm_daniel'):
    """Kokoro → Effects → Speaker (sentence-by-sentence)"""
    try:
        sentences = split_sentences(text)
        if not sentences:
            sentences = [text]
        for sentence in sentences:
            samples, sr = tts.create(sentence, voice=voice)
            audio = np.array(samples, dtype=np.float32)
            pad = np.zeros(int(sr * 0.3), dtype=np.float32)
            audio = np.concatenate([audio, pad])
            for fx in EFFECTS:
                audio = fx.apply(audio, sr)
            sd.play(audio, sr)
            sd.wait()
            time.sleep(0.05)
    except Exception as e:
        print(f"  ❌ TTS: {e}")


def stop_server():
    """Kill any running mlx_lm.server"""
    try:
        subprocess.run(
            ["pkill", "-f", "mlx_lm.server"],
            capture_output=True, timeout=5
        )
        time.sleep(2)
    except Exception:
        pass


def start_server(model_name):
    """Start mlx_lm.server with the given model"""
    stop_server()
    cmd = [
        f"{MLX_ENV}/mlx_lm.server",
        "--model", model_name,
        "--port", str(MLX_PORT),
    ]
    proc = subprocess.Popen(
        cmd,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    # Wait for server to be ready
    for i in range(60):
        try:
            r = requests.get(MLX_MODELS_URL, timeout=2)
            if r.status_code == 200:
                return proc
        except Exception:
            pass
        time.sleep(1)
        if i % 10 == 9:
            print(f"    ⏳ Still loading ({i+1}s)...")

    print("  ❌ Server failed to start")
    return None


def ask(question):
    """Query MLX, return (answer, latency)"""
    try:
        start = time.time()
        r = requests.post(MLX_URL, json={
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": question}
            ],
            "max_tokens": 500,  # Thinking models need headroom for <think> + answer
            "temperature": 0.7
        }, timeout=120.0)
        latency = time.time() - start

        answer = r.json()['choices'][0]['message']['content'].strip()

        # Strip think tags — thinking models can burn 200+ tokens reasoning
        if '<think>' in answer:
            if '</think>' in answer:
                # Closed thinking block — take everything after it
                answer = answer.split('</think>')[-1].strip()
            else:
                # Unclosed — model hit token limit mid-thought, no usable answer
                answer = ""

        # Clean any stray tags
        answer = re.sub(r'</?think>', '', answer).strip()

        if not answer:
            answer = "(thinking model used all tokens reasoning — no visible answer)"

        return answer, latency

    except Exception as e:
        return f"Error: {e}", 0.0


def short_name(model):
    return model.split("/")[-1]


def interview_model(model_name):
    """Run the full interview for one model"""
    name = short_name(model_name)
    print(f"\n{'═' * 60}")
    print(f"  🎤 INTERVIEWING: {name}")
    print(f"{'═' * 60}")

    print(f"  ⏳ Loading model...")
    proc = start_server(model_name)
    if not proc:
        print(f"  ❌ Skipping {name} — failed to load")
        return None

    print(f"  ✅ {name} is live!\n")

    results = []
    for i, q in enumerate(INTERVIEW, 1):
        print(f"  ── {q['name']} ({q['tests']}) ──")
        print(f"  ❓ {q['question']}")

        answer, latency = ask(q['question'])
        word_count = len(answer.split())

        print(f"  🤖 {answer}")
        print(f"     ⏱️  {latency:.1f}s | {word_count} words\n")

        speak(answer)

        results.append({
            "question": q['name'],
            "answer": answer,
            "latency": latency,
            "words": word_count,
        })

        if i < len(INTERVIEW):
            input("  [Enter for next question] ")

    # Summary
    avg_latency = sum(r['latency'] for r in results) / len(results)
    avg_words = sum(r['words'] for r in results) / len(results)
    print(f"\n  📊 {name} Summary:")
    print(f"     Avg latency: {avg_latency:.1f}s")
    print(f"     Avg words:   {avg_words:.0f}")

    proc.terminate()
    return {"model": name, "results": results, "avg_latency": avg_latency, "avg_words": avg_words}


def main():
    print("═" * 60)
    print("  🏆 M1K3 MODEL INTERVIEWS")
    print("  Auditions for On-Device KMP Deployment")
    print("  Same questions · Full pipeline · You be the judge")
    print("═" * 60)
    print(f"\n  📋 {len(CANDIDATES)} candidates | {len(INTERVIEW)} questions each")
    print(f"  🔊 Full audio: Kokoro → Intercom → VHS Hi-Fi → Speaker\n")

    # Check which candidates are actually downloaded
    available = []
    for model in CANDIDATES:
        cache_name = model.replace("/", "--")
        cache_path = os.path.expanduser(f"~/.cache/huggingface/hub/models--{cache_name}")
        if os.path.isdir(cache_path):
            available.append(model)
        else:
            print(f"  ⚠️  {short_name(model)} not downloaded, skipping")

    print(f"\n  ✅ {len(available)} models ready to interview\n")

    all_results = []
    for idx, model in enumerate(available, 1):
        print(f"\n  📍 Candidate {idx}/{len(available)}")
        result = interview_model(model)
        if result:
            all_results.append(result)

        if idx < len(available):
            input(f"\n  [Enter for next candidate] ")

    # ═══ Final Scoreboard ═══
    print(f"\n\n{'═' * 60}")
    print("  🏆 FINAL SCOREBOARD")
    print(f"{'═' * 60}")
    print(f"  {'Model':<35s} {'Avg Latency':>12s} {'Avg Words':>10s}")
    print(f"  {'─' * 57}")
    for r in sorted(all_results, key=lambda x: x['avg_latency']):
        print(f"  {r['model']:<35s} {r['avg_latency']:>10.1f}s {r['avg_words']:>10.0f}")
    print(f"{'═' * 60}")
    print(f"\n  🎯 Pick your champion for the KMP app!\n")

    stop_server()


if __name__ == "__main__":
    main()
