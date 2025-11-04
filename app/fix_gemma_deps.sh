#!/bin/bash

echo "🔧 Installing missing Gemma export dependencies..."
echo ""

# Install optimum with ONNX Runtime support
echo "📦 Installing optimum[onnxruntime]..."
pip install --upgrade 'optimum[onnxruntime]'

echo ""
echo "📦 Installing additional dependencies..."
pip install --upgrade onnx onnxruntime

echo ""
echo "📦 Installing transformers and torch..."
pip install --upgrade transformers torch

echo ""
echo "✅ All dependencies installed!"
echo ""
echo "Verify installation:"
python -c "from optimum.onnxruntime import ORTModelForCausalLM; print('✅ optimum.onnxruntime OK')"
python -c "import onnx; print('✅ onnx OK')"
python -c "import onnxruntime; print('✅ onnxruntime OK')"
python -c "import transformers; print('✅ transformers OK')"
python -c "import torch; print('✅ torch OK')"

echo ""
echo "Now run the export:"
echo "  python export_gemma3_270m.py --validate"
