#!/opt/homebrew/bin/python3.12
"""
M1K3 Model Interview — Auditions for On-Device KMP Deployment
Same questions, every model, spoken through the full pipeline.
Interactive model selection — pick individuals, groups, or run all.

MurphySig: kev+claude | confidence: 0.9 | context: model evaluation tooling
"""

import sys
import os
import time
import re
import subprocess
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

# ═══ Model Registry ═══
# tag: "app" = currently deployed in KMP app (GGUF via llama.cpp)
# tag: "candidate" = potential future app model
# tag: "thinking" = uses <think> tags, needs extra tokens
MODELS = [
    # ── Currently in the KMP App ──
    {
        "id": "mlx-community/Falcon-H1-Tiny-R-90M-bf16",
        "name": "Falcon-H1 90M",
        "params": "90M",
        "tags": ["app", "mamba2"],
        "app_note": "In app as Falcon-H1-Tiny-90M-Instruct-Q8_0.gguf",
    },
    {
        "id": "mlx-community/gemma-3-1b-it-qat-4bit",
        "name": "Gemma 3 1B",
        "params": "1B",
        "tags": ["candidate"],
        "app_note": "App has 270M variant (gemma-3-270m-it-UD-IQ3_XXS.gguf)",
    },
    # ── Tiny (< 500M) ──
    {
        "id": "mlx-community/SmolLM2-135M-Instruct",
        "name": "SmolLM2 135M",
        "params": "135M",
        "tags": ["candidate"],
        "app_note": "In app as smollm2-135m-q4.gguf",
    },
    {
        "id": "mlx-community/SmolLM2-360M-Instruct",
        "name": "SmolLM2 360M",
        "params": "360M",
        "tags": ["candidate"],
        "app_note": "App primary LLM (SmolLM2-360M ONNX q4f16)",
    },
    {
        "id": "mlx-community/Qwen2.5-0.5B-Instruct-4bit",
        "name": "Qwen2.5 0.5B",
        "params": "0.5B",
        "tags": ["candidate"],
        "app_note": None,
    },
    # ── Small (0.5B – 2B) ──
    {
        "id": "mlx-community/Qwen3-0.6B-4bit",
        "name": "Qwen3 0.6B",
        "params": "0.6B",
        "tags": ["candidate", "thinking"],
        "app_note": None,
    },
    {
        "id": "mlx-community/TinyLlama-1.1B-Chat-v1.0-4bit",
        "name": "TinyLlama 1.1B",
        "params": "1.1B",
        "tags": ["candidate"],
        "app_note": None,
    },
    {
        "id": "mlx-community/Llama-3.2-1B-Instruct-4bit",
        "name": "Llama 3.2 1B",
        "params": "1B",
        "tags": ["candidate"],
        "app_note": None,
    },
    {
        "id": "mlx-community/Qwen3-1.7B-4bit",
        "name": "Qwen3 1.7B",
        "params": "1.7B",
        "tags": ["candidate", "thinking"],
        "app_note": None,
    },
    # ── Medium (3B+) ──
    {
        "id": "mlx-community/SmolLM3-3B-4bit",
        "name": "SmolLM3 3B",
        "params": "3B",
        "tags": ["candidate"],
        "app_note": None,
    },
    {
        "id": "mlx-community/Qwen3-4B-4bit",
        "name": "Qwen3 4B",
        "params": "4B",
        "tags": ["candidate", "thinking"],
        "app_note": None,
    },
    {
        "id": "mlx-community/Falcon-H1R-7B-4bit",
        "name": "Falcon-H1R 7B",
        "params": "7B",
        "tags": ["mamba2", "thinking"],
        "app_note": "Big brother of app's Falcon-H1 90M",
    },
    # ── Large (reference / ceiling) ──
    {
        "id": "mlx-community/Qwen3-Coder-Next-4bit",
        "name": "Qwen3-Coder-Next 80B",
        "params": "80B MoE",
        "tags": ["thinking"],
        "app_note": "Reference ceiling — too large for device, shows what's possible",
    },
]

