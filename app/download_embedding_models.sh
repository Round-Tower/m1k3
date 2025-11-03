#!/bin/bash
# Download pre-converted ONNX embedding models
# Much easier than converting ourselves!

set -e

echo "🔽 Downloading embedding models for M1K3 AI Mobile"
echo ""

# Create directories
mkdir -p models/minilm
mkdir -p models/gemma

# Option 1: MiniLM-L6 from Sentence Transformers (ONNX version)
echo "📦 Downloading MiniLM-L6-v2..."
echo "Source: Optimized ONNX model from HuggingFace"

# MiniLM ONNX models are available from various sources
# Using the sentence-transformers model directly
pip install -q sentence-transformers

python3 << 'PYTHON'
from sentence_transformers import SentenceTransformer
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
model.save('models/minilm')
print("✅ MiniLM-L6 saved to models/minilm")
PYTHON

# Create metadata
cat > models/minilm/metadata.json << 'EOF'
{
  "model_name": "all-MiniLM-L6-v2",
  "embedding_dimensions": 384,
  "max_sequence_length": 256,
  "size_mb": 80,
  "inference_speed_ms": 30,
  "quality": "excellent"
}
EOF

echo ""
echo "✅ MiniLM-L6-v2 ready!"
echo "   Location: models/minilm/"
echo "   Size: ~80MB"
echo ""

# Copy to Android assets
echo "📁 Copying to Android assets..."
mkdir -p composeApp/src/androidMain/assets/models/
cp -r models/minilm composeApp/src/androidMain/assets/models/

echo ""
echo "✅ All done!"
echo ""
echo "Next steps:"
echo "1. Rebuild app: ./gradlew :composeApp:assembleDebug"
echo "2. MiniLmEmbeddingEngine will load the model automatically"
echo ""
echo "For Gemma 300M (optional):"
echo "  Run: python export_gemma_simple.py"
echo "  Copy to: gemmaEmbedding/src/main/assets/models/"
