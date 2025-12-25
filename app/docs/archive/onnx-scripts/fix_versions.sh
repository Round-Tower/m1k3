#!/bin/bash

echo "🔧 Fixing version conflicts for Gemma export..."
echo ""

# Downgrade to compatible versions
echo "📦 Installing compatible versions..."

# Step 1: Downgrade numpy to 1.x
pip install 'numpy<2.0' --force-reinstall

# Step 2: Install compatible transformers
pip install 'transformers<4.56.0,>=4.36.0' --force-reinstall

# Step 3: Reinstall optimum with correct versions
pip install 'optimum[onnxruntime]==1.27.0' --force-reinstall

# Step 4: Upgrade torch to 2.4+
pip install 'torch>=2.4.0' --upgrade

echo ""
echo "✅ Fixed version conflicts!"
echo ""
echo "Verify installation:"
python -c "import numpy; print(f'numpy: {numpy.__version__}')"
python -c "import transformers; print(f'transformers: {transformers.__version__}')"
python -c "import torch; print(f'torch: {torch.__version__}')"
python -c "from optimum.onnxruntime import ORTModelForCausalLM; print('✅ optimum OK')"

echo ""
echo "Now run the export:"
echo "  python export_gemma3_270m.py --validate"