# ═══ Interview Questions ═══
INTERVIEW = [
    {
        "name": "Daily Summary",
        "question": (
            "Summarise my day from this data:\n"
            "Notifications: 3 Slack messages (work), 2 WhatsApp (Mam, Dave), 1 missed call (unknown)\n"
            "Locations: Home until 9am, Office 9:30-5pm, Tesco 5:20pm, Home 6pm\n"
            "Emails: 12 received (4 marketing, 3 JIRA, 2 from boss, 1 calendar invite, 2 newsletters)\n"
            "Screen time: 6h 22m (1h 45m Slack, 1h 20m VS Code, 58m Safari, 45m YouTube, 34m Twitter)\n"
            "Steps: 4,812 | Weather: 11°C, light rain"
        ),
        "tests": "structured data comprehension, summarisation, tone",
    },
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
    "Warm, Irish, fun. Keep answers concise but complete. "
    "Answer directly."
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
    """Kokoro -> Effects -> Speaker (sentence-by-sentence)"""
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
        subprocess.run(["pkill", "-f", "mlx_lm.server"], capture_output=True, timeout=5)
        time.sleep(2)
    except Exception:
        pass


def start_server(model_id):
    """Start mlx_lm.server with the given model"""
    stop_server()
    proc = subprocess.Popen(
        [f"{MLX_ENV}/mlx_lm.server", "--model", model_id, "--port", str(MLX_PORT)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
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
            "max_tokens": 1024,
            "temperature": 0.7
        }, timeout=180.0)
        latency = time.time() - start

        answer = r.json()['choices'][0]['message']['content'].strip()

        # Strip think tags — extract visible answer
        if '<think>' in answer:
            if '</think>' in answer:
                # Take content after the last </think>
                after_think = answer.split('</think>')[-1].strip()
                if after_think:
                    answer = after_think
                else:
                    # Model put everything inside think block — grab last paragraph
                    think_content = answer.split('</think>')[0].split('<think>')[-1]
                    paragraphs = [p.strip() for p in think_content.split('\n\n') if p.strip()]
                    answer = paragraphs[-1] if paragraphs else ""
            else:
                # Unclosed think — model ran out of tokens mid-thought
                # Grab whatever came before <think> or last paragraph inside
                before = answer.split('<think>')[0].strip()
                if before:
                    answer = before
                else:
                    inner = answer.split('<think>')[-1]
                    paragraphs = [p.strip() for p in inner.split('\n\n') if p.strip()]
                    answer = paragraphs[-1] if paragraphs else ""

        # Clean any stray tags and LaTeX
        answer = re.sub(r'</?think>', '', answer)
        answer = re.sub(r'\\[\[\]]', '', answer)  # strip \[ \] LaTeX delimiters
        answer = answer.strip()

        if not answer:
            answer = "(model produced no visible answer)"

        return answer, latency
    except Exception as e:
        return f"Error: {e}", 0.0


def is_downloaded(model_id):
    """Check if model is in HuggingFace cache"""
    cache_name = model_id.replace("/", "--")
    cache_path = os.path.expanduser(f"~/.cache/huggingface/hub/models--{cache_name}")
    if not os.path.isdir(cache_path):
        return False
    # Check for actual weight files (not just config)
    for root, dirs, files in os.walk(cache_path):
        for f in files:
            if f.endswith('.safetensors'):
                return True
    # Check blobs for symlinked snapshots
    snapshots = os.path.join(cache_path, "snapshots")
    if os.path.isdir(snapshots):
        for snap in os.listdir(snapshots):
            snap_dir = os.path.join(snapshots, snap)
            if os.path.isdir(snap_dir):
                for f in os.listdir(snap_dir):
                    if 'safetensors' in f or 'model' in f.lower():
                        target = os.path.join(snap_dir, f)
                        if os.path.islink(target) or os.path.isfile(target):
                            return True
    return False


def select_models():
    """Interactive model selector"""
    print("═" * 60)
    print("  🏆 M1K3 MODEL INTERVIEWS")
    print("  Auditions for On-Device KMP Deployment")
    print("═" * 60)

    # Build available list
    available = []
    for m in MODELS:
        dl = is_downloaded(m["id"])
        available.append({**m, "downloaded": dl})

    # Display roster
    print(f"\n  {'#':<4} {'Model':<25} {'Params':>6}  {'Status':<12} {'Notes'}")
    print(f"  {'─' * 75}")
    for i, m in enumerate(available, 1):
        status = "✅ ready" if m["downloaded"] else "❌ not downloaded"
        tags = ""
        if "app" in m["tags"]:
            tags = "📱 IN APP"
        elif m.get("app_note"):
            tags = f"📱 {m['app_note']}"
        if "thinking" in m["tags"]:
            tags = ("🧠 " if not tags else tags + " | 🧠") + "thinker"
        if "mamba2" in m["tags"]:
            tags = ("⚡ " if not tags else tags + " | ⚡") + "SSM"
        print(f"  {i:<4} {m['name']:<25} {m['params']:>6}  {status:<12} {tags}")

    # Selection
    print(f"\n  Selection options:")
    print(f"    all       — Interview all downloaded models")
    print(f"    app       — Only models currently in the KMP app")
    print(f"    tiny      — Models under 500M (mobile-friendly)")
    print(f"    small     — Models 500M–2B")
    print(f"    medium    — Models 3B+")
    print(f"    1,3,5     — Pick by number (comma-separated)")
    print(f"    1-6       — Pick a range")
    print(f"    q         — Quit\n")

    choice = input("  🎯 Select: ").strip().lower()

    if choice == 'q':
        return []
    elif choice == 'all':
        selected = [m for m in available if m["downloaded"]]
    elif choice == 'app':
        selected = [m for m in available if "app" in m["tags"] and m["downloaded"]]
    elif choice == 'tiny':
        tiny_params = {"90M", "135M", "270M", "360M", "0.5B"}
        selected = [m for m in available if m["params"] in tiny_params and m["downloaded"]]
    elif choice == 'small':
        small_params = {"0.6B", "1B", "1.1B", "1.7B"}
        selected = [m for m in available if m["params"] in small_params and m["downloaded"]]
    elif choice == 'medium':
        medium_params = {"3B", "4B", "7B"}
        selected = [m for m in available if m["params"] in medium_params and m["downloaded"]]
    elif '-' in choice:
        # Range: "1-6"
        try:
            start, end = choice.split('-')
            indices = list(range(int(start) - 1, int(end)))
            selected = [available[i] for i in indices if 0 <= i < len(available) and available[i]["downloaded"]]
        except (ValueError, IndexError):
            print("  ❌ Invalid range")
            return []
    else:
        # Comma-separated: "1,3,5"
        try:
            indices = [int(x.strip()) - 1 for x in choice.split(',')]
            selected = [available[i] for i in indices if 0 <= i < len(available)]
            not_downloaded = [s for s in selected if not s["downloaded"]]
            if not_downloaded:
                for m in not_downloaded:
                    print(f"  ⚠️  {m['name']} not downloaded, skipping")
            selected = [s for s in selected if s["downloaded"]]
        except (ValueError, IndexError):
            print("  ❌ Invalid selection")
            return []

    return selected


def interview_model(model):
    """Run the full interview for one model"""
    name = model["name"]
    tags_str = " ".join(
        {"app": "📱", "mamba2": "⚡", "thinking": "🧠", "candidate": ""}.get(t, "")
        for t in model["tags"]
    ).strip()

    print(f"\n{'═' * 60}")
    print(f"  🎤 INTERVIEWING: {name} ({model['params']}) {tags_str}")
    if model.get("app_note"):
        print(f"  📱 {model['app_note']}")
    print(f"{'═' * 60}")

    print(f"  ⏳ Loading model...")
    proc = start_server(model["id"])
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

    avg_latency = sum(r['latency'] for r in results) / len(results)
    avg_words = sum(r['words'] for r in results) / len(results)
    print(f"\n  📊 {name} Summary:")
    print(f"     Avg latency: {avg_latency:.1f}s")
    print(f"     Avg words:   {avg_words:.0f}")

    proc.terminate()
    return {
        "model": name,
        "params": model["params"],
        "tags": model["tags"],
        "results": results,
        "avg_latency": avg_latency,
        "avg_words": avg_words,
    }


def main():
    selected = select_models()

    if not selected:
        print("\n  No models selected. Goodbye!\n")
        return

    print(f"\n  🎬 Interviewing {len(selected)} models, {len(INTERVIEW)} questions each")
    print(f"  🔊 Full audio: Kokoro → Intercom → VHS Hi-Fi → Speaker\n")

    all_results = []
    for idx, model in enumerate(selected, 1):
        print(f"\n  📍 Candidate {idx}/{len(selected)}")
        result = interview_model(model)
        if result:
            all_results.append(result)

        if idx < len(selected):
            input(f"\n  [Enter for next candidate] ")

    # ═══ Final Scoreboard ═══
    if all_results:
        print(f"\n\n{'═' * 70}")
        print("  🏆 FINAL SCOREBOARD")
        print(f"{'═' * 70}")
        print(f"  {'Model':<28s} {'Params':>6s} {'Tags':>8s} {'Avg Latency':>12s} {'Avg Words':>10s}")
        print(f"  {'─' * 66}")
        for r in sorted(all_results, key=lambda x: x['avg_latency']):
            tags = ""
            if "app" in r["tags"]:
                tags = "📱"
            if "mamba2" in r["tags"]:
                tags += "⚡"
            print(f"  {r['model']:<28s} {r['params']:>6s} {tags:>8s} {r['avg_latency']:>10.1f}s {r['avg_words']:>10.0f}")
        print(f"{'═' * 70}")
        print(f"\n  🎯 Pick your champion for the KMP app!\n")

    stop_server()


if __name__ == "__main__":
    main()
