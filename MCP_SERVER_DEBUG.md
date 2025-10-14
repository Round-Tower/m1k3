# Ticket: Debug MCP Server Startup

**Issue:**
The `mcp_server.py` fails to start when run via `pytest` or directly. The tests time out waiting for the server to become available.

**Last Known Error:**
The server process exits prematurely. Initial debugging attempts to capture the error log were unsuccessful due to the testing environment suppressing the output.

**Next Steps:**
1. Directly run `python mcp_server.py` and ensure the process output is captured to identify the root cause of the startup failure.
2. Fix the underlying initialization error in `mcp_server.py`.
3. Re-run the test suite in `tests/test_mcp_server.py` to confirm the fix and validate the TTS/STT endpoints.
