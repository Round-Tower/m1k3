#!/bin/bash
# Script to launch the M1K3 MCP Server

echo "Starting M1K3 MCP Server on port 8001..."
uvicorn mcp_server:app --host 0.0.0.0 --port 8001
