# Falcon-H1 Mamba2 Hybrid Test Results

**Date:** 2026-02-14
**Model:** tiiuae/Falcon-H1-0.5B-Instruct
**Architecture:** Hybrid Transformer + Mamba2 State Space Models (SSM)
**Hardware:** M1 Max (32-core GPU), 64GB RAM
**Framework:** MLX 0.30.6 (Metal acceleration)

## What is Mamba2?

[Mamba2](https://github.com/state-spaces/mamba) is a State Space Model (SSM) architecture that offers an alternative to traditional attention mechanisms. It provides:
- **Linear complexity** (O(n) vs O(n²) for attention)
- **Constant memory** during inference
- **Efficient long-context processing**

## Falcon-H1 Hybrid Architecture

[Falcon-H1](https://github.com/tiiuae/Falcon-H1) by TII UAE combines:
- **Mamba2 SSM heads** for efficient sequential processing
- **Attention heads** for complex reasoning
- **Parallel execution** of both mechanisms in each block

This hybrid approach aims to get the best of both worlds: SSM efficiency + attention power.

## Test Results

### Performance Metrics

| Metric | Value |
|--------|-------|
| Average Speed | ~103 tok/s |
| Peak Memory | ~1.08 GB |
| Parameters | 0.5B |
| Context Window | 8K tokens |

### Task Breakdown

| Task | Tokens | Time (s) | Speed (tok/s) |
|------|--------|----------|---------------|
| Technical explanation (Mamba2 SSMs) | 78 | 0.87 | 89 |
| Code generation (Quicksort) | 96 | 0.86 | 112 |
| Concept explanation (Attention) | 58 | 0.52 | 112 |
| Instruction following | 38 | 0.38 | 101 |

**Total:** 270 tokens in 2.63s = ~103 tok/s average

### Quality Observations

✅ **Coherent technical explanations** - explained SSMs accurately
✅ **Valid code generation** - produced working Python quicksort
✅ **Consistent formatting** - maintained markdown code blocks
✅ **Instruction following** - responded appropriately to greetings

## Comparison to Pure Transformer

SmolLM2-135M-Instruct (pure transformer, MLX):
- Speed: ~111 tok/s
- Memory: ~200MB
- Architecture: Standard attention-only

Falcon-H1-0.5B (Mamba2 hybrid):
- Speed: ~103 tok/s
- Memory: ~1.08GB
- Architecture: Hybrid SSM + Attention

**Trade-off:** Slightly slower but ~3.5x more parameters, enabling more complex reasoning.

## M1K3 Integration Status

✅ MLX server detection
✅ Model discovery via `/v1/models`
✅ Streaming generation
✅ Error handling and fallback
✅ Priority ordering (MLX > Ollama > HF > ctransformers)

## Commands

```bash
# Start Falcon-H1 Mamba2 server
mlx-env/bin/mlx_lm.server \
  --model tiiuae/Falcon-H1-0.5B-Instruct \
  --port 8080

# Test via M1K3 MLXClient
python -c "
from src.engines.ai.ai_inference import MLXClient
client = MLXClient(model='tiiuae/Falcon-H1-0.5B-Instruct', port=8080)
print(client.generate_sync('Explain Mamba2', max_tokens=50))
"
```

## Resources

- [Falcon-H1 GitHub](https://github.com/tiiuae/Falcon-H1)
- [Falcon-H1 HuggingFace](https://huggingface.co/tiiuae/Falcon-H1-0.5B-Instruct)
- [Mamba2 Paper](https://arxiv.org/abs/2405.21060)
- [MLX Framework](https://github.com/ml-explore/mlx)
- [mlx-lm](https://github.com/ml-explore/mlx-lm)

## Next Steps

- [ ] Test Falcon-H1-3B-Instruct (larger hybrid model)
- [ ] Compare pure Mamba2 models (mlx-community/mamba2-370m-4bit)
- [ ] Benchmark long-context performance (SSM advantage)
- [ ] Test Qwen3-Coder-Next-4bit (80B MoE with MLX)
