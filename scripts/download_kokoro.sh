#!/bin/bash
# Download Kokoro-82M TTS model files

set -e

MODELS_DIR="models/kokoro"
MODEL_URL="https://huggingface.co/hexgrad/Kokoro-82M/resolve/main"

echo "🎤 Kokoro-82M TTS Model Downloader"
echo "===================================="
echo ""

# Create models directory
echo "📁 Creating directory: $MODELS_DIR"
mkdir -p "$MODELS_DIR"

# Download kokoro-v1.0.onnx
echo "⬇️  Downloading kokoro-v1.0.onnx (~350MB)..."
if [ -f "$MODELS_DIR/kokoro-v1.0.onnx" ]; then
    echo "   ✅ kokoro-v1.0.onnx already exists, skipping"
else
    curl -L "$MODEL_URL/kokoro-v1.0.onnx" -o "$MODELS_DIR/kokoro-v1.0.onnx"
    echo "   ✅ kokoro-v1.0.onnx downloaded"
fi

# Download voices-v1.0.bin
echo "⬇️  Downloading voices-v1.0.bin..."
if [ -f "$MODELS_DIR/voices-v1.0.bin" ]; then
    echo "   ✅ voices-v1.0.bin already exists, skipping"
else
    curl -L "$MODEL_URL/voices-v1.0.bin" -o "$MODELS_DIR/voices-v1.0.bin"
    echo "   ✅ voices-v1.0.bin downloaded"
fi

echo ""
echo "✅ Kokoro-82M model files downloaded successfully!"
echo ""
echo "📊 Model Info:"
ls -lh "$MODELS_DIR/"
echo ""
echo "🔍 Next steps:"
echo "   1. pip install kokoro-onnx"
echo "   2. python src/tts/controllers/kokoro_tts_manager.py  # Test"
echo "   3. python m1k3.py --profile kokoro  # Use in M1K3"
echo ""
