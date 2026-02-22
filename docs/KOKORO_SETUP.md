# Kokoro-82M TTS Integration Guide

## Overview

Kokoro-82M is a state-of-the-art, ultra-lightweight TTS model with only 82 million parameters. Despite its small size, it achieved **#1 ranking on the TTS Arena**, outperforming much larger models like:
- XTTS v2 (467M params)
- MetaVoice (1.2B params)
- Fish Speech (~500M params)

**Key Features:**
- 🏆 #1 on TTS Arena benchmark
- 🪶 Only 82M parameters (~350MB model size)
- ⚡ Real-time synthesis capability
- 📦 ONNX format for cross-platform deployment
- 🔓 Apache 2.0 licensed (fully open)
- 🎯 Trained on <100 hours of audio

**Architecture:** StyleTTS 2 + ISTFTNet (no diffusion, decoder-only)

## Installation

### 1. Install Python Package

```bash
pip install kokoro-onnx
```

For GPU acceleration (optional):
```bash
pip install kokoro-onnx[gpu]
```

### 2. Download Model Files

Download the following files from [HuggingFace](https://huggingface.co/hexgrad/Kokoro-82M):

1. **kokoro-v1.0.onnx** (~350MB) - The TTS model
2. **voices-v1.0.bin** - Voice embeddings file

Place them in the `models/kokoro/` directory:

```bash
mkdir -p models/kokoro
cd models/kokoro

# Download model (choose one method)

# Method 1: Direct download with wget
wget https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/kokoro-v1.0.onnx
wget https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/voices-v1.0.bin

# Method 2: Using huggingface-cli
huggingface-cli download hexgrad/Kokoro-82M kokoro-v1.0.onnx voices-v1.0.bin --local-dir .

# Method 3: Git LFS
git lfs install
git clone https://huggingface.co/hexgrad/Kokoro-82M
cp Kokoro-82M/kokoro-v1.0.onnx .
cp Kokoro-82M/voices-v1.0.bin .
```

### 3. Verify Installation

```bash
python src/tts/controllers/kokoro_tts_manager.py
```

You should see:
```
🧪 Testing Kokoro TTS Manager
✅ Kokoro TTS package available
✅ Model loaded successfully
✅ Successfully generated audio
```

## Usage

### CLI with Kokoro Profile

```bash
# Use default Kokoro profile (American Female)
python m1k3.py --profile kokoro

# Use American Male voice
python m1k3.py --profile kokoro_male

# Use British Female voice
python m1k3.py --profile kokoro_british

# Use fastest Kokoro profile
python m1k3.py --profile kokoro_fast
```

### Programmatic Usage

```python
from src.tts.controllers.kokoro_tts_manager import kokoro_manager

# Load model
if kokoro_manager.load_model():
    # Generate audio with default voice (American Female)
    audio = kokoro_manager.generate("Hello! This is Kokoro TTS.")

    # Generate with specific voice
    audio = kokoro_manager.generate(
        "Testing British accent",
        voice="bf",  # British Female
        speed=1.0
    )

    # Available voices: 'af', 'am', 'bf', 'bm'
    voices = kokoro_manager.get_available_voices()
    print(f"Available voices: {voices}")
```

## Available Voices

| Voice ID | Description | Language | Gender | Best For |
|----------|-------------|----------|---------|----------|
| `af` | American Female | en-US | Female | Assistant responses, narration |
| `am` | American Male | en-US | Male | Technical content, presentations |
| `bf` | British Female | en-GB | Female | Formal content, audiobooks |
| `bm` | British Male | en-GB | Male | Documentation, educational content |

## Performance

**Real-Time Factor (RTF):** ~0.1-0.3x (generates audio 3-10x faster than playback)

Example metrics:
- 2.5s audio generated in ~0.25s (RTF: 0.1x)
- 5.0s audio generated in ~0.8s (RTF: 0.16x)

**Resource Usage:**
- RAM: ~500MB (model + runtime)
- CPU: Single-threaded inference
- GPU: Optional (requires `onnxruntime-gpu`)

## ONNX Export for Mobile

The Kokoro model is **already in ONNX format**, making it ready for mobile deployment!

### For Android (KMP)

1. **Copy ONNX files to Android assets:**
   ```bash
   mkdir -p app/composeApp/src/androidMain/assets/models
   cp models/kokoro/kokoro-v1.0.onnx app/composeApp/src/androidMain/assets/models/
   cp models/kokoro/voices-v1.0.bin app/composeApp/src/androidMain/assets/models/
   ```

2. **Add ONNX Runtime dependency** (in `app/composeApp/build.gradle.kts`):
   ```kotlin
   androidMain.dependencies {
       implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.1")
   }
   ```

3. **Load and run inference:**
   ```kotlin
   val ortEnv = OrtEnvironment.getEnvironment()
   val sessionOptions = OrtSession.SessionOptions()

   // Load model from assets
   val modelPath = context.assets.open("models/kokoro-v1.0.onnx")
   val modelBytes = modelPath.readBytes()
   val session = ortEnv.createSession(modelBytes, sessionOptions)

   // Run inference (simplified - see full implementation in mobile docs)
   val output = session.run(inputs)
   ```

### For iOS (KMP)

iOS uses **Core ML** for ONNX model execution:

1. **Convert ONNX to Core ML:**
   ```python
   import coremltools as ct
   from coremltools.converters.onnx import convert

   # Convert Kokoro ONNX to Core ML
   model = convert(
       model='models/kokoro/kokoro-v1.0.onnx',
       minimum_ios_deployment_target='15.0'
   )
   model.save('Kokoro.mlpackage')
   ```

2. **Add to Xcode project** and use with Swift:
   ```swift
   let model = try Kokoro(configuration: MLModelConfiguration())
   let output = try model.prediction(input: input)
   ```

## Troubleshooting

### Model files not found

Ensure files are in the correct location:
```bash
ls -lh models/kokoro/
# Should show:
# kokoro-v1.0.onnx (~350MB)
# voices-v1.0.bin
```

### kokoro-onnx not installed

```bash
pip install kokoro-onnx
```

### GPU acceleration not working

Install GPU-enabled ONNX Runtime:
```bash
pip install onnxruntime-gpu
```

Then enable in code:
```python
kokoro_manager.enable_gpu()
```

## References

- **Model:** [hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M)
- **ONNX Export:** [onnx-community/Kokoro-82M-ONNX](https://huggingface.co/onnx-community/Kokoro-82M-ONNX)
- **Python Package:** [kokoro-onnx on PyPI](https://pypi.org/project/kokoro-onnx/)
- **GitHub:** [thewh1teagle/kokoro-onnx](https://github.com/thewh1teagle/kokoro-onnx)
- **TTS Arena:** [Kokoro-82M #1 Ranking](https://medium.com/data-science-in-your-pocket/kokoro-82m-the-best-tts-model-in-just-82-million-parameters-512b4ba4f94c)

## License

Kokoro-82M is licensed under **Apache 2.0**, allowing commercial use, modification, and distribution.
