# Quick Start: Add Embedding Models to M1K3 AI Mobile

**Current Status:** Implementation complete, models needed

**Goal:** Add MiniLM-L6 model (80MB) to enable semantic search

---

## 🚀 Fastest Method (Recommended)

### Step 1: Install Dependencies

```bash
cd /Users/kevinmurphy/Development/m1k3/app

# Install sentence-transformers (easiest option)
pip install sentence-transformers
```

### Step 2: Download Model

```bash
# Make script executable
chmod +x download_embedding_models.sh

# Run download script
./download_embedding_models.sh
```

This will:
- Download MiniLM-L6-v2 from HuggingFace
- Save to `models/minilm/`
- Copy to `composeApp/src/androidMain/assets/models/`
- Create metadata.json

### Step 3: Rebuild App

```bash
./gradlew :composeApp:assembleDebug
```

### Step 4: Test

The model will be loaded automatically by `MiniLmEmbeddingEngine`.

**Note:** Current implementation uses placeholder embeddings until we implement actual ONNX inference. The model files prepare the app for real inference.

---

## 🔧 Alternative: Manual Download

If the script doesn't work, download manually:

```python
# In Python console
from sentence_transformers import SentenceTransformer

model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
model.save('models/minilm')
```

Then copy to assets:
```bash
mkdir -p composeApp/src/androidMain/assets/models/
cp -r models/minilm composeApp/src/androidMain/assets/models/
```

---

## 📊 What You Get

**After adding the model:**

✅ **Semantic search ready** (using placeholder embeddings)
✅ **Model files in place** (ready for ONNX inference)
✅ **80MB added to APK** (total: 471MB)
✅ **Memory system operational**

**Next step:** Replace placeholder embeddings with actual ONNX inference (see `MiniLmEmbeddingEngine.android.kt`)

---

## 🔍 Verify Model Installation

```bash
# Check if model files exist
ls -lh composeApp/src/androidMain/assets/models/minilm/

# Should show:
# - pytorch_model.bin or model.safetensors
# - config.json
# - tokenizer files
# - vocab.txt
```

---

## ⚙️ For Gemma 300M (Optional)

**Only if you want the dynamic feature module:**

```bash
# Export Gemma (requires more memory/GPU recommended)
python export_gemma_simple.py

# Copy to dynamic module
mkdir -p gemmaEmbedding/src/main/assets/models/
cp -r models/gemma gemmaEmbedding/src/main/assets/models/
```

**Note:** Gemma is 180MB and takes longer to export. MiniLM is sufficient for 99% of users.

---

## 🐛 Troubleshooting

### "ModuleNotFoundError: No module named 'sentence_transformers'"

```bash
pip install sentence-transformers
```

### "Out of memory during download"

The model is ~80MB. Ensure you have:
- 500MB free disk space
- Stable internet connection

### "Model files not appearing in APK"

Check the path:
```bash
# Correct path:
composeApp/src/androidMain/assets/models/minilm/

# NOT:
composeApp/src/main/assets/  # Wrong!
```

### "App crashes on model load"

Check logs:
```bash
adb logcat | grep MiniLmEmbeddingEngine
```

Look for:
- "Model not found" → Check assets path
- "ONNX error" → ONNX Runtime issue (currently using placeholders, so this shouldn't happen yet)

---

## 📁 Expected File Structure

```
composeApp/src/androidMain/assets/models/minilm/
├── config.json
├── pytorch_model.bin (or model.safetensors)
├── special_tokens_map.json
├── tokenizer.json
├── tokenizer_config.json
├── vocab.txt
└── metadata.json (created by script)
```

**Size:** ~80-90MB

---

## 🎯 Current Limitations

**Placeholder Embeddings:**
- The current implementation generates deterministic placeholder embeddings
- Semantic search "works" but uses simple hash-based vectors
- To enable REAL embeddings, we need to implement ONNX inference in `MiniLmEmbeddingEngine.android.kt`

**What This Means:**
- ✅ App compiles and runs
- ✅ Memory system works
- ✅ Search returns results
- ⚠️ Search quality limited (placeholder embeddings)
- 🔜 Next step: Implement ONNX inference

---

## 🚀 Next Phase: Real Inference

To replace placeholders with real embeddings:

1. **Load ONNX model** in `MiniLmEmbeddingEngine`
2. **Tokenize input** using WordPiece tokenizer
3. **Run inference** through ONNX Runtime
4. **Mean pooling** over token embeddings
5. **Normalize** output vector

See `GemmaEmbeddingEngine.android.kt` for ONNX inference example structure (has TODOs for implementation).

---

## ✅ Success Checklist

- [ ] Dependencies installed (`sentence-transformers`)
- [ ] Model downloaded (`models/minilm/`)
- [ ] Files copied to assets
- [ ] App rebuilt successfully
- [ ] Model loads without errors (check logs)
- [ ] Semantic search returns results
- [ ] Ready for ONNX inference implementation

---

**Estimated Time:** 5-10 minutes (depending on download speed)
**Disk Space Required:** 500MB (model + build artifacts)
**Internet Required:** Yes (for initial download only)
