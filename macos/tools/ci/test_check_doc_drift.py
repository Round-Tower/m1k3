"""Unit tests for the pure helpers in check_doc_drift."""

import check_doc_drift as m


def test_package_library_products_extracts_only_libraries():
    pkg = """
    products: [
        .library(name: "M1K3Knowledge", targets: ["M1K3Knowledge"]),
        .library(name: "M1K3MemoryChatBridge", targets: ["M1K3MemoryChatBridge"]),
        .executable(name: "M1K3MCP", targets: ["M1K3MCP"]),
    ],
    """
    assert m.package_library_products(pkg) == {"M1K3Knowledge", "M1K3MemoryChatBridge"}


def test_module_map_names_reads_first_cell_incl_multi_module_rows():
    md = """
## Architecture

**Module map** (`Sources/`):

| Target | Role |
|---|---|
| `M1K3Knowledge` | RAG corpus. |
| `M1K3KnowledgeTools` / `M1K3AgentTools` | Agent tools. |
| `M1K3MCPKit` / `M1K3MCP` | MCP server + stdio exe. |

**Brains** (`BrainTier.swift`): `M1K3Inference` is mentioned in prose here — must NOT count.
"""
    assert m.module_map_names(md) == {
        "M1K3Knowledge",
        "M1K3KnowledgeTools",
        "M1K3AgentTools",
        "M1K3MCPKit",
        "M1K3MCP",
    }


def test_diff_flags_undocumented_and_ignores_allowlisted_executable():
    products = {"M1K3Knowledge", "M1K3MemoryChatBridge"}
    documented = {"M1K3Knowledge", "M1K3MCP"}  # M1K3MCP is the allowlisted exe row
    undocumented, ghost = m.diff_modules(products=products, documented=documented)
    assert undocumented == {"M1K3MemoryChatBridge"}
    assert ghost == set()  # M1K3MCP allowlisted, not a ghost


def test_diff_flags_ghost_rows():
    products = {"M1K3Knowledge"}
    documented = {"M1K3Knowledge", "M1K3RemovedModule"}
    undocumented, ghost = m.diff_modules(products=products, documented=documented)
    assert undocumented == set()
    assert ghost == {"M1K3RemovedModule"}


def test_aligned_sets_produce_no_diff():
    both = {"M1K3Knowledge", "M1K3Chat"}
    assert m.diff_modules(products=both, documented=both) == (set(), set())
