# Agent System Architecture Research
**Multi-Platform Agent Implementation for M1K3 and 間 AI**

**Date:** 2025-11-02
**Status:** Research Complete, Ready for Implementation

---

## Executive Summary

This document provides architectural recommendations for implementing agent systems with task planning, tool calling, and multi-step reasoning capabilities across both M1K3 (Python CLI/Desktop) and 間 AI (Kotlin Multiplatform Mobile) platforms.

**Key Finding:** Both platforms have strong existing infrastructure that can be leveraged for agent functionality with minimal architectural disruption.

---

## 1. Recommended Agent Patterns

### M1K3 (Python CLI): ReAct Pattern
**Best for:** Desktop/CLI with transparent reasoning

```
User Query → Reasoning Step → Action Selection → Tool Execution
  → Observation → Reasoning Step → ... → Final Answer
```

**Example:**
```
User: "Find information about quantum computing and summarize it"

Thought: I need to search for quantum computing information
Action: rag_search(query="quantum computing", max_results=5)
Observation: [5 documents retrieved]

Thought: I should recall past discussions
Action: memory_recall(query="quantum computing")
Observation: [2 past conversations]

Final Answer: [Synthesized response]
```

### 間 AI (Kotlin/Mobile): Function Calling Pattern
**Best for:** Mobile with battery/latency constraints

```
User Query → Intent Classification → Tool Selection
  → Parameter Extraction → Tool Execution → Response Synthesis
```

**Example:**
```
User: "Take a photo and tell me what's in it"

Intent: MULTIMODAL_REQUEST
Tools: [camera_capture, vision_analyze]

Step 1: camera_capture() → image_uri
Step 2: vision_analyze(image_uri) → labels, objects
Final Response: "I see a dog in a park with grass."
```

---

## 2. Core Agent Architecture

### Shared Components (70-80% code reuse)

```
┌─────────────────────────────────────────────────────┐
│              Agent Orchestrator                     │
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ Task Planner │  │ Tool Registry│  │ Memory   │ │
│  │              │  │              │  │ Manager  │ │
│  │ • Decompose  │  │ • Discovery  │  │ • State  │ │
│  │ • Schedule   │  │ • Validation │  │ • History│ │
│  │ • Track      │  │ • Execution  │  │          │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │         Reasoning Engine                     │  │
│  │  • Intent Classification (EXISTING)          │  │
│  │  • Chain-of-Thought                          │  │
│  │  • Error Detection & Recovery                │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **Tool-Centric**: Agents select and invoke pre-defined tools (no code generation)
2. **Intent-Driven**: Leverage existing IntentClassificationEngine
3. **Memory-Augmented**: Use VectorMemoryManager for context
4. **Privacy-First**: 100% local processing
5. **Graceful Degradation**: Fallback strategies for failures

---

## 3. Existing Infrastructure to Leverage

### M1K3 Strengths
✅ **LocalAIEngine** - Multi-backend AI with streaming
✅ **M1K3RAGEngine** - 1,341+ documents, semantic search
✅ **VectorMemoryManager** - HNSW index, importance scoring
✅ **IntentClassificationEngine** - 18 intents, 7 response strategies
✅ **CLIAIResponseProcessor** - State machine for multi-step execution

**Integration Point:** `src/engines/ai/ai_inference.py:LocalAIEngine`
- Add `agent_orchestrator` attribute
- Add `enable_agent()` method
- Modify `generate_response()` to check agent mode

### 間 AI Strengths (Planned)
✅ **SmolLM2-360M** - 24K context, ONNX Runtime
✅ **MiniLM-L6 Embeddings** - Compatible with M1K3 (384D)
✅ **JVector HNSW** - Fast vector search
✅ **Multi-Modal Support** - CameraX, ML Kit
✅ **Project-Scoped** - Natural agent workflow boundaries

**Integration Point:** New `AgentCapableAIEngine` interface
- Implement in Phase 3 (Knowledge Systems)
- Extend with multi-modal tools in Phase 4
- Polish agent UX in Phase 5

---

## 4. Tool Architecture

### M1K3 Python Tools

```python
from dataclasses import dataclass
from typing import Callable, Dict, Any

