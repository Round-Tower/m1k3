# Agent System Architecture - Detailed Integration Plan
**Comprehensive Implementation Guide for M1K3 and 間 AI Agent Systems**

**Version:** 1.0
**Date:** 2025-11-02
**Status:** Ready for Implementation

---

## Table of Contents

1. [Overview](#1-overview)
2. [M1K3 Python Implementation](#2-m1k3-python-implementation)
3. [間 AI Kotlin Implementation](#3-間-ai-kotlin-implementation)
4. [Shared Architecture Patterns](#4-shared-architecture-patterns)
5. [Tool Development Guide](#5-tool-development-guide)
6. [Testing Strategy](#6-testing-strategy)
7. [Performance Optimization](#7-performance-optimization)
8. [Migration Guide](#8-migration-guide)

---

## 1. Overview

### 1.1 Purpose

This document provides a detailed integration plan for implementing agent systems in both M1K3 (Python CLI) and 間 AI (Kotlin Multiplatform Mobile). It serves as the authoritative implementation guide with code examples, file structures, and integration steps.

### 1.2 Design Philosophy

**Core Principles:**
1. **Tool-Centric Design**: Agents select and invoke pre-defined tools (no code generation)
2. **Minimal Integration Footprint**: Leverage existing systems without major refactoring
3. **Privacy-First**: 100% local processing, zero external API calls
4. **Cross-Platform Compatibility**: 70-80% code reuse via shared patterns
5. **Graceful Degradation**: Robust fallbacks for tool failures

### 1.3 Architecture Summary

```
┌─────────────────────────────────────────────────────┐
│              Agent Orchestrator                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ Task Planner │  │ Tool Registry│  │ Context  │ │
│  │              │  │              │  │ Manager  │ │
│  │ • Decompose  │  │ • Discovery  │  │ • State  │ │
│  │ • Schedule   │  │ • Validation │  │ • History│ │
│  │ • Monitor    │  │ • Execution  │  │ • Memory │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │         Reasoning Engine                     │  │
│  │  • Intent Classification                     │  │
│  │  • Chain-of-Thought                          │  │
│  │  • Error Detection & Recovery                │  │
│  │  • Plan Generation & Parsing                 │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
└─────────────────────────────────────────────────────┘
               ↓               ↓               ↓
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │  Tools   │   │  Tools   │   │  Tools   │
        │          │   │          │   │          │
        │ RAG      │   │ Vision   │   │ Device   │
        │ Memory   │   │ Camera   │   │ Sensors  │
        │ Math     │   │ ML Kit   │   │ System   │
        └──────────┘   └──────────┘   └──────────┘
```

---

## 2. M1K3 Python Implementation

### 2.1 Directory Structure

```
src/
├── agent/                          # NEW: Agent system
│   ├── __init__.py
│   ├── tool.py                     # Tool base classes
│   ├── registry.py                 # Tool registry
│   ├── orchestrator.py             # Main orchestrator
│   ├── planner.py                  # Task planning
│   ├── executor.py                 # Tool execution
│   ├── parser.py                   # Plan parsing
│   └── tools/                      # Tool implementations
│       ├── __init__.py
│       ├── rag_tool.py             # RAG search
│       ├── memory_tool.py          # Memory recall
│       ├── calculator_tool.py      # Math calculator
│       ├── text_analysis_tool.py   # Text analysis
│       └── file_search_tool.py     # File search
│
├── engines/
│   └── ai/
│       └── ai_inference.py         # MODIFY: Add agent support
│
├── cli/
│   └── cli_ai_handler.py           # MODIFY: Add /agent commands
│
└── utils/
    └── intent_classification_system.py  # EXISTING: Leverage for agent
```

### 2.2 Core Implementation Files

#### 2.2.1 Tool Base Classes (`src/agent/tool.py`)

```python
#!/usr/bin/env python3
"""
Agent Tool Base Classes
Defines the core tool interface and categories for M1K3 agent system
"""

from dataclasses import dataclass, field
from typing import Callable, Dict, Any, Optional, List
from enum import Enum
import time


class ToolCategory(Enum):
    """Tool categories for organization and filtering"""
    INFORMATION = "information"      # RAG, search, memory, knowledge retrieval
    COMPUTATION = "computation"      # Math, code execution, data processing
    MANIPULATION = "manipulation"    # File ops, data transformation
    ANALYSIS = "analysis"           # Text analysis, sentiment, pattern detection
    SYSTEM = "system"               # Settings, config, system info


@dataclass
class ToolParameter:
    """Definition of a tool parameter"""
    name: str
    type: type
    required: bool = False
    default: Any = None
    description: str = ""

    def validate(self, value: Any) -> bool:
        """Validate parameter value"""
        if self.required and value is None:
            return False
        if value is not None and not isinstance(value, self.type):
            return False
        return True


@dataclass
class ToolResult:
    """Result from tool execution"""
    success: bool
    data: Any = None
    error: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)
    execution_time: float = 0.0

    def __str__(self):
        if self.success:
            return f"✅ Success: {self.data}"
        else:
            return f"❌ Error: {self.error}"


@dataclass
class Tool:
    """
    Base tool definition for M1K3 agent system

    Agents select and invoke tools to accomplish tasks.
    Each tool has a name, description, parameters, and execution function.
    """
    name: str
    category: ToolCategory
    description: str
    parameters: List[ToolParameter]
    execute: Callable[..., ToolResult]
    requires_confirmation: bool = False
    examples: List[str] = field(default_factory=list)

    def get_parameter(self, name: str) -> Optional[ToolParameter]:
        """Get parameter definition by name"""
        for param in self.parameters:
            if param.name == name:
                return param
        return None

    def validate_parameters(self, params: Dict[str, Any]) -> tuple[bool, Optional[str]]:
        """Validate provided parameters against tool definition"""
        # Check required parameters
        for param in self.parameters:
            if param.required and param.name not in params:
                return False, f"Missing required parameter: {param.name}"

            # Validate parameter value if provided
            if param.name in params:
                if not param.validate(params[param.name]):
                    return False, f"Invalid value for parameter: {param.name}"

        return True, None

    def execute_safe(self, **kwargs) -> ToolResult:
        """Execute tool with validation and error handling"""
        start_time = time.time()

        # Validate parameters
        valid, error = self.validate_parameters(kwargs)
        if not valid:
            return ToolResult(
                success=False,
                error=error,
                execution_time=time.time() - start_time
            )

        # Fill in defaults
        params = {}
        for param in self.parameters:
            if param.name in kwargs:
                params[param.name] = kwargs[param.name]
            elif param.default is not None:
                params[param.name] = param.default

        # Execute with error handling
        try:
            result = self.execute(**params)
            result.execution_time = time.time() - start_time
            return result
        except Exception as e:
            return ToolResult(
                success=False,
                error=f"Tool execution failed: {str(e)}",
                execution_time=time.time() - start_time
            )

    def to_dict(self) -> Dict[str, Any]:
        """Convert tool definition to dictionary for serialization"""
        return {
            "name": self.name,
            "category": self.category.value,
            "description": self.description,
            "parameters": [
                {
                    "name": p.name,
                    "type": p.type.__name__,
                    "required": p.required,
                    "default": p.default,
                    "description": p.description
                }
                for p in self.parameters
            ],
            "requires_confirmation": self.requires_confirmation,
            "examples": self.examples
        }
```

#### 2.2.2 Tool Registry (`src/agent/registry.py`)

```python
#!/usr/bin/env python3
"""
Tool Registry
Central registry for discovering and managing agent tools
"""

from typing import Dict, List, Optional
from src.agent.tool import Tool, ToolCategory


class ToolRegistry:
    """
    Central registry for agent tools

    Provides tool discovery, validation, and execution management
    """

    def __init__(self):
        self.tools: Dict[str, Tool] = {}
        self._register_default_tools()

    def register_tool(self, tool: Tool) -> None:
        """Register a tool in the registry"""
        if tool.name in self.tools:
            print(f"⚠️  Warning: Tool '{tool.name}' already registered, overwriting")

        self.tools[tool.name] = tool
        print(f"✅ Registered tool: {tool.name} ({tool.category.value})")

    def unregister_tool(self, name: str) -> bool:
        """Unregister a tool by name"""
        if name in self.tools:
            del self.tools[name]
            print(f"🗑️  Unregistered tool: {name}")
            return True
        return False

    def get_tool(self, name: str) -> Optional[Tool]:
        """Get a tool by name"""
        return self.tools.get(name)

    def list_tools(self, category: Optional[ToolCategory] = None) -> List[Tool]:
        """List all tools, optionally filtered by category"""
        if category:
            return [t for t in self.tools.values() if t.category == category]
        return list(self.tools.values())

    def search_tools(self, query: str) -> List[Tool]:
        """Search tools by name or description"""
        query_lower = query.lower()
        results = []

        for tool in self.tools.values():
            if (query_lower in tool.name.lower() or
                query_lower in tool.description.lower()):
                results.append(tool)

        return results

    def get_tool_summary(self) -> str:
        """Get formatted summary of all tools"""
        lines = ["📋 Available Tools:"]
        lines.append("=" * 80)

        # Group by category
        by_category = {}
        for tool in self.tools.values():
            if tool.category not in by_category:
                by_category[tool.category] = []
            by_category[tool.category].append(tool)

        for category in ToolCategory:
            if category in by_category:
                lines.append(f"\n🔧 {category.value.upper()}")
                lines.append("-" * 80)

                for tool in by_category[category]:
                    conf_marker = "🔒" if tool.requires_confirmation else "  "
                    lines.append(f"{conf_marker} {tool.name:20} - {tool.description}")

        lines.append("\n" + "=" * 80)
        lines.append(f"Total: {len(self.tools)} tools")

        return "\n".join(lines)

    def _register_default_tools(self):
        """Register default tools (implemented by subclass or plugins)"""
        # Default tools will be registered by create_default_registry()
        pass

    def to_prompt_format(self) -> str:
        """
        Format tool registry for inclusion in agent prompts

        Returns a concise description of available tools suitable for
        including in the agent planning prompt.
        """
        lines = ["Available Tools:"]

        for tool in self.tools.values():
            params = ", ".join([
                f"{p.name}: {p.type.__name__}" +
                ("*" if p.required else "")
                for p in tool.parameters
            ])

            lines.append(f"• {tool.name}({params})")
            lines.append(f"  {tool.description}")

            if tool.examples:
                lines.append(f"  Example: {tool.examples[0]}")

        return "\n".join(lines)


def create_default_registry() -> ToolRegistry:
    """
    Create a registry with default tools

    Returns:
        ToolRegistry with core M1K3 tools registered
    """
    from src.agent.tools.rag_tool import create_rag_tool
    from src.agent.tools.memory_tool import create_memory_tool
    from src.agent.tools.calculator_tool import create_calculator_tool

    registry = ToolRegistry()

    # Register core tools
    registry.register_tool(create_rag_tool())
    registry.register_tool(create_memory_tool())
    registry.register_tool(create_calculator_tool())

    return registry
```

#### 2.2.3 Example Tool Implementation (`src/agent/tools/calculator_tool.py`)

```python
#!/usr/bin/env python3
"""
Calculator Tool
Mathematical computation tool for M1K3 agent system
"""

import ast
import operator
from typing import Dict
from src.agent.tool import Tool, ToolCategory, ToolParameter, ToolResult


# Safe math operators
SAFE_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.Pow: operator.pow,
    ast.Mod: operator.mod,
    ast.FloorDiv: operator.floordiv,
    ast.USub: operator.neg,
}


def safe_eval_expression(expression: str) -> float:
    """
    Safely evaluate a mathematical expression

    Args:
        expression: Mathematical expression string (e.g., "2 + 3 * 4")

    Returns:
        Result of the expression

    Raises:
        ValueError: If expression is invalid or uses unsafe operations
    """
    # Parse expression
    try:
        node = ast.parse(expression, mode='eval')
    except SyntaxError as e:
        raise ValueError(f"Invalid expression syntax: {e}")

    # Validate and evaluate
    def eval_node(node):
        if isinstance(node, ast.Expression):
            return eval_node(node.body)

        elif isinstance(node, ast.Constant):  # Python 3.8+
            return node.value

        elif isinstance(node, ast.BinOp):
            left = eval_node(node.left)
            right = eval_node(node.right)
            op_type = type(node.op)

            if op_type not in SAFE_OPERATORS:
                raise ValueError(f"Unsafe operator: {op_type.__name__}")

            return SAFE_OPERATORS[op_type](left, right)

        elif isinstance(node, ast.UnaryOp):
            operand = eval_node(node.operand)
            op_type = type(node.op)

            if op_type not in SAFE_OPERATORS:
                raise ValueError(f"Unsafe operator: {op_type.__name__}")

            return SAFE_OPERATORS[op_type](operand)

        else:
            raise ValueError(f"Unsafe node type: {type(node).__name__}")

    return eval_node(node)


def calculator_execute(expression: str) -> ToolResult:
    """
    Execute calculator tool

    Args:
        expression: Mathematical expression to evaluate

    Returns:
        ToolResult with calculated value or error
    """
    try:
        result = safe_eval_expression(expression)

        return ToolResult(
            success=True,
            data=result,
            metadata={
                "expression": expression,
                "result_type": type(result).__name__
            }
        )

    except Exception as e:
        return ToolResult(
            success=False,
            error=f"Calculation failed: {str(e)}"
        )


def create_calculator_tool() -> Tool:
    """
    Create calculator tool instance

    Returns:
        Configured Calculator tool
    """
    return Tool(
        name="calculator",
        category=ToolCategory.COMPUTATION,
        description="Perform mathematical calculations with basic arithmetic operations",
        parameters=[
            ToolParameter(
                name="expression",
                type=str,
                required=True,
                description="Mathematical expression to evaluate (e.g., '2 + 3 * 4')"
            )
        ],
        execute=calculator_execute,
        requires_confirmation=False,
        examples=[
            "calculator(expression='15 * 23')",
            "calculator(expression='(100 - 25) / 5')",
            "calculator(expression='2 ** 10')"
        ]
    )
```

#### 2.2.4 Agent Orchestrator (`src/agent/orchestrator.py`)

```python
#!/usr/bin/env python3
"""
Agent Orchestrator
Main coordination logic for M1K3 agent system using ReAct pattern
"""

from typing import Generator, Dict, Any, Optional, List
from dataclasses import dataclass
from enum import Enum
import time

from src.agent.registry import ToolRegistry
from src.agent.tool import Tool, ToolResult
from src.utils.intent_classification_system import IntentClassificationEngine, UserIntent


class AgentState(Enum):
    """Agent execution states"""
    IDLE = "idle"
    THINKING = "thinking"
    PLANNING = "planning"
    EXECUTING = "executing"
    SYNTHESIZING = "synthesizing"
    COMPLETE = "complete"
    ERROR = "error"


@dataclass
class AgentStep:
    """Represents a single step in the agent workflow"""
    index: int
    thought: str
    action: Optional[str] = None  # Tool name
    action_input: Optional[Dict[str, Any]] = None  # Tool parameters
    observation: Optional[str] = None  # Tool result
    state: AgentState = AgentState.THINKING


@dataclass
class AgentResponse:
    """Complete agent response with all steps"""
    query: str
    steps: List[AgentStep]
    final_answer: str
    total_time: float
    metadata: Dict[str, Any]


class AgentOrchestrator:
    """
    Main agent orchestrator using ReAct pattern (Reasoning + Acting)

    Workflow:
    1. User query → Intent classification
    2. Generate reasoning step
    3. Select tool (action)
    4. Execute tool → Observation
    5. Repeat steps 2-4 until task complete
    6. Generate final answer
    """

    def __init__(
        self,
        ai_engine: Any,  # LocalAIEngine instance
        tool_registry: ToolRegistry,
        memory_manager: Optional[Any] = None,
        rag_engine: Optional[Any] = None,
        max_steps: int = 10
    ):
        self.ai_engine = ai_engine
        self.tool_registry = tool_registry
        self.memory_manager = memory_manager
        self.rag_engine = rag_engine
        self.max_steps = max_steps

        self.intent_classifier = IntentClassificationEngine()
        self.current_state = AgentState.IDLE
        self.steps: List[AgentStep] = []

    def execute_task(
        self,
        user_query: str,
        context: Optional[Dict] = None,
        stream: bool = True
    ) -> Generator[Dict[str, Any], None, AgentResponse]:
        """
        Execute agent task with streaming progress updates

        Args:
            user_query: User's input query
            context: Optional context dictionary
            stream: Whether to stream intermediate steps

        Yields:
            Progress updates with state and current step

        Returns:
            Final AgentResponse with all steps
        """
        start_time = time.time()
        self.steps = []
        self.current_state = AgentState.THINKING

        # Step 1: Classify intent
        if stream:
            yield {"state": AgentState.THINKING, "message": "Analyzing request..."}

        classification = self.intent_classifier.classify_intent(user_query, context)

        # Step 2: Determine if agent workflow needed
        if self._should_use_agent(classification):
            # Agent workflow (ReAct pattern)
            if stream:
                yield {"state": AgentState.PLANNING, "message": "Planning approach..."}

            # Execute ReAct loop
            for step_update in self._execute_react_loop(user_query, classification, stream):
                if stream:
                    yield step_update

        else:
            # Simple direct response (no agent workflow)
            if stream:
                yield {"state": AgentState.SYNTHESIZING, "message": "Generating response..."}

            direct_response = self.ai_engine.generate_response(user_query)

            self.steps.append(AgentStep(
                index=0,
                thought="Direct response (no tools needed)",
                state=AgentState.COMPLETE
            ))

            final_answer = "".join(direct_response)

        # Final synthesis (if agent workflow was used)
        if self.current_state == AgentState.EXECUTING:
            if stream:
                yield {"state": AgentState.SYNTHESIZING, "message": "Synthesizing final answer..."}

            final_answer = self._synthesize_final_answer(user_query)

        self.current_state = AgentState.COMPLETE
        total_time = time.time() - start_time

        # Create final response
        response = AgentResponse(
            query=user_query,
            steps=self.steps,
            final_answer=final_answer,
            total_time=total_time,
            metadata={
                "num_steps": len(self.steps),
                "intent": classification.intent.value,
                "tools_used": [s.action for s in self.steps if s.action]
            }
        )

        if stream:
            yield {"state": AgentState.COMPLETE, "response": response}

        return response

    def _should_use_agent(self, classification) -> bool:
        """Determine if agent workflow is needed based on intent"""
        # Use agent for planning, multi-step, complex tasks
        agent_intents = [
            UserIntent.PLANNING_REQUEST,
            UserIntent.TROUBLESHOOTING,
            UserIntent.CODE_DEBUGGING,
            UserIntent.MATHEMATICAL_CALCULATION,
            UserIntent.RECOMMENDATION_REQUEST
        ]

        return classification.intent in agent_intents

    def _execute_react_loop(
        self,
        query: str,
        classification,
        stream: bool
    ) -> Generator[Dict[str, Any], None, None]:
        """
        Execute ReAct (Reasoning + Acting) loop

        Yields progress updates for each step
        """
        self.current_state = AgentState.EXECUTING
        step_index = 0

        # Initial context
        context = query

        while step_index < self.max_steps:
            # Generate reasoning step (thought)
            thought_prompt = self._create_thought_prompt(query, context, step_index)
            thought = self._generate_thought(thought_prompt)

            step = AgentStep(
                index=step_index,
                thought=thought,
                state=AgentState.THINKING
            )
            self.steps.append(step)

            if stream:
                yield {
                    "state": AgentState.THINKING,
                    "step": step_index,
                    "thought": thought
                }

            # Determine if we need an action (tool call)
            if self._should_take_action(thought):
                # Extract action and parameters
                action, action_input = self._parse_action(thought)

                if action:
                    step.action = action
                    step.action_input = action_input
                    step.state = AgentState.EXECUTING

                    if stream:
                        yield {
                            "state": AgentState.EXECUTING,
                            "step": step_index,
                            "action": action,
                            "input": action_input
                        }

                    # Execute tool
                    observation = self._execute_tool(action, action_input)
                    step.observation = observation

                    if stream:
                        yield {
                            "state": AgentState.EXECUTING,
                            "step": step_index,
                            "observation": observation
                        }

                    # Update context with observation
                    context += f"\n\nObservation: {observation}"

            # Check if we're done
            if self._is_task_complete(thought):
                break

            step_index += 1

        # If we hit max steps, log warning
        if step_index >= self.max_steps:
            print(f"⚠️  Warning: Agent reached max steps ({self.max_steps})")

    def _create_thought_prompt(self, query: str, context: str, step_index: int) -> str:
        """Create prompt for generating reasoning step"""
        tools_description = self.tool_registry.to_prompt_format()

        prompt = f"""You are an AI agent with access to tools. Use the ReAct pattern (Reasoning + Acting) to answer the user's question.

{tools_description}

User Query: {query}

Previous Context:
{context}

Current Step: {step_index + 1}

Think about what to do next. You can:
1. Use a tool by stating: "Action: tool_name(param=value)"
2. Conclude with: "Final Answer: [your answer]"

Your thought:"""

        return prompt

    def _generate_thought(self, prompt: str) -> str:
        """Generate reasoning step using AI engine"""
        response = self.ai_engine.generate_response(prompt, max_tokens=256)

        # Collect full response
        thought = ""
        for token in response:
            thought += token

        return thought.strip()

    def _should_take_action(self, thought: str) -> bool:
        """Check if thought indicates an action should be taken"""
        return "Action:" in thought and "Final Answer:" not in thought

    def _parse_action(self, thought: str) -> tuple[Optional[str], Optional[Dict]]:
        """Parse action and parameters from thought"""
        # Simple parsing: "Action: tool_name(param=value, param2=value2)"
        if "Action:" not in thought:
            return None, None

        try:
            action_line = thought.split("Action:")[1].strip().split("\n")[0]

            # Extract tool name
            tool_name = action_line.split("(")[0].strip()

            # Extract parameters
            if "(" in action_line and ")" in action_line:
                params_str = action_line.split("(")[1].split(")")[0]

                # Parse key=value pairs
                params = {}
                for param_pair in params_str.split(","):
                    if "=" in param_pair:
                        key, value = param_pair.split("=", 1)
                        # Remove quotes if present
                        value = value.strip().strip("'\"")
                        params[key.strip()] = value

                return tool_name, params

            return tool_name, {}

        except Exception as e:
            print(f"⚠️  Failed to parse action: {e}")
            return None, None

    def _execute_tool(self, tool_name: str, params: Dict[str, Any]) -> str:
        """Execute tool and return observation"""
        tool = self.tool_registry.get_tool(tool_name)

        if not tool:
            return f"Error: Tool '{tool_name}' not found"

        # Execute tool
        result = tool.execute_safe(**params)

        if result.success:
            return f"Success: {result.data}"
        else:
            return f"Error: {result.error}"

    def _is_task_complete(self, thought: str) -> bool:
        """Check if task is complete based on thought"""
        return "Final Answer:" in thought

    def _synthesize_final_answer(self, query: str) -> str:
        """Synthesize final answer from all steps"""
        # Collect all observations
        observations = []
        for step in self.steps:
            if step.observation:
                observations.append(f"Step {step.index + 1}: {step.observation}")

        # Generate synthesis prompt
        synthesis_prompt = f"""Based on the following observations, provide a comprehensive answer to the user's question.

User Query: {query}

Observations:
{chr(10).join(observations)}

Final Answer:"""

        response = self.ai_engine.generate_response(synthesis_prompt, max_tokens=512)

        # Collect full response
        final_answer = ""
        for token in response:
            final_answer += token

        return final_answer.strip()
```

### 2.3 Integration with LocalAIEngine

#### Modifications to `src/engines/ai/ai_inference.py`:

```python
# Add to imports
from typing import Optional
from src.agent.orchestrator import AgentOrchestrator
from src.agent.registry import create_default_registry

class LocalAIEngine:
    def __init__(self, ...):
        # ... existing init code ...

        # Agent system
        self.agent_mode = False
        self.agent_orchestrator: Optional[AgentOrchestrator] = None

    def enable_agent_mode(self, custom_tools: Optional[List] = None):
        """
        Enable agent mode with tool-based task execution

        Args:
            custom_tools: Optional list of custom tools to register
        """
        if self.agent_orchestrator:
            print("🤖 Agent mode already enabled")
            return

        # Create tool registry
        tool_registry = create_default_registry()

        # Register custom tools if provided
        if custom_tools:
            for tool in custom_tools:
                tool_registry.register_tool(tool)

        # Create orchestrator
        self.agent_orchestrator = AgentOrchestrator(
            ai_engine=self,
            tool_registry=tool_registry,
            memory_manager=self._get_memory_manager(),
            rag_engine=self._get_rag_engine(),
            max_steps=10
        )

        self.agent_mode = True
        print(f"🤖 Agent mode enabled with {len(tool_registry.tools)} tools")
        print(tool_registry.get_tool_summary())

    def disable_agent_mode(self):
        """Disable agent mode"""
        self.agent_mode = False
        self.agent_orchestrator = None
        print("🤖 Agent mode disabled")

    def generate_response(
        self,
        prompt: str,
        max_tokens: int = 512,
        use_agent: Optional[bool] = None,
        **kwargs
    ) -> Generator[str, None, None]:
        """
        Generate AI response (with optional agent mode)

        Args:
            prompt: User input
            max_tokens: Maximum tokens to generate
            use_agent: Override agent mode for this request (None = use self.agent_mode)
            **kwargs: Additional generation parameters
        """
        # Determine if agent mode should be used
        should_use_agent = use_agent if use_agent is not None else self.agent_mode

        if should_use_agent and self.agent_orchestrator:
            # Agent workflow
            for update in self.agent_orchestrator.execute_task(prompt, stream=True):
                if update.get("state") == "complete":
                    # Stream final answer
                    final_answer = update["response"].final_answer
                    for char in final_answer:
                        yield char
                else:
                    # Stream progress updates (for CLI display)
                    # Could yield special tokens for CLI to handle
                    pass
        else:
            # Standard generation (existing code)
            # ... existing generate_response implementation ...
            pass

    def _get_memory_manager(self):
        """Get memory manager instance (if available)"""
        try:
            from src.database.vector_memory_manager import get_vector_memory_manager
            return get_vector_memory_manager()
        except:
            return None

    def _get_rag_engine(self):
        """Get RAG engine instance (if available)"""
        if hasattr(self, 'rag_engine'):
            return self.rag_engine
        return None
```

### 2.4 CLI Integration

#### Add commands to `src/cli/cli_ai_handler.py` (or equivalent):

```python
def handle_agent_command(args: List[str], ai_engine: LocalAIEngine):
    """Handle /agent commands"""

    if not args:
        print("Usage: /agent <subcommand>")
        print("Subcommands:")
        print("  enable              Enable agent mode")
        print("  disable             Disable agent mode")
        print("  status              Show agent status")
        print("  tools               List available tools")
        print("  plan <query>        Execute agent workflow for query")
        return

    subcommand = args[0].lower()

    if subcommand == "enable":
        ai_engine.enable_agent_mode()

    elif subcommand == "disable":
        ai_engine.disable_agent_mode()

    elif subcommand == "status":
        if ai_engine.agent_mode:
            print("🤖 Agent mode: ENABLED")
            if ai_engine.agent_orchestrator:
                registry = ai_engine.agent_orchestrator.tool_registry
                print(f"📋 Tools available: {len(registry.tools)}")
                print(registry.get_tool_summary())
        else:
            print("🤖 Agent mode: DISABLED")

    elif subcommand == "tools":
        if ai_engine.agent_orchestrator:
            registry = ai_engine.agent_orchestrator.tool_registry
            print(registry.get_tool_summary())
        else:
            print("❌ Agent mode not enabled")

    elif subcommand == "plan":
        if not ai_engine.agent_mode:
            print("❌ Agent mode not enabled. Use '/agent enable' first.")
            return

        query = " ".join(args[1:])
        if not query:
            print("❌ Please provide a query: /agent plan <your query>")
            return

        print(f"🤖 Executing agent workflow for: {query}")
        print("=" * 80)

        # Execute with agent mode
        response_gen = ai_engine.generate_response(query, use_agent=True)

        # Stream and display response
        for token in response_gen:
            print(token, end="", flush=True)

        print("\n" + "=" * 80)

    else:
        print(f"❌ Unknown subcommand: {subcommand}")
```

---

## 3. 間 AI Kotlin Implementation

### 3.1 Directory Structure

```
app/
├── shared/
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       └── com/maai/
│       │           ├── domain/
│       │           │   └── agent/           # NEW: Agent system
│       │           │       ├── Tool.kt
│       │           │       ├── ToolRegistry.kt
│       │           │       ├── AgentOrchestrator.kt
│       │           │       ├── ToolResult.kt
│       │           │       └── tools/
│       │           │           ├── CalculatorTool.kt
│       │           │           ├── MemoryRecallTool.kt
│       │           │           └── RAGSearchTool.kt
│       │           │
│       │           └── data/
│       │               └── ai/
│       │                   └── AgentCapableAIEngine.kt  # NEW
│       │
│       ├── androidMain/
│       │   └── kotlin/
│       │       └── com/maai/
│       │           ├── domain/
│       │           │   └── agent/
│       │           │       └── tools/      # Android-specific tools
│       │           │           ├── CameraTool.kt
│       │           │           ├── VisionAnalysisTool.kt
│       │           │           ├── GalleryTool.kt
│       │           │           └── DeviceInfoTool.kt
│       │           │
│       │           └── data/
│       │               └── ai/
│       │                   └── AndroidAgentAIEngine.kt
│       │
│       └── iosMain/
│           └── kotlin/
│               └── com/maai/
│                   └── domain/
│                       └── agent/
│                           └── tools/      # iOS-specific tools (future)
│
└── composeApp/
    └── src/
        ├── commonMain/
        │   └── kotlin/
        │       └── com/maai/
        │           └── presentation/
        │               └── agent/          # NEW: Agent UI
        │                   ├── AgentTaskScreen.kt
        │                   ├── AgentViewModel.kt
        │                   ├── PlanStepItem.kt
        │                   └── StepProgressIndicator.kt
        │
        └── androidMain/
            └── kotlin/
                └── com/maai/
                    └── MainActivity.kt    # MODIFY: Wire up agent
```

### 3.2 Core Implementation Files

#### 3.2.1 Tool Interface (`shared/src/commonMain/.../domain/agent/Tool.kt`)

```kotlin
package com.maai.domain.agent

/**
 * Tool categories for organization
 */
enum class ToolCategory {
    INFORMATION,    // RAG, memory, search
    COMPUTATION,    // Math, analysis
    MULTIMODAL,     // Camera, vision
    DEVICE,         // Device info, sensors
    SYSTEM          // Settings, config
}

/**
 * Parameter type specifications
 */
enum class ParameterType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    ARRAY,
    OBJECT
}

/**
 * Tool parameter specification
 */
data class ParameterSpec(
    val type: ParameterType,
    val required: Boolean = false,
    val default: Any? = null,
    val description: String = ""
)

/**
 * Result from tool execution
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val executionTimeMs: Long = 0
) {
    override fun toString(): String {
        return if (success) {
            "✅ Success: $data"
        } else {
            "❌ Error: $error"
        }
    }
}

/**
 * Base interface for agent tools
 *
 * Tools are atomic units of functionality that agents can invoke
 * to accomplish tasks. Each tool has a name, description, parameters,
 * and execution logic.
 */
interface Tool {
    val name: String
    val category: ToolCategory
    val description: String
    val parameters: Map<String, ParameterSpec>
    val requiresConfirmation: Boolean
    val examples: List<String>

    /**
     * Execute the tool with given parameters
     *
     * @param params Parameter map with parameter names as keys
     * @return Result of tool execution
     */
    suspend fun execute(params: Map<String, Any>): Result<ToolResult>

    /**
     * Validate parameters before execution
     *
     * @param params Parameter map to validate
     * @return Validation result with optional error message
     */
    fun validateParameters(params: Map<String, Any>): Pair<Boolean, String?> {
        // Check required parameters
        parameters.forEach { (name, spec) ->
            if (spec.required && !params.containsKey(name)) {
                return false to "Missing required parameter: $name"
            }
        }

        return true to null
    }

    /**
     * Convert tool to map for serialization
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "category" to category.name,
            "description" to description,
            "parameters" to parameters.mapValues { (_, spec) ->
                mapOf(
                    "type" to spec.type.name,
                    "required" to spec.required,
                    "default" to spec.default,
                    "description" to spec.description
                )
            },
            "requiresConfirmation" to requiresConfirmation,
            "examples" to examples
        )
    }
}
```

#### 3.2.2 Tool Registry (`shared/src/commonMain/.../domain/agent/ToolRegistry.kt`)

```kotlin
package com.maai.domain.agent

/**
 * Central registry for agent tools
 *
 * Provides tool discovery, validation, and management
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    /**
     * Register a tool
     */
    fun registerTool(tool: Tool) {
        if (tools.containsKey(tool.name)) {
            println("⚠️ Warning: Tool '${tool.name}' already registered, overwriting")
        }

        tools[tool.name] = tool
        println("✅ Registered tool: ${tool.name} (${tool.category})")
    }

    /**
     * Unregister a tool by name
     */
    fun unregisterTool(name: String): Boolean {
        return tools.remove(name) != null
    }

    /**
     * Get a tool by name
     */
    fun getTool(name: String): Tool? {
        return tools[name]
    }

    /**
     * List all tools, optionally filtered by category
     */
    fun listTools(category: ToolCategory? = null): List<Tool> {
        return if (category != null) {
            tools.values.filter { it.category == category }
        } else {
            tools.values.toList()
        }
    }

    /**
     * Search tools by name or description
     */
    fun searchTools(query: String): List<Tool> {
        val queryLower = query.lowercase()
        return tools.values.filter {
            it.name.lowercase().contains(queryLower) ||
            it.description.lowercase().contains(queryLower)
        }
    }

    /**
     * Get tool count
     */
    fun getToolCount(): Int = tools.size

    /**
     * Get formatted tool summary
     */
    fun getToolSummary(): String {
        val lines = mutableListOf<String>()
        lines.add("📋 Available Tools:")
        lines.add("=".repeat(60))

        // Group by category
        val byCategory = tools.values.groupBy { it.category }

        ToolCategory.values().forEach { category ->
            byCategory[category]?.let { toolsInCategory ->
                lines.add("")
                lines.add("🔧 ${category.name}")
                lines.add("-".repeat(60))

                toolsInCategory.forEach { tool ->
                    val confMarker = if (tool.requiresConfirmation) "🔒" else "  "
                    lines.add("$confMarker ${tool.name.padEnd(20)} - ${tool.description}")
                }
            }
        }

        lines.add("")
        lines.add("=".repeat(60))
        lines.add("Total: ${tools.size} tools")

        return lines.joinToString("\n")
    }

    /**
     * Format tools for AI prompt inclusion
     */
    fun toPromptFormat(): String {
        val lines = mutableListOf<String>()
        lines.add("Available Tools:")

        tools.values.forEach { tool ->
            val params = tool.parameters.entries.joinToString(", ") { (name, spec) ->
                "$name: ${spec.type}" + if (spec.required) "*" else ""
            }

            lines.add("• ${tool.name}($params)")
            lines.add("  ${tool.description}")

            if (tool.examples.isNotEmpty()) {
                lines.add("  Example: ${tool.examples.first()}")
            }
        }

        return lines.joinToString("\n")
    }
}

/**
 * Create default tool registry with common tools
 */
fun createDefaultRegistry(): ToolRegistry {
    val registry = ToolRegistry()

    // Register common tools
    registry.registerTool(createCalculatorTool())
    // More tools registered in platform-specific code

    return registry
}
```

#### 3.2.3 Example Tool (`shared/src/commonMain/.../domain/agent/tools/CalculatorTool.kt`)

```kotlin
package com.maai.domain.agent.tools

import com.maai.domain.agent.*
import kotlin.math.pow

/**
 * Calculator tool for mathematical computations
 */
class CalculatorTool : Tool {
    override val name = "calculator"
    override val category = ToolCategory.COMPUTATION
    override val description = "Perform mathematical calculations"
    override val parameters = mapOf(
        "expression" to ParameterSpec(
            type = ParameterType.STRING,
            required = true,
            description = "Mathematical expression (e.g., '2 + 3 * 4')"
        )
    )
    override val requiresConfirmation = false
    override val examples = listOf(
        "calculator(expression='15 * 23')",
        "calculator(expression='(100 - 25) / 5')"
    )

    override suspend fun execute(params: Map<String, Any>): Result<ToolResult> {
        val startTime = System.currentTimeMillis()

        return try {
            // Validate parameters
            val (valid, error) = validateParameters(params)
            if (!valid) {
                return Result.success(ToolResult(
                    success = false,
                    error = error,
                    executionTimeMs = System.currentTimeMillis() - startTime
                ))
            }

            val expression = params["expression"] as String
            val result = evaluateExpression(expression)

            Result.success(ToolResult(
                success = true,
                data = result,
                metadata = mapOf(
                    "expression" to expression,
                    "result_type" to result::class.simpleName.orEmpty()
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            ))
        } catch (e: Exception) {
            Result.success(ToolResult(
                success = false,
                error = "Calculation failed: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            ))
        }
    }

    /**
     * Safely evaluate mathematical expression
     * Simple implementation - could be enhanced with proper parser
     */
    private fun evaluateExpression(expression: String): Double {
        // Very simple evaluator - would use proper parser in production
        // This is a placeholder for demonstration

        val cleaned = expression.replace("\\s+".toRegex(), "")

        // Handle basic operations
        // In production, use a proper expression parser/evaluator

        return try {
            // Placeholder: would implement proper evaluation
            // For now, just throw if not simple number
            cleaned.toDouble()
        } catch (e: Exception) {
            throw IllegalArgumentException("Complex expressions not yet supported")
        }
    }
}

/**
 * Factory function for calculator tool
 */
fun createCalculatorTool(): Tool = CalculatorTool()
```

#### 3.2.4 Agent Orchestrator (`shared/src/commonMain/.../domain/agent/AgentOrchestrator.kt`)

```kotlin
package com.maai.domain.agent

import com.maai.domain.ai.AIEngine
import com.maai.domain.memory.MemoryManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Agent execution states
 */
sealed class AgentResponse {
    data class Thinking(val message: String) : AgentResponse()
    data class Plan(val steps: List<PlanStep>) : AgentResponse()
    data class ExecutingStep(val index: Int, val description: String) : AgentResponse()
    data class StepComplete(val index: Int, val result: ToolResult) : AgentResponse()
    data class Synthesizing(val message: String) : AgentResponse()
    data class Complete(val finalResponse: String, val metadata: Map<String, Any>) : AgentResponse()
    data class Error(val error: String) : AgentResponse()
}

/**
 * Represents a step in the agent plan
 */
data class PlanStep(
    val index: Int,
    val description: String,
    val toolName: String? = null,
    val parameters: Map<String, Any>? = null
)

/**
 * Main agent orchestrator using function calling pattern
 *
 * Optimized for mobile with focus on:
 * - Low latency (selective tool use)
 * - Battery efficiency (minimal model calls)
 * - Offline capability (no network tools)
 */
class AgentOrchestrator(
    private val aiEngine: AIEngine,
    private val memoryManager: MemoryManager?,
    private val toolRegistry: ToolRegistry,
    private val maxSteps: Int = 5  // Lower than desktop due to mobile constraints
) {

    /**
     * Execute agent task with streaming progress updates
     *
     * @param userQuery User's input query
     * @param projectId Optional project ID for context
     * @return Flow of agent responses (progress updates and final result)
     */
    suspend fun executeTask(
        userQuery: String,
        projectId: String? = null
    ): Flow<AgentResponse> = flow {

        try {
            // Step 1: Analyze intent
            emit(AgentResponse.Thinking("Analyzing request..."))

            // Step 2: Generate plan
            emit(AgentResponse.Thinking("Creating plan..."))
            val planPrompt = createPlanningPrompt(userQuery)
            val planText = generatePlan(planPrompt)
            val steps = parsePlan(planText)

            emit(AgentResponse.Plan(steps))

            // Step 3: Execute steps
            val results = mutableListOf<ToolResult>()

            steps.forEachIndexed { index, step ->
                emit(AgentResponse.ExecutingStep(index, step.description))

                // Execute tool if specified
                step.toolName?.let { toolName ->
                    val tool = toolRegistry.getTool(toolName)

                    if (tool != null) {
                        // Check if confirmation needed
                        if (tool.requiresConfirmation) {
                            // Would emit confirmation request here
                            // For now, skip confirmation in automated flow
                        }

                        // Execute tool
                        val result = tool.execute(step.parameters ?: emptyMap())
                        val toolResult = result.getOrElse {
                            ToolResult(
                                success = false,
                                error = "Tool execution failed: ${it.message}"
                            )
                        }

                        results.add(toolResult)
                        emit(AgentResponse.StepComplete(index, toolResult))
                    } else {
                        // Tool not found
                        val errorResult = ToolResult(
                            success = false,
                            error = "Tool not found: $toolName"
                        )
                        results.add(errorResult)
                        emit(AgentResponse.StepComplete(index, errorResult))
                    }
                }
            }

            // Step 4: Synthesize final response
            emit(AgentResponse.Synthesizing("Preparing final response..."))
            val finalResponse = synthesizeResponse(userQuery, steps, results)

            emit(AgentResponse.Complete(
                finalResponse = finalResponse,
                metadata = mapOf(
                    "steps_executed" to steps.size,
                    "tools_used" to steps.mapNotNull { it.toolName }
                )
            ))

        } catch (e: Exception) {
            emit(AgentResponse.Error("Agent execution failed: ${e.message}"))
        }
    }

    /**
     * Create planning prompt with available tools
     */
    private fun createPlanningPrompt(query: String): String {
        val toolsDescription = toolRegistry.toPromptFormat()

        return """
            You are an AI agent with access to tools. Create a step-by-step plan to answer this query.

            $toolsDescription

            User Query: $query

            Create a plan with 1-${maxSteps} steps. For each step:
            1. Description of what to do
            2. Tool to use (if any)
            3. Parameters for the tool

            Format:
            Step 1: [Description]
            Tool: [tool_name]
            Parameters: {\"param\": \"value\"}

            Plan:
        """.trimIndent()
    }

    /**
     * Generate plan using AI engine
     */
    private suspend fun generatePlan(prompt: String): String {
        // Use AI engine to generate plan
        // Implementation would collect from flow
        return "" // Placeholder
    }

    /**
     * Parse plan text into structured steps
     */
    private fun parsePlan(planText: String): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()

        // Simple parsing logic
        // In production, would use more robust parsing

        val stepPattern = Regex("""Step (\d+): (.+)""")
        val toolPattern = Regex("""Tool: (.+)""")
        val paramsPattern = Regex("""Parameters: (.+)""")

        val lines = planText.lines()
        var currentStep: PlanStep? = null

        lines.forEach { line ->
            stepPattern.find(line)?.let { match ->
                val index = match.groupValues[1].toInt() - 1
                val description = match.groupValues[2]
                currentStep = PlanStep(index, description)
            }

            toolPattern.find(line)?.let { match ->
                currentStep = currentStep?.copy(toolName = match.groupValues[1].trim())
            }

            paramsPattern.find(line)?.let { match ->
                // Parse JSON parameters (simplified)
                currentStep = currentStep?.copy(parameters = emptyMap()) // Placeholder
            }

            // If step is complete, add to list
            currentStep?.let { step ->
                if (line.isEmpty() || line.startsWith("Step")) {
                    steps.add(step)
                    currentStep = null
                }
            }
        }

        return steps
    }

    /**
     * Synthesize final response from steps and results
     */
    private fun synthesizeResponse(
        query: String,
        steps: List<PlanStep>,
        results: List<ToolResult>
    ): String {
        // Combine observations into final answer
        val observations = results.mapIndexed { index, result ->
            "Step ${index + 1}: ${if (result.success) result.data else result.error}"
        }

        return """
            Based on the following observations:

            ${observations.joinToString("\n")}

            [Final synthesized response would go here]
        """.trimIndent()
    }
}
```

### 3.3 Integration with AI Engine

#### Create `AgentCapableAIEngine` interface:

```kotlin
package com.maai.data.ai

import com.maai.domain.agent.AgentOrchestrator
import com.maai.domain.agent.AgentResponse
import kotlinx.coroutines.flow.Flow

/**
 * AI Engine with agent capabilities
 */
interface AgentCapableAIEngine : AIEngine {
    val agentOrchestrator: AgentOrchestrator

    /**
     * Execute query using agent workflow
     */
    suspend fun executeWithAgent(
        query: String,
        projectId: String? = null
    ): Flow<AgentResponse>
}
```

### 3.4 Compose UI Integration

#### Agent Task Screen (`composeApp/src/commonMain/.../presentation/agent/AgentTaskScreen.kt`)

```kotlin
package com.maai.presentation.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maai.domain.agent.AgentResponse
import com.maai.domain.agent.PlanStep

@Composable
fun AgentTaskScreen(
    query: String,
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Query display
        Text(
            text = query,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Agent progress
        when (val state = agentState) {
            is AgentState.Idle -> {
                Text("Waiting to start...")
            }

            is AgentState.Thinking -> {
                Column {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Thinking: ${state.message}")
                }
            }

            is AgentState.Planning -> {
                Column {
                    Text(
                        "Plan:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn {
                        items(state.steps) { step ->
                            PlanStepItem(step = step)
                        }
                    }
                }
            }

            is AgentState.Executing -> {
                LazyColumn {
                    items(state.steps.size) { index ->
                        val step = state.steps[index]
                        StepProgressItem(
                            step = step,
                            isActive = index == state.currentStepIndex,
                            isComplete = index < state.currentStepIndex,
                            result = state.results.getOrNull(index)
                        )
                    }
                }
            }

            is AgentState.Complete -> {
                Column {
                    Text(
                        "Complete!",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.finalResponse,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is AgentState.Error -> {
                Text(
                    "Error: ${state.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun PlanStepItem(
    step: PlanStep,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Step ${step.index + 1}",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium
            )

            step.toolName?.let { toolName ->
                Text(
                    text = "Tool: $toolName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StepProgressItem(
    step: PlanStep,
    isActive: Boolean,
    isComplete: Boolean,
    result: ToolResult?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isComplete -> MaterialTheme.colorScheme.primaryContainer
                isActive -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status indicator
            when {
                isComplete -> Text("✅")
                isActive -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                else -> Text("⏸️")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${step.index + 1}",
                    style = MaterialTheme.typography.labelSmall
                )

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isComplete && result != null) {
                    Text(
                        text = if (result.success) {
                            "Result: ${result.data}"
                        } else {
                            "Error: ${result.error}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}
```

---

## 4. Shared Architecture Patterns

### 4.1 Tool Development Guidelines

**Creating a New Tool (Python)**:

1. Create file in `src/agent/tools/`
2. Import base classes: `Tool, ToolCategory, ToolParameter, ToolResult`
3. Implement execution function
4. Create factory function
5. Register in `create_default_registry()`

**Creating a New Tool (Kotlin)**:

1. Create class implementing `Tool` interface
2. Define `name`, `category`, `description`, `parameters`
3. Implement `execute()` suspend function
4. Add factory function
5. Register in `createDefaultRegistry()`

### 4.2 Error Handling Patterns

**Both Platforms**:

1. **Tool Execution**: Always return `ToolResult` with success/failure
2. **Parameter Validation**: Check before execution
3. **Graceful Degradation**: Provide fallbacks for tool failures
4. **User-Friendly Errors**: Clear, actionable error messages
5. **Timeout Handling**: Set reasonable timeouts for long-running tools

### 4.3 Performance Optimization

**Desktop (M1K3)**:
- Cache tool results for repeated queries
- Parallel tool execution where possible
- Lazy load tool implementations
- Stream progress updates for UX

**Mobile (間 AI)**:
- Prioritize lightweight tools (calculator > camera)
- Battery-aware execution (reduce tool usage on low battery)
- Background execution for long-running tasks (WorkManager)
- Result caching in SQLDelight

---

## 5. Tool Development Guide

### 5.1 Core Tools (Both Platforms)

#### Priority 1 (Week 1-2):
1. **RAG Search Tool** - Search knowledge base
2. **Memory Recall Tool** - Search conversation history
3. **Calculator Tool** - Mathematical computations

#### Priority 2 (Week 3-4):
4. **Text Analysis Tool** - Sentiment, keywords, entities
5. **Trivia Lookup Tool** - Search trivia database

### 5.2 Platform-Specific Tools

#### M1K3 Only:
- **File Search Tool** - Search local files
- **Code Executor Tool** - Safe Python code execution
- **System Info Tool** - Get system metrics

#### 間 AI Only:
- **Camera Tool** - Capture images (CameraX)
- **Vision Analysis Tool** - ML Kit (OCR, labels, objects)
- **Gallery Tool** - Select from gallery
- **Device Info Tool** - SoC, RAM, battery status
- **Sensor Tool** - Accelerometer, gyroscope data

### 5.3 Tool Implementation Checklist

**For Each Tool**:
- [ ] Clear, concise name (snake_case Python, camelCase Kotlin)
- [ ] Accurate description (1-2 sentences)
- [ ] Well-defined parameters with types and defaults
- [ ] Parameter validation before execution
- [ ] Comprehensive error handling
- [ ] Execution time tracking
- [ ] At least 2 usage examples
- [ ] Unit tests (>80% coverage)
- [ ] Integration tests with orchestrator
- [ ] Documentation (docstrings/KDoc)

---

## 6. Testing Strategy

### 6.1 M1K3 Python Testing

**Unit Tests** (`tests/agent/test_tool.py`):
```python
def test_calculator_tool():
    calculator = create_calculator_tool()
    result = calculator.execute_safe(expression="15 * 23")
    assert result.success
    assert result.data == 345

def test_tool_parameter_validation():
    calculator = create_calculator_tool()
    result = calculator.execute_safe()  # Missing required param
    assert not result.success
    assert "required parameter" in result.error.lower()

def test_tool_registry():
    registry = create_default_registry()
    assert len(registry.tools) >= 3
    assert registry.get_tool("calculator") is not None
```

**Integration Tests** (`tests/agent/test_orchestrator.py`):
```python
def test_agent_simple_task():
    orchestrator = create_test_orchestrator()
    response = orchestrator.execute_task("What is 15 times 23?")

    # Should have used calculator tool
    assert any(step.action == "calculator" for step in response.steps)
    assert "345" in response.final_answer

def test_agent_multi_step():
    orchestrator = create_test_orchestrator()
    response = orchestrator.execute_task(
        "Search for quantum computing and calculate how many documents were found"
    )

    # Should have used both RAG and calculator
    tool_names = [s.action for s in response.steps if s.action]
    assert "rag_search" in tool_names
    assert "calculator" in tool_names or response.final_answer
```

### 6.2 間 AI Kotlin Testing

**Unit Tests** (`shared/src/commonTest/.../CalculatorToolTest.kt`):
```kotlin
class CalculatorToolTest {

    @Test
    fun testCalculatorSuccess() = runTest {
        val calculator = createCalculatorTool()
        val result = calculator.execute(mapOf("expression" to "15 * 23"))

        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertTrue(toolResult.success)
        assertEquals(345.0, toolResult.data)
    }

    @Test
    fun testCalculatorParameterValidation() = runTest {
        val calculator = createCalculatorTool()
        val result = calculator.execute(emptyMap())  // Missing required param

        assertTrue(result.isSuccess)
        val toolResult = result.getOrNull()!!
        assertFalse(toolResult.success)
        assertTrue(toolResult.error!!.contains("required parameter"))
    }

    @Test
    fun testToolRegistry() {
        val registry = createDefaultRegistry()
        assertTrue(registry.getToolCount() >= 1)
        assertNotNull(registry.getTool("calculator"))
    }
}
```

**Integration Tests** (`shared/src/androidTest/.../AgentOrchestratorTest.kt`):
```kotlin
@Test
fun testAgentSimpleTask() = runTest {
    val orchestrator = createTestOrchestrator()
    val responses = orchestrator.executeTask("Calculate 15 * 23").toList()

    val completeResponse = responses.last() as AgentResponse.Complete
    assertTrue(completeResponse.finalResponse.contains("345"))
}

@Test
fun testAgentMultipleTools() = runTest {
    val orchestrator = createTestOrchestrator()

    // Mock complex task requiring multiple tools
    val responses = orchestrator.executeTask(
        "Recall past conversations about math and calculate the average"
    ).toList()

    // Verify multiple steps executed
    val planResponse = responses.filterIsInstance<AgentResponse.Plan>().first()
    assertTrue(planResponse.steps.size > 1)
}
```

### 6.3 Performance Testing

**M1K3 Benchmarks**:
```python
def test_agent_latency():
    """Agent should complete simple task in <5 seconds"""
    orchestrator = create_production_orchestrator()
    start = time.time()

    response = orchestrator.execute_task("What is 100 + 200?")

    elapsed = time.time() - start
    assert elapsed < 5.0, f"Agent took {elapsed:.2f}s (target: <5s)"

def test_tool_execution_speed():
    """Individual tools should execute quickly"""
    calculator = create_calculator_tool()

    start = time.time()
    result = calculator.execute_safe(expression="1234 * 5678")
    elapsed = time.time() - start

    assert elapsed < 0.01, f"Calculator took {elapsed*1000:.1f}ms (target: <10ms)"
```

**間 AI Benchmarks**:
```kotlin
@Test
fun benchmarkAgentPlanGeneration() = runTest {
    val orchestrator = createProductionOrchestrator()
    val startTime = System.currentTimeMillis()

    orchestrator.executeTask("Complex multi-step task").collect()

    val duration = System.currentTimeMillis() - startTime
    assertTrue(duration < 8000, "Plan generation took ${duration}ms (target: <8s)")
}

@Test
fun benchmarkToolExecution() = runTest {
    val calculator = createCalculatorTool()
    val startTime = System.nanoTime()

    calculator.execute(mapOf("expression" to "1234 * 5678"))

    val durationMs = (System.nanoTime() - startTime) / 1_000_000
    assertTrue(durationMs < 10, "Calculator took ${durationMs}ms (target: <10ms)")
}
```

---

## 7. Performance Optimization

### 7.1 Caching Strategies

**Plan Caching** (Both Platforms):
- Cache generated plans for similar queries
- Use vector similarity to match query to cached plans
- Invalidate cache on tool registry changes

**Result Caching** (Both Platforms):
- Cache deterministic tool results (calculator, text analysis)
- Skip caching for time-sensitive tools (memory, device info)
- Implement LRU eviction policy

### 7.2 Parallel Execution

**M1K3** (Python `asyncio`):
```python
async def execute_tools_parallel(tools_with_params):
    """Execute multiple independent tools in parallel"""
    tasks = [
        tool.execute_async(**params)
        for tool, params in tools_with_params
    ]

    results = await asyncio.gather(*tasks, return_exceptions=True)
    return results
```

**間 AI** (Kotlin Coroutines):
```kotlin
suspend fun executeToolsParallel(toolsWithParams: List<Pair<Tool, Map<String, Any>>>): List<ToolResult> {
    return coroutineScope {
        toolsWithParams.map { (tool, params) ->
            async {
                tool.execute(params).getOrNull() ?: ToolResult(
                    success = false,
                    error = "Parallel execution failed"
                )
            }
        }.awaitAll()
    }
}
```

### 7.3 Memory Management

**M1K3**:
- Lazy load tool implementations
- Stream large results instead of loading in memory
- Clear tool caches after extended idle periods

**間 AI**:
- Use WeakReference for cached results
- Implement memory pressure callbacks
- Offload processing to background threads (Dispatchers.IO)

---

## 8. Migration Guide

### 8.1 Migrating Existing M1K3 Code

**Step 1: Enable Agent Mode**
```python
# In your existing script/CLI
from src.engines.ai.ai_inference import LocalAIEngine

ai_engine = LocalAIEngine()
ai_engine.enable_agent_mode()  # Enable with default tools

# Or with custom tools
from src.agent.tools.my_custom_tool import create_my_tool
ai_engine.enable_agent_mode(custom_tools=[create_my_tool()])
```

**Step 2: Use Agent for Specific Queries**
```python
# Explicit agent use
response = ai_engine.generate_response(
    "Complex task requiring tools",
    use_agent=True
)

# Or let intent classification decide
ai_engine.agent_mode = True  # Always use agent when appropriate
response = ai_engine.generate_response("Any query")
```

**Step 3: Add CLI Commands**
```python
# Add /agent commands to your CLI handler
# See section 2.4 for implementation
```

### 8.2 Integrating into 間 AI

**Phase 3 Integration** (Weeks 9-10):
1. Implement `Tool` interface and `ToolRegistry`
2. Create 3 information tools (RAG, memory, trivia)
3. Implement basic `AgentOrchestrator` (single-step)
4. Wire up to existing AI engine

**Phase 4 Integration** (Weeks 11-12):
1. Add multi-modal tools (camera, vision)
2. Enhance orchestrator for multi-step workflows
3. Create Compose UI for agent visualization

**Phase 5 Integration** (Weeks 13-15):
1. Add agent analytics and metrics
2. Implement tool confirmation UI
3. Polish agent progress visualization
4. Performance optimization

---

## Appendix A: File Templates

### Python Tool Template

```python
#!/usr/bin/env python3
"""
[Tool Name] Tool
[Brief description]
"""

from src.agent.tool import Tool, ToolCategory, ToolParameter, ToolResult


def [tool_name]_execute(**kwargs) -> ToolResult:
    """
    Execute [tool name] tool

    Args:
        [param_name]: [description]

    Returns:
        ToolResult with [description] or error
    """
    try:
        # Implementation here

        return ToolResult(
            success=True,
            data=result,
            metadata={}
        )

    except Exception as e:
        return ToolResult(
            success=False,
            error=f"[Tool] failed: {str(e)}"
        )


def create_[tool_name]_tool() -> Tool:
    """
    Create [tool name] tool instance

    Returns:
        Configured [Tool Name] tool
    """
    return Tool(
        name="[tool_name]",
        category=ToolCategory.[CATEGORY],
        description="[Description]",
        parameters=[
            ToolParameter(
                name="[param_name]",
                type=[type],
                required=[True/False],
                description="[Description]"
            )
        ],
        execute=[tool_name]_execute,
        requires_confirmation=[True/False],
        examples=[
            "[tool_name](param='value')"
        ]
    )
```

### Kotlin Tool Template

```kotlin
package com.maai.domain.agent.tools

import com.maai.domain.agent.*

/**
 * [Tool Name] tool
 * [Brief description]
 */
class [ToolName]Tool : Tool {
    override val name = "[tool_name]"
    override val category = ToolCategory.[CATEGORY]
    override val description = "[Description]"
    override val parameters = mapOf(
        "[param_name]" to ParameterSpec(
            type = ParameterType.[TYPE],
            required = [true/false],
            description = "[Description]"
        )
    )
    override val requiresConfirmation = [true/false]
    override val examples = listOf(
        "[tool_name](param='value')"
    )

    override suspend fun execute(params: Map<String, Any>): Result<ToolResult> {
        val startTime = System.currentTimeMillis()

        return try {
            // Validate
            val (valid, error) = validateParameters(params)
            if (!valid) {
                return Result.success(ToolResult(
                    success = false,
                    error = error,
                    executionTimeMs = System.currentTimeMillis() - startTime
                ))
            }

            // Extract parameters
            val paramValue = params["[param_name]"] as [Type]

            // Implementation here

            Result.success(ToolResult(
                success = true,
                data = result,
                executionTimeMs = System.currentTimeMillis() - startTime
            ))
        } catch (e: Exception) {
            Result.success(ToolResult(
                success = false,
                error = "[Tool] failed: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            ))
        }
    }
}

/**
 * Factory function
 */
fun create[ToolName]Tool(): Tool = [ToolName]Tool()
```

---

## Appendix B: References

### Related Documentation
- `AGENT_SYSTEM_RESEARCH.md` - High-level research and recommendations
- `CLAUDE.md` - M1K3 and 間 AI overview
- `app/PROJECT_MANAGEMENT.md` - 間 AI development roadmap
- `app/AI_ARCHITECTURE.md` - 間 AI technical architecture

### External Resources
- [ReAct Pattern Paper](https://arxiv.org/abs/2210.03629) - Reasoning + Acting
- [LangChain Agents](https://python.langchain.com/docs/modules/agents/) - Agent patterns
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) - KMP docs
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI framework

---

**Version History:**
- v1.0 (2025-11-02): Initial comprehensive integration plan

**Last Updated:** 2025-11-02
**Status:** Ready for Implementation
