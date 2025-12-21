#!/bin/bash

# Gemma 3:270m Export Guide for M1 Mac with Conda
# PHASE1.5-001

echo "🚀 Gemma 3:270m ONNX Export for M1 Mac"
echo "======================================"
echo ""

# Check if conda is activated
if [[ -z "$CONDA_DEFAULT_ENV" ]]; then
    echo "⚠️  No conda environment active!"
    echo ""
    echo "Please activate your conda environment first:"
    echo "  conda activate gemma"
    echo ""
    echo "If you don't have the environment yet:"
    echo "  conda create -n gemma python=3.10"
    echo "  conda activate gemma"
    echo "  pip install 'optimum[onnxruntime]' transformers torch"
    exit 1
fi

echo "✅ Conda environment active: $CONDA_DEFAULT_ENV"
echo ""

# Check dependencies
echo "🔍 Checking dependencies..."
python -c "import optimum, transformers, torch, onnx; print('✅ All dependencies installed')" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "❌ Missing dependencies!"
    echo ""
    echo "Install with:"
    echo "  pip install 'optimum[onnxruntime]' transformers torch onnx onnxruntime"
    exit 1
fi

echo ""
echo "📥 Starting Gemma 3:270m export..."
echo "   This will:"
echo "   1. Download Gemma 2 model (~2.5GB) from HuggingFace"
echo "   2. Export to ONNX format"
echo "   3. Apply INT4 quantization"
echo "   4. Validate the exported model"
echo "   5. Save to composeApp/src/androidMain/assets/models/"
echo ""
echo "⏱️  Expected time: 5-10 minutes on M1 Mac"
echo ""

# Run the export
python export_gemma3_270m.py --validate

echo ""
echo "✅ Export complete! Check the output above for any errors."
echo ""
echo "Next steps:"
echo "  1. Copy the ONNX model to assets/models/"
echo "  2. Update Gemma3Engine.kt with autoregressive generation"
echo "  3. Run comparison tests against SmolLM2-360M"