@dataclass
class Tool:
    name: str
    category: ToolCategory  # INFORMATION, COMPUTATION, MANIPULATION, SYSTEM
    description: str
    parameters: Dict[str, Any]  # JSON schema
    execute: Callable
    requires_confirmation: bool = False

class ToolRegistry:
    def register_tool(self, tool: Tool): ...
    def get_tool(self, name: str) -> Optional[Tool]: ...
    def list_tools(self, category=None) -> List[Tool]: ...
```

**Core Tools (Priority 1):**
- `rag_search` - Search knowledge base
- `memory_recall` - Recall past conversations
- `calculator` - Mathematical calculations

**Additional Tools (Priority 2):**
- `text_analysis` - Sentiment, keywords, entities
- `file_search` - Search local files
- `code_executor` - Safe code execution

### 間 AI Kotlin Tools

```kotlin
// commonMain/domain/agent/Tool.kt
interface Tool {
    val name: String
    val category: ToolCategory
    val description: String
    val parameters: Map<String, ParameterSpec>
    val requiresConfirmation: Boolean

    suspend fun execute(params: Map<String, Any>): Result<ToolResult>
}

enum class ToolCategory {
    INFORMATION,    // RAG, memory, trivia
    COMPUTATION,    // Math, analysis
    MULTIMODAL,     // Camera, vision
    DEVICE,         // Sensors, device info
    SYSTEM          // Settings, config
}
```

**Platform-Specific Tools:**

**Android:**
- `camera_capture` - CameraX image capture
- `vision_analyze` - ML Kit (OCR, objects, labels)
- `gallery_select` - Pick from gallery
- `device_info` - SoC, RAM, battery status

**Common (Shared):**
- `calculator` - Math evaluation
- `memory_recall` - Vector search
- `rag_search` - Knowledge retrieval
- `trivia_lookup` - Fact search

---

## 5. Implementation Roadmap

### M1K3 Agent Implementation (4-6 weeks)

**Week 1-2: Foundation**
- [ ] Create `src/agent/tool.py` with Tool dataclass and ToolRegistry
- [ ] Implement 3 core tools: RAG, memory, calculator
- [ ] Add `enable_agent()` to LocalAIEngine
- [ ] CLI command: `/agent enable`

**Week 3-4: Orchestrator**
- [ ] Create `src/agent/orchestrator.py` with AgentOrchestrator
- [ ] Implement plan parser for tool call extraction
- [ ] Add tool execution engine with error handling
- [ ] CLI commands: `/agent tools`, `/agent plan <query>`

**Week 5-6: Polish**
- [ ] Progress indicators and step visualization
- [ ] Confirmation prompts for sensitive tools
- [ ] Comprehensive testing (unit + integration)
- [ ] Documentation and examples

**Deliverables:**
- Agent system with 5-10 tools
- CLI interface for agent workflows
- 80%+ test coverage
- User documentation

### 間 AI Agent Implementation (8-10 weeks, integrated)

**Phase 3 Enhancement (Weeks 9-10):**
- [ ] PHASE3-016: Define Tool interface (commonMain) - 2 days
- [ ] PHASE3-017: Implement ToolRegistry - 2 days
- [ ] PHASE3-018: Create information tools (RAG, memory, trivia) - 3 days
- [ ] PHASE3-019: Basic AgentOrchestrator - 3 days

**Phase 4 Enhancement (Weeks 11-12):**
- [ ] PHASE4-021: CameraTool with CameraX - 3 days
- [ ] PHASE4-022: MLKitVisionTool (OCR, labels, objects) - 3 days
- [ ] PHASE4-023: GalleryTool for image selection - 2 days
- [ ] PHASE4-024: Multi-modal agent workflows - 4 days

**Phase 5 Enhancement (Weeks 13-15):**
- [ ] PHASE5-031: Multi-step planning workflow - 4 days
- [ ] PHASE5-032: Tool confirmation UI - 3 days
- [ ] PHASE5-033: Agent progress visualization - 3 days
- [ ] PHASE5-034: Device intelligence tools - 2 days
- [ ] PHASE5-035: Agent analytics - 2 days

**Phase 6 Enhancement (Week 16):**
- [ ] PHASE6-011: Agent integration tests - 2 days
- [ ] PHASE6-012: Tool execution stress tests - 1 day
- [ ] PHASE6-013: Multi-modal E2E tests - 2 days

**Deliverables:**
- 10-15 tools (information, computation, multi-modal, device)
- Compose UI for agent task visualization
- Project-scoped agent workflows
- Comprehensive test coverage

---

## 6. Performance Targets

### M1K3 Python
| Metric | Target | Notes |
|--------|--------|-------|
| Intent classification | <50ms | Existing system |
| Plan generation | 2-5s | Depends on complexity |
| Tool execution | 10ms-5s | Varies by tool |
| Total workflow | 5-30s | Complex multi-step |

### 間 AI Mobile
| Metric | Target | Notes |
|--------|--------|-------|
| APK size impact | +10-20MB | Within 200MB budget |
| Battery impact | <0.5%/hour | Agent overhead only |
| Plan generation | 3-8s | Mid-range device |
| Tool execution | 100ms-5s | Varies by tool |
| Total workflow | 10-60s | Complex multi-step |

**Optimization Strategies:**
1. Plan caching (similar queries)
2. Lazy tool loading
3. Parallel execution for independent tools
4. Result streaming to UI
5. Incremental planning

---

## 7. Integration Code Snippets

### M1K3: Enable Agent Mode

```python
# In src/engines/ai/ai_inference.py
class LocalAIEngine:
    def __init__(self, ...):
        # ... existing code ...
        self.agent_orchestrator = None
        self.agent_enabled = False

    def enable_agent(self, tools: List[Tool] = None):
        """Enable agent mode with optional custom tools"""
        from src.agent.orchestrator import AgentOrchestrator
        from src.agent.tools import get_default_tools

        tool_registry = ToolRegistry()
        for tool in (tools or get_default_tools()):
            tool_registry.register_tool(tool)

        self.agent_orchestrator = AgentOrchestrator(
            ai_engine=self,
            rag_engine=self.rag_engine,
            memory_manager=get_vector_memory_manager(),
            tool_registry=tool_registry
        )
        self.agent_enabled = True
        print(f"🤖 Agent mode enabled with {len(tool_registry.tools)} tools")

    def generate_response(self, prompt: str, use_agent: bool = None):
        use_agent_mode = use_agent if use_agent is not None else self.agent_enabled

        if use_agent_mode and self.agent_orchestrator:
            # Agent workflow
            return self.agent_orchestrator.execute_task(prompt)
        else:
            # Existing direct generation
            # ... existing code ...
