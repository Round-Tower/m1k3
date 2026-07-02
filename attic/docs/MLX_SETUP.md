# MLX-LM Setup (Apple Silicon)

Native Metal-accelerated inference for M1K3 using Apple's MLX framework.

## Why MLX?

MLX is built by Apple Research for Apple Silicon. On M-series chips it's ~30-50% faster than Ollama/GGUF for the same model, especially for MoE (Mixture of Experts) architectures like Qwen3-Coder.

## Prerequisites

- Apple Silicon Mac (M1/M2/M3/M4)
- ARM64 Python 3.12+ (NOT Rosetta/x86_64)

### The Rosetta Gotcha

If your Python reports `x86_64` on an M-series Mac, you're running under Rosetta. MLX will not work. Check with:

```bash
python3 -c "import platform; print(platform.machine())"
# Must print: arm64
```

Common cause: Miniforge/conda installed via x86_64 Homebrew (`/usr/local/`). The ARM64 Homebrew lives at `/opt/homebrew/`.

## Quick Setup

```bash
# 1. Install ARM64 Python (if needed)
/opt/homebrew/bin/brew install python@3.12

# 2. Create MLX virtual environment (from project root)
/opt/homebrew/bin/python3.12 -m venv mlx-env

# 3. Install mlx-lm
mlx-env/bin/pip install mlx-lm

# 4. Verify Metal acceleration
mlx-env/bin/python -c "import mlx.core as mx; print(mx.default_device())"
# Should print: Device(gpu, 0)
```

## Starting the Server

```bash
# Small test model (~200MB)
mlx-env/bin/mlx_lm.server --model mlx-community/SmolLM2-135M-Instruct --port 8080

# Qwen3 0.6B 4-bit (~350MB) - good for dev
mlx-env/bin/mlx_lm.server --model mlx-community/Qwen3-0.6B-4bit --port 8080

# Qwen3-Coder-Next 80B 4-bit (~46GB) - the big one (needs 64GB RAM)
mlx-env/bin/mlx_lm.server \
  --model mlx-community/Qwen3-Coder-Next-4bit \
  --port 8080 \
  --trust-remote-code \
  --cache-limit-gb 10
```

M1K3 auto-detects the MLX server on port 8080 at startup. Override with `MLX_SERVER_PORT` env var.

## API

The server exposes an OpenAI-compatible API:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/v1/models` | GET | List available models |
| `/v1/chat/completions` | POST | Chat completions (streaming supported) |

**Note:** This is NOT Ollama's API. There is no `/health`, `/api/tags`, or `/api/generate`.

## Model Formats

| Format | Example | Notes |
|--------|---------|-------|
| Standard 4-bit | `mlx-community/Qwen3-0.6B-4bit` | Good balance of speed/quality |
| DWQ | `mlx-community/Qwen3-8B-DWQ` | Dynamic Weight Quantization, better quality |
| Full precision | `mlx-community/SmolLM2-135M-Instruct` | Highest quality, more RAM |

Models are downloaded from HuggingFace on first use and cached in `~/.cache/huggingface/hub/`.

## M1K3 Integration

The `MLXClient` in `src/engines/ai/ai_inference.py` connects to the server via HTTP. No MLX Python imports needed in the main process.

Priority order: MLX > Ollama > HuggingFace > ctransformers > SimpleAI

`LocalModelManager` in `src/engines/ai/local_model_manager.py` auto-discovers models from the running MLX server via `/v1/models`.

## Performance Tips (M1 Max 64GB)

- **Context limit:** Keep client context to 32-64K tokens. At 100K+ you'll hit swap and crash.
- **High power mode:** `sudo pmset -a powermode 1` when plugged in.
- **Thermal management:** Token speed drops after ~30min of sustained inference. A laptop stand helps.
- **RAM budget:** 4-bit Qwen3-Coder-Next uses ~46GB, leaving ~18GB for OS + KV cache.

## Testing

```bash
# Unit tests (mocked HTTP, no server needed)
python -m pytest tests/python/test_mlx_integration.py -v --noconftest

# Live integration test (requires running server on :8080)
python -c "
from src.engines.ai.ai_inference import MLXClient
client = MLXClient(port=8080)
print('Ready:', client.is_server_ready())
print('Models:', client.get_model_info())
print('Response:', client.generate_sync('Hello!', max_tokens=20))
"
```

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `ModuleNotFoundError: mlx` | Running x86_64 Python | Use `mlx-env/bin/python` (ARM64) |
| Server 404 on `/api/tags` | Using Ollama endpoints | Use `/v1/models` instead |
| OOM crash | Model too large for RAM | Use 4-bit quantization, reduce context |
| Slow tokens (~2 t/s) | Hitting swap memory | Close other apps, use smaller model |
| Port conflict | Another service on 8080 | `MLX_SERVER_PORT=8081 python m1k3.py` |
