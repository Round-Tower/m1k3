#!/usr/bin/env python3
"""
Tests for MLX-LM integration in M1K3.
Covers: MLXClient, model discovery, OpenAI-compatible API parsing, DWQ support.

TDD: Written BEFORE implementation fixes.
"""

import json
import pytest
from unittest.mock import patch, MagicMock, PropertyMock


# ---------------------------------------------------------------------------
# Fixtures: mock HTTP responses matching mlx_lm.server's OpenAI-compatible API
# ---------------------------------------------------------------------------

def _make_models_response(models: list[dict] | None = None) -> dict:
    """Build a /v1/models response in OpenAI format."""
    if models is None:
        models = [
            {"id": "mlx-community/Qwen3-Coder-Next-4bit", "object": "model"},
        ]
    return {"object": "list", "data": models}


def _make_sse_chunks(tokens: list[str]) -> list[bytes]:
    """Build SSE byte lines like mlx_lm.server streams them."""
    lines = []
    for token in tokens:
        chunk = {
            "choices": [{"delta": {"content": token}, "index": 0}]
        }
        lines.append(f"data: {json.dumps(chunk)}".encode("utf-8"))
    lines.append(b"data: [DONE]")
    return lines


# ---------------------------------------------------------------------------
# MLXClient tests
# ---------------------------------------------------------------------------

class TestMLXClientDefaults:
    """Bug 3: Default port must be 8080 (mlx_lm.server default), not 11435."""

    def test_default_port_is_8080(self):
        with patch.dict("os.environ", {}, clear=False):
            # Remove any override so we get the true default
            import os
            os.environ.pop("MLX_SERVER_PORT", None)
            os.environ.pop("MLX_MODEL", None)

            # Re-import to pick up defaults
            import importlib
            from src.engines.ai import ai_inference
            importlib.reload(ai_inference)

            assert ai_inference.MLX_SERVER_PORT == 8080

    def test_default_model_name(self):
        import os
        os.environ.pop("MLX_MODEL", None)
        import importlib
        from src.engines.ai import ai_inference
        importlib.reload(ai_inference)

        assert ai_inference.MLX_MODEL_NAME == "mlx-community/Qwen3-Coder-Next-4bit"


