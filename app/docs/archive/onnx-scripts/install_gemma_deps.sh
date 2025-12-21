#!/bin/bash

# Install Gemma 3:270m export dependencies
# Fix for zsh bracket issue on macOS

echo "🔧 Installing Gemma 3:270m export dependencies..."
echo ""

# Option 1: Quote the package name (recommended)
pip install --upgrade --upgrade-strategy eager 'optimum[onnxruntime]'

# Also install other dependencies
pip install transformers>=4.40.0
pip install onnx>=1.16.0
pip install onnxruntime>=1.17.0
pip install torch>=2.0.0

echo ""
echo "✅ Dependencies installed!"
echo ""
echo "Next step: Run the export script"
echo "  python export_gemma3_270m.py"
