#!/bin/bash

# Clean Virtual Environment for Gemma 3:270m Export
# Isolates dependencies from global PyEnv packages

echo "🔧 Setting up isolated virtual environment for Gemma export..."
echo ""

# Step 1: Create fresh venv
echo "📦 Creating virtual environment..."
python -m venv gemma_export_env

# Step 2: Activate venv
echo "✅ Activating environment..."
source gemma_export_env/bin/activate

# Step 3: Upgrade pip
echo "⬆️  Upgrading pip..."
pip install --upgrade pip

# Step 4: Install compatible dependencies in correct order
echo ""
echo "📦 Installing dependencies..."
echo "   This avoids conflicts with your global gradio/streamlit/pillow packages"
echo ""

# Install numpy first (1.x for torch compatibility)
pip install 'numpy<2.0,>=1.23.0'

# Install torch with M1 Mac support
pip install 'torch>=2.4.0'

# Install transformers (compatible version)
pip install 'transformers>=4.36.0,<4.56.0'

# Install optimum with ONNX Runtime
pip install 'optimum[onnxruntime]==1.27.0'

# Install additional dependencies
pip install onnx onnxruntime accelerate

echo ""
echo "✅ Virtual environment setup complete!"
echo ""
echo "📊 Installed versions:"
python -c "import numpy; print(f'  numpy: {numpy.__version__}')"
python -c "import torch; print(f'  torch: {torch.__version__}')"
python -c "import transformers; print(f'  transformers: {transformers.__version__}')"
python -c "from optimum.onnxruntime import ORTModelForCausalLM; print('  optimum: OK')"

echo ""
echo "🚀 Now run the export:"
echo "   source gemma_export_env/bin/activate"
echo "   python export_gemma3_270m.py --validate"
echo ""
echo "💡 To deactivate when done:"
echo "   deactivate"
