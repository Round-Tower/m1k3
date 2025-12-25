#!/bin/bash

# Setup Conda for Gemma Export on M1 Mac

echo "🔧 Setting up Conda for Gemma 3:270m export..."
echo ""

# Step 1: Initialize conda for zsh (default shell on macOS)
echo "Step 1: Initializing conda for zsh..."
conda init zsh

echo ""
echo "✅ Conda initialized!"
echo ""
echo "⚠️  IMPORTANT: You need to restart your terminal or run:"
echo "   source ~/.zshrc"
echo ""
echo "Then create and activate the environment:"
echo "   conda create -n gemma python=3.10"
echo "   conda activate gemma"
echo "   pip install 'optimum[onnxruntime]' transformers torch onnx onnxruntime"
echo ""
echo "Finally, run the export:"
echo "   python export_gemma3_270m.py --validate"