```

### 間 AI: Agent-Capable AI Engine

```kotlin
// commonMain/domain/ai/AgentCapableAIEngine.kt
interface AgentCapableAIEngine : AIEngine {
    val agentOrchestrator: AgentOrchestrator

    suspend fun executeWithAgent(
        query: String,
        projectId: String? = null
    ): Flow<AgentResponse>
}

// androidMain/ai/AndroidAgentAIEngine.kt
class AndroidAgentAIEngine(
    private val onnxEngine: OnnxAIEngine,
    private val memoryManager: MemoryManager,
    private val toolRegistry: ToolRegistry
) : AgentCapableAIEngine {

    override val agentOrchestrator = AgentOrchestrator(
        aiEngine = onnxEngine,
        memoryManager = memoryManager,
        toolRegistry = toolRegistry,
        intentClassifier = IntentClassifier()
    )

    override suspend fun executeWithAgent(
        query: String,
        projectId: String?
    ): Flow<AgentResponse> {
        return agentOrchestrator.executeTask(query, projectId)
    }
}
```

### Compose UI: Agent Progress

```kotlin
@Composable
fun AgentTaskScreen(query: String, viewModel: AgentViewModel) {
    val agentState by viewModel.agentState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = query, style = MaterialTheme.typography.headlineSmall)

        when (val state = agentState) {
            is AgentState.Thinking -> {
                CircularProgressIndicator()
                Text("Thinking: ${state.message}")
            }

            is AgentState.Planning -> {
                Text("Plan:", style = MaterialTheme.typography.titleMedium)
                state.steps.forEach { step ->
                    PlanStepItem(step)
                }
            }

            is AgentState.Executing -> {
                state.steps.forEachIndexed { index, step ->
                    StepProgressItem(
                        step = step,
                        isActive = index == state.currentStepIndex,
                        isComplete = index < state.currentStepIndex
                    )
                }
            }

            is AgentState.Complete -> {
                MarkdownText(state.finalResponse)
            }
        }
    }
}
```

---

## 8. Success Criteria

### M1K3 Agent Success
- [ ] 80%+ user queries correctly classified (agent vs direct)
- [ ] <5s plan generation for complex tasks
- [ ] >90% tool execution success rate
- [ ] >80% user satisfaction with agent responses

### 間 AI Agent Success
- [ ] <10MB APK size increase
- [ ] <2%/hour battery impact (including agent overhead)
- [ ] <8s plan generation on mid-range devices (6GB RAM)
- [ ] >85% tool execution success rate
- [ ] 95%+ accessibility score (WCAG 2.2 AA)

---

## 9. Risk Mitigation

### Technical Risks

**Model Limitations (Small models struggle with planning)**
- **Mitigation:** Start simple, structured prompts, provide examples

**Tool Latency (Camera/vision tools slow)**
- **Mitigation:** Async execution, progress indicators, cancellation support

**APK Size (Agent code may exceed budget)**
- **Mitigation:** Lazy loading, modular tools, optional downloads

### UX Risks

**User Confusion (Complex workflow)**
- **Mitigation:** Clear visualization, step-by-step explanation

**Confirmation Fatigue (Too many prompts)**
- **Mitigation:** Smart confirmation (only when needed), user preferences

**Long Wait Times (30+ seconds)**
- **Mitigation:** Streaming progress, intermediate results, background execution

---

## 10. Future Enhancements

### Short-Term (3-6 months)
- Expand tool ecosystem (code execution, web scraping, API calls)
- Plan optimization (learning from execution history)
- Enhanced memory (tool execution history in vector store)

### Medium-Term (6-12 months)
- Cross-platform sync (encrypted P2P between M1K3 and 間 AI)
- Advanced reasoning (tree-of-thought, self-correction)
- Personality integration (tool selection influenced by traits)

### Long-Term (12+ months)
- Federated learning (privacy-preserving plan sharing)
- Custom tool creation (visual programming, tool composition)
- Multi-agent collaboration (specialized sub-agents, hierarchical coordination)

---

## 11. Recommended Next Steps

### Priority 1: M1K3 Proof of Concept (Week 1-2)
1. Create `src/agent/` directory structure
2. Implement Tool dataclass and ToolRegistry
3. Build 3 core tools: RAG, memory, calculator
4. Add basic AgentOrchestrator (single-step execution)
5. CLI command: `/agent test <query>`

### Priority 2: M1K3 Full Implementation (Week 3-6)
1. Multi-step planning workflow
2. Tool execution engine with error handling
3. Progress visualization in CLI
4. Comprehensive testing
5. User documentation

### Priority 3: 間 AI Integration (Week 9+)
1. Define Tool interface in commonMain (Phase 3)
2. Implement information tools (RAG, memory, trivia)
3. Add multi-modal tools in Phase 4 (camera, vision)
4. Polish agent UX in Phase 5 (progress, confirmations)
5. Integration testing in Phase 6

---

## Conclusion

Both M1K3 and 間 AI have strong existing infrastructure for agent systems:
- Intent classification for task routing
- RAG for knowledge retrieval
- Vector memory for context
- Project-scoped workflows (間 AI)
- Multi-modal capabilities (間 AI)

**Agent systems are a natural evolution, not a radical departure.**

The recommended approach is tool-centric (agents select tools, don't generate code), privacy-first (100% local), and leverages existing components with minimal disruption. Starting with M1K3 allows faster iteration and pattern validation before porting to mobile.

**Estimated Effort:**
- M1K3: 4-6 weeks (dedicated)
- 間 AI: 8-10 weeks (integrated into existing phases)
- Shared code: 70-80% (domain logic, common tools)

---

**Last Updated:** 2025-11-02
**Status:** Research Complete, Ready for Implementation Planning