class TestMLXClientServerReady:
    """Bug 4: is_server_ready must hit /v1/models, not /health."""

    def test_server_ready_uses_v1_models(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = _make_models_response()

        with patch.object(client._session, "get", return_value=mock_response) as mock_get:
            result = client.is_server_ready()

        mock_get.assert_called_once()
        call_url = mock_get.call_args[0][0]
        assert "/v1/models" in call_url, f"Expected /v1/models, got {call_url}"
        assert "/health" not in call_url
        assert result is True

    def test_server_not_ready_on_connection_error(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)

        with patch.object(client._session, "get", side_effect=ConnectionError("refused")):
            assert client.is_server_ready() is False


class TestMLXClientGetModelInfo:
    """Bug 4: get_model_info must use /v1/models and parse OpenAI format."""

    def test_get_model_info_uses_v1_models(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="mlx-community/Qwen3-Coder-Next-4bit", port=8080)

        models_data = _make_models_response([
            {"id": "mlx-community/Qwen3-Coder-Next-4bit", "object": "model"},
            {"id": "mlx-community/SmolLM2-135M-4bit", "object": "model"},
        ])
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = models_data

        with patch.object(client._session, "get", return_value=mock_response) as mock_get:
            result = client.get_model_info()

        call_url = mock_get.call_args[0][0]
        assert "/v1/models" in call_url
        assert "/api/tags" not in call_url
        assert result.get("id") == "mlx-community/Qwen3-Coder-Next-4bit"

    def test_get_model_info_returns_empty_on_404(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)
        mock_response = MagicMock()
        mock_response.status_code = 404

        with patch.object(client._session, "get", return_value=mock_response):
            assert client.get_model_info() == {}


class TestMLXClientGenerate:
    """Streaming generation via /v1/chat/completions (already correct endpoint)."""

    def test_generate_streams_tokens(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)

        sse_lines = _make_sse_chunks(["Hello", " world", "!"])
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.iter_lines.return_value = iter(sse_lines)

        with patch.object(client._session, "post", return_value=mock_response):
            tokens = list(client.generate("Hi", max_tokens=64))

        assert tokens == ["Hello", " world", "!"]

    def test_generate_includes_system_prompt(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.iter_lines.return_value = iter([b"data: [DONE]"])

        with patch.object(client._session, "post", return_value=mock_response) as mock_post:
            list(client.generate("Hi", system_prompt="Be helpful"))

        payload = mock_post.call_args[1]["json"]
        assert payload["messages"][0] == {"role": "system", "content": "Be helpful"}
        assert payload["messages"][1] == {"role": "user", "content": "Hi"}

    def test_generate_sync_returns_full_string(self):
        from src.engines.ai.ai_inference import MLXClient

        client = MLXClient(model="test-model", port=8080)
        sse_lines = _make_sse_chunks(["foo", "bar"])
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.iter_lines.return_value = iter(sse_lines)

        with patch.object(client._session, "post", return_value=mock_response):
            result = client.generate_sync("test")

        assert result == "foobar"

    def test_generate_raises_on_server_error(self):
        """Error responses should raise MLXServerError, not yield error strings."""
        from src.engines.ai.ai_inference import MLXClient, MLXServerError

        client = MLXClient(model="test-model", port=8080)
        mock_response = MagicMock()
        mock_response.status_code = 500

        with patch.object(client._session, "post", return_value=mock_response):
            with pytest.raises(MLXServerError):
                list(client.generate("Hi"))

    def test_generate_raises_on_connection_error(self):
        """Connection failures should raise MLXServerError."""
        from src.engines.ai.ai_inference import MLXClient, MLXServerError

        client = MLXClient(model="test-model", port=8080)

        with patch.object(client._session, "post", side_effect=ConnectionError("refused")):
            with pytest.raises(MLXServerError):
                list(client.generate("Hi"))


# ---------------------------------------------------------------------------
# Backend availability gate
# ---------------------------------------------------------------------------

class TestBackendAvailabilityGate:
    """Bug 5: MLX must be included in the backend availability check."""

    def test_mlx_alone_does_not_raise(self):
        """If only MLX HTTP client is available, module should not raise ImportError."""
        # This is a design test - the gate at module level should include MLX_LM_AVAILABLE
        from src.engines.ai import ai_inference

        # The gate line should reference MLX
        import inspect
        source = inspect.getsource(ai_inference)
        # Verify the availability gate includes MLX
        assert "MLX_HTTP_AVAILABLE" in source or "MLX_LM_AVAILABLE" in source


class TestCtransformersErrorMessage:
    """Bug 1: Typo - should say 'not available' when import fails."""

    def test_ctransformers_error_message_says_not_available(self):
        import inspect
        from src.engines.ai import ai_inference

        source = inspect.getsource(ai_inference)
        # The string "ctransformers available" WITHOUT "not" should NOT appear
        # in an except/error context
        lines = source.split("\n")
        for i, line in enumerate(lines):
            if "CTRANSFORMERS_AVAILABLE = False" in line:
                # The next print line should say "not available"
                for j in range(i + 1, min(i + 3, len(lines))):
                    if "ctransformers" in lines[j].lower() and "print" in lines[j]:
                        assert "not available" in lines[j], \
                            f"Expected 'not available' in error message, got: {lines[j].strip()}"
                        break


# ---------------------------------------------------------------------------
# Model discovery in LocalModelManager
# ---------------------------------------------------------------------------

class TestMLXModelDiscovery:
    """Bug 6: _discover_mlx_models must use /v1/models with OpenAI format."""

    def test_discovery_uses_v1_models_endpoint(self):
        from src.engines.ai.local_model_manager import LocalModelManager

        models_response = _make_models_response([
            {"id": "mlx-community/Qwen3-Coder-Next-4bit", "object": "model"},
            {"id": "mlx-community/SmolLM2-135M-4bit", "object": "model"},
        ])

        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = models_response

        with patch("requests.get", return_value=mock_resp) as mock_get:
            mgr = LocalModelManager.__new__(LocalModelManager)
            result = mgr._discover_mlx_models()

        call_url = mock_get.call_args[0][0]
        assert "/v1/models" in call_url, f"Expected /v1/models, got {call_url}"
        assert "/api/tags" not in call_url

    def test_discovery_parses_openai_format(self):
        from src.engines.ai.local_model_manager import LocalModelManager

        models_response = _make_models_response([
            {"id": "mlx-community/Qwen3-Coder-Next-4bit", "object": "model"},
        ])

        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = models_response

        with patch("requests.get", return_value=mock_resp):
            mgr = LocalModelManager.__new__(LocalModelManager)
            result = mgr._discover_mlx_models()

        assert "mlx-community/Qwen3-Coder-Next-4bit" in result
        spec = result["mlx-community/Qwen3-Coder-Next-4bit"]
        assert spec.model_type == "mlx"

    def test_discovery_default_port_is_8080(self):
        """Bug 3 in model manager: default port must also be 8080."""
        import os
        os.environ.pop("MLX_SERVER_PORT", None)

        from src.engines.ai.local_model_manager import LocalModelManager

        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = _make_models_response()

        with patch("requests.get", return_value=mock_resp) as mock_get:
            mgr = LocalModelManager.__new__(LocalModelManager)
            mgr._discover_mlx_models()

        call_url = mock_get.call_args[0][0]
        assert ":8080/" in call_url, f"Expected port 8080 in URL, got {call_url}"


# ---------------------------------------------------------------------------
# DWQ model support
# ---------------------------------------------------------------------------

class TestDWQModelCharacteristics:
    """Bug 7: DWQ (Dynamic Weight Quantization) models should be recognized."""

    def test_dwq_model_recognized(self):
        from src.engines.ai.local_model_manager import LocalModelManager

        mgr = LocalModelManager.__new__(LocalModelManager)
        desc, speed = mgr._analyze_mlx_model_characteristics(
            "mlx-community/Qwen3-8B-DWQ", 4500.0
        )
        assert "DWQ" in desc or "Dynamic Weight" in desc

    def test_qwen3_coder_recognized(self):
        from src.engines.ai.local_model_manager import LocalModelManager

        mgr = LocalModelManager.__new__(LocalModelManager)
        desc, speed = mgr._analyze_mlx_model_characteristics(
            "mlx-community/Qwen3-Coder-Next-4bit", 46000.0
        )
        assert "Qwen3" in desc
        assert "code" in desc.lower() or "Coder" in desc

    def test_generic_mlx_model_fallback(self):
        from src.engines.ai.local_model_manager import LocalModelManager

        mgr = LocalModelManager.__new__(LocalModelManager)
        desc, speed = mgr._analyze_mlx_model_characteristics(
            "mlx-community/SomeNewModel-3B", 1500.0
        )
        assert "MLX" in desc
